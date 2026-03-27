package com.vaulttradebot.application.usecase;

import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import com.vaulttradebot.application.port.in.SchedulerControlUseCase;
import com.vaulttradebot.application.port.in.SchedulerQueryUseCase;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.NotificationPort;
import com.vaulttradebot.application.query.SchedulerStatusSnapshot;
import com.vaulttradebot.config.ApiTimeSupport;
import com.vaulttradebot.config.VaultSchedulerProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class TradingCycleSchedulerService implements SchedulerQueryUseCase, SchedulerControlUseCase {
    private final RunTradingCycleUseCase runTradingCycleUseCase;
    private final OrderFillSyncService orderFillSyncService;
    private final ClockPort clockPort;
    private final NotificationPort notificationPort;
    private final VaultSchedulerProperties properties;
    private final Object monitor = new Object();

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean executionInProgress = new AtomicBoolean(false);
    private final AtomicReference<ZonedDateTime> nextPlannedAt = new AtomicReference<>();
    private final AtomicReference<RetryPlan> pendingRetry = new AtomicReference<>();
    private final AtomicReference<Instant> lastScheduledAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastStartedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastCompletedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
    private final AtomicReference<String> lastTrigger = new AtomicReference<>("NONE");
    private final AtomicReference<String> lastMessage = new AtomicReference<>("scheduler initialized");
    private final AtomicLong totalDispatches = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalMisfires = new AtomicLong(0);
    private final AtomicLong totalOverlapPreventions = new AtomicLong(0);
    private final AtomicInteger pendingRetryAttempt = new AtomicInteger(0);

    public TradingCycleSchedulerService(
            RunTradingCycleUseCase runTradingCycleUseCase,
            OrderFillSyncService orderFillSyncService,
            ClockPort clockPort,
            NotificationPort notificationPort,
            VaultSchedulerProperties properties
    ) {
        this.runTradingCycleUseCase = runTradingCycleUseCase;
        this.orderFillSyncService = orderFillSyncService;
        this.clockPort = clockPort;
        this.notificationPort = notificationPort;
        this.properties = properties;
    }

    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }

        orderFillSyncService.syncActiveOrders();

        DispatchPlan plan;
        synchronized (monitor) {
            plan = nextPlan(clockPort.now());
        }
        if (plan == null) {
            return;
        }

        execute(plan);
    }

    @Override
    public SchedulerStatusSnapshot schedulerStatus() {
        RetryPlan retry = pendingRetry.get();
        ZonedDateTime planned = nextPlannedAt.get();
        return new SchedulerStatusSnapshot(
                properties.isEnabled(),
                paused.get(),
                executionInProgress.get(),
                properties.getCron(),
                properties.getZone(),
                properties.getMisfirePolicy().name(),
                planned == null ? null : ApiTimeSupport.toApiTime(planned.toInstant()),
                retry == null ? null : ApiTimeSupport.toApiTime(retry.executeAt()),
                pendingRetryAttempt.get(),
                ApiTimeSupport.toApiTime(lastScheduledAt.get()),
                ApiTimeSupport.toApiTime(lastStartedAt.get()),
                ApiTimeSupport.toApiTime(lastCompletedAt.get()),
                ApiTimeSupport.toApiTime(lastSuccessAt.get()),
                ApiTimeSupport.toApiTime(lastFailureAt.get()),
                lastTrigger.get(),
                lastMessage.get(),
                totalDispatches.get(),
                totalSuccesses.get(),
                totalFailures.get(),
                totalMisfires.get(),
                totalOverlapPreventions.get()
        );
    }

    @Override
    public SchedulerStatusSnapshot pause() {
        paused.set(true);
        lastMessage.set("scheduler paused");
        return schedulerStatus();
    }

    @Override
    public SchedulerStatusSnapshot resume() {
        paused.set(false);
        synchronized (monitor) {
            ensureScheduleInitialized(clockPort.now());
        }
        lastMessage.set("scheduler resumed");
        return schedulerStatus();
    }

    @Override
    public CycleResult triggerNow() {
        if (!executionInProgress.compareAndSet(false, true)) {
            totalOverlapPreventions.incrementAndGet();
            lastMessage.set("manual trigger skipped: scheduler execution already in progress");
            return new CycleResult(false, false, "scheduler execution already in progress");
        }

        Instant now = clockPort.now();
        totalDispatches.incrementAndGet();
        lastScheduledAt.set(now);
        lastStartedAt.set(now);
        lastTrigger.set("MANUAL");
        try {
            return finalizeDispatch(runSafely(), DispatchPlan.manual(now));
        } finally {
            executionInProgress.set(false);
        }
    }

    private DispatchPlan nextPlan(Instant now) {
        if (paused.get()) {
            return null;
        }

        ensureScheduleInitialized(now);
        if (executionInProgress.get()) {
            return null;
        }

        RetryPlan retry = pendingRetry.get();
        if (retry != null && !retry.executeAt().isAfter(now)) {
            pendingRetry.set(null);
            pendingRetryAttempt.set(0);
            return reserveDispatch(DispatchPlan.retry(retry.scheduledFor(), retry.attempt()));
        }

        ZonedDateTime nowAtZone = ZonedDateTime.ofInstant(now, zoneId());
        ZonedDateTime planned = nextPlannedAt.get();
        if (planned == null || nowAtZone.isBefore(planned)) {
            return null;
        }

        // Detect overdue fire times before dispatching the next slot.
        Duration lag = Duration.between(planned, nowAtZone);
        if (lag.toMillis() > properties.getMisfireThresholdMs()) {
            totalMisfires.incrementAndGet();
            return handleMisfire(nowAtZone, planned);
        }

        nextPlannedAt.set(nextExecution(planned));
        return reserveDispatch(DispatchPlan.scheduled(planned.toInstant(), 0));
    }

    private DispatchPlan handleMisfire(ZonedDateTime nowAtZone, ZonedDateTime planned) {
        ZonedDateTime latestDue = planned;
        ZonedDateTime cursor = planned;
        long skipped = 0;
        while (!cursor.isAfter(nowAtZone)) {
            latestDue = cursor;
            cursor = nextExecution(cursor);
            if (!cursor.isAfter(nowAtZone)) {
                skipped++;
            }
        }
        nextPlannedAt.set(cursor);

        if (properties.getMisfirePolicy() == VaultSchedulerProperties.MisfirePolicy.SKIP) {
            // Skip backlog cleanly when the operator prefers freshness over catch-up.
            lastMessage.set("misfire skipped " + (skipped + 1) + " overdue slot(s)");
            notificationPort.notify("Trading scheduler skipped overdue slot(s) because of misfire policy");
            return null;
        }

        // Collapse backlog into one replay so we preserve idempotent execution boundaries.
        lastMessage.set("misfire collapsed backlog into one immediate execution");
        notificationPort.notify("Trading scheduler detected a misfire and will execute once immediately");
        return reserveDispatch(DispatchPlan.scheduled(Objects.requireNonNull(latestDue, "latestDue").toInstant(), 0));
    }

    private DispatchPlan reserveDispatch(DispatchPlan plan) {
        if (!executionInProgress.compareAndSet(false, true)) {
            totalOverlapPreventions.incrementAndGet();
            lastMessage.set("scheduler prevented an overlapping execution");
            return null;
        }
        totalDispatches.incrementAndGet();
        lastScheduledAt.set(plan.scheduledFor());
        lastStartedAt.set(clockPort.now());
        lastTrigger.set(plan.trigger());
        return plan;
    }

    private void execute(DispatchPlan plan) {
        try {
            finalizeDispatch(runSafely(), plan);
        } finally {
            executionInProgress.set(false);
        }
    }

    private CycleResult finalizeDispatch(CycleResult result, DispatchPlan plan) {
        Instant completedAt = clockPort.now();
        lastCompletedAt.set(completedAt);
        lastMessage.set(result.message());

        if (result.failed()) {
            totalFailures.incrementAndGet();
            lastFailureAt.set(completedAt);
            scheduleRetry(plan, completedAt);
            return result;
        }

        totalSuccesses.incrementAndGet();
        lastSuccessAt.set(completedAt);
        clearRetry();
        return result;
    }

    private void scheduleRetry(DispatchPlan plan, Instant completedAt) {
        if (!plan.retryable() || plan.attempt() >= properties.getMaxRetryAttempts()) {
            clearRetry();
            notificationPort.notify("Trading scheduler exhausted retries after failure: " + lastMessage.get());
            return;
        }

        int nextAttempt = plan.attempt() + 1;
        Instant retryAt = completedAt.plusMillis(backoffMillis(nextAttempt));
        pendingRetry.set(new RetryPlan(plan.scheduledFor(), retryAt, nextAttempt));
        pendingRetryAttempt.set(nextAttempt);
        // Keep retries attached to the same logical slot for safe replay.
        lastMessage.set(lastMessage.get() + " (retry " + nextAttempt + " scheduled)");
    }

    private void clearRetry() {
        pendingRetry.set(null);
        pendingRetryAttempt.set(0);
    }

    private CycleResult runSafely() {
        try {
            return runTradingCycleUseCase.runCycle();
        } catch (RuntimeException error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) {
                message = error.getClass().getSimpleName();
            }
            return new CycleResult(true, false, "cycle failed: " + message);
        }
    }

    private void ensureScheduleInitialized(Instant now) {
        if (nextPlannedAt.get() != null) {
            return;
        }
        ZonedDateTime nowAtZone = ZonedDateTime.ofInstant(now, zoneId());
        ZonedDateTime initial = cron().next(nowAtZone.minusNanos(1));
        nextPlannedAt.set(initial == null ? nowAtZone.plusSeconds(1) : initial);
    }

    private ZoneId zoneId() {
        return ZoneId.of(properties.getZone());
    }

    private CronExpression cron() {
        return CronExpression.parse(properties.getCron());
    }

    private ZonedDateTime nextExecution(ZonedDateTime base) {
        ZonedDateTime next = cron().next(base);
        return next == null ? base.plusYears(100) : next;
    }

    private long backoffMillis(int attempt) {
        long delay = properties.getRetryBaseDelayMs();
        for (int i = 1; i < attempt; i++) {
            if (delay >= properties.getRetryMaxDelayMs()) {
                return properties.getRetryMaxDelayMs();
            }
            delay = Math.min(properties.getRetryMaxDelayMs(), delay * 2);
        }
        return delay;
    }

    private record DispatchPlan(Instant scheduledFor, String trigger, int attempt, boolean retryable) {
        private static DispatchPlan scheduled(Instant scheduledFor, int attempt) {
            return new DispatchPlan(scheduledFor, "SCHEDULED", attempt, true);
        }

        private static DispatchPlan retry(Instant scheduledFor, int attempt) {
            return new DispatchPlan(scheduledFor, "RETRY", attempt, true);
        }

        private static DispatchPlan manual(Instant scheduledFor) {
            return new DispatchPlan(scheduledFor, "MANUAL", 0, false);
        }
    }

    private record RetryPlan(Instant scheduledFor, Instant executeAt, int attempt) {
        private RetryPlan {
            Objects.requireNonNull(scheduledFor, "scheduledFor");
            Objects.requireNonNull(executeAt, "executeAt");
        }
    }
}
