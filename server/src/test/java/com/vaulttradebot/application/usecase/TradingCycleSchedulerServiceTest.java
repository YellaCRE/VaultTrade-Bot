package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.query.SchedulerStatusSnapshot;
import com.vaulttradebot.config.ApiTimeSupport;
import com.vaulttradebot.config.VaultSchedulerProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class TradingCycleSchedulerServiceTest {
    private final OrderFillSyncService noOpFillSyncService = new OrderFillSyncService(null, null, null) {
        @Override
        public void syncActiveOrders() {
        }
    };

    @Test
    void pollRunsDueCronSlotOnce() {
        // Verifies one due cron slot dispatches exactly one scheduled trading cycle.
        MutableClock clock = new MutableClock(Instant.parse("2026-03-14T01:00:01Z"));
        StubTradingCycleUseCase useCase = new StubTradingCycleUseCase(new CycleResult(true, false, "ok"));
        List<String> notifications = new ArrayList<>();
        TradingCycleSchedulerService service = new TradingCycleSchedulerService(
                useCase,
                noOpFillSyncService,
                clock,
                notifications::add,
                schedulerProperties()
        );

        service.poll();
        assertThat(useCase.invocations()).isZero();

        clock.set(Instant.parse("2026-03-14T01:00:05Z"));
        service.poll();

        SchedulerStatusSnapshot status = service.schedulerStatus();
        assertThat(useCase.invocations()).isEqualTo(1);
        assertThat(status.totalSuccesses()).isEqualTo(1);
        assertThat(status.lastTrigger()).isEqualTo("SCHEDULED");
        assertThat(status.pendingRetryAt()).isNull();
        assertThat(notifications).isEmpty();
    }

    @Test
    void failedExecutionSchedulesRetryWithBackoff() {
        // Verifies a failed cycle schedules one retry using the configured backoff policy.
        MutableClock clock = new MutableClock(Instant.parse("2026-03-14T01:00:00Z"));
        StubTradingCycleUseCase useCase = new StubTradingCycleUseCase(
                new CycleResult(true, false, "cycle failed: first"),
                new CycleResult(true, false, "recovered")
        );
        List<String> notifications = new ArrayList<>();
        TradingCycleSchedulerService service = new TradingCycleSchedulerService(
                useCase,
                noOpFillSyncService,
                clock,
                notifications::add,
                schedulerProperties()
        );

        clock.set(Instant.parse("2026-03-14T01:00:05Z"));
        service.poll();

        SchedulerStatusSnapshot failedStatus = service.schedulerStatus();
        assertThat(failedStatus.totalFailures()).isEqualTo(1);
        assertThat(failedStatus.pendingRetryAttempt()).isEqualTo(1);
        assertThat(failedStatus.pendingRetryAt()).isEqualTo(
                ApiTimeSupport.toApiTime(Instant.parse("2026-03-14T01:00:07Z"))
        );

        clock.set(Instant.parse("2026-03-14T01:00:07Z"));
        service.poll();

        SchedulerStatusSnapshot recoveredStatus = service.schedulerStatus();
        assertThat(useCase.invocations()).isEqualTo(2);
        assertThat(recoveredStatus.totalSuccesses()).isEqualTo(1);
        assertThat(recoveredStatus.pendingRetryAt()).isNull();
        assertThat(recoveredStatus.lastTrigger()).isEqualTo("RETRY");
        assertThat(notifications).isEmpty();
    }

    @Test
    void misfireSkipPolicyDropsOverdueSlots() {
        // Verifies SKIP misfire policy drops overdue slots instead of replaying stale executions.
        MutableClock clock = new MutableClock(Instant.parse("2026-03-14T01:00:01Z"));
        StubTradingCycleUseCase useCase = new StubTradingCycleUseCase(new CycleResult(true, false, "ok"));
        List<String> notifications = new ArrayList<>();
        VaultSchedulerProperties properties = schedulerProperties();
        properties.setMisfirePolicy(VaultSchedulerProperties.MisfirePolicy.SKIP);
        properties.setMisfireThresholdMs(1000L);
        TradingCycleSchedulerService service = new TradingCycleSchedulerService(
                useCase,
                noOpFillSyncService,
                clock,
                notifications::add,
                properties
        );

        service.poll();
        clock.set(Instant.parse("2026-03-14T01:00:20Z"));
        service.poll();

        SchedulerStatusSnapshot status = service.schedulerStatus();
        assertThat(useCase.invocations()).isZero();
        assertThat(status.totalMisfires()).isEqualTo(1);
        assertThat(status.lastMessage()).contains("misfire skipped");
        assertThat(notifications).hasSize(1);
    }

    @Test
    void pausePreventsAutomaticDispatchUntilResume() {
        // Verifies pause blocks automatic dispatches until the scheduler is resumed.
        MutableClock clock = new MutableClock(Instant.parse("2026-03-14T01:00:00Z"));
        StubTradingCycleUseCase useCase = new StubTradingCycleUseCase(new CycleResult(true, false, "ok"));
        TradingCycleSchedulerService service = new TradingCycleSchedulerService(
                useCase,
                noOpFillSyncService,
                clock,
                message -> {
                },
                schedulerProperties()
        );

        service.pause();
        clock.set(Instant.parse("2026-03-14T01:00:05Z"));
        service.poll();
        assertThat(useCase.invocations()).isZero();

        service.resume();
        service.poll();
        assertThat(useCase.invocations()).isEqualTo(1);
    }

    private VaultSchedulerProperties schedulerProperties() {
        VaultSchedulerProperties properties = new VaultSchedulerProperties();
        properties.setCron("*/5 * * * * *");
        properties.setZone("UTC");
        properties.setPollDelayMs(200L);
        properties.setMisfireThresholdMs(15_000L);
        properties.setRetryBaseDelayMs(2000L);
        properties.setRetryMaxDelayMs(30_000L);
        properties.setMaxRetryAttempts(3);
        return properties;
    }

    private static final class MutableClock implements ClockPort {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            return now;
        }

        private void set(Instant now) {
            this.now = now;
        }
    }

    private static final class StubTradingCycleUseCase implements RunTradingCycleUseCase {
        private final Queue<CycleResult> results = new ArrayDeque<>();
        private int invocations = 0;

        private StubTradingCycleUseCase(CycleResult... plannedResults) {
            results.addAll(List.of(plannedResults));
        }

        @Override
        public CycleResult runCycle() {
            invocations++;
            return results.isEmpty() ? new CycleResult(true, false, "ok") : results.remove();
        }

        private int invocations() {
            return invocations;
        }
    }
}
