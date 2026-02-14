package com.vaulttradebot.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RiskEvaluationServiceTest {
    private final RiskEvaluationService service = new RiskEvaluationService();
    private final RiskPolicy policy = new RiskPolicy(
            new BigDecimal("5000"),
            new BigDecimal("100000"),
            new BigDecimal("0.30"),
            new BigDecimal("0.03"),
            Duration.ofSeconds(30),
            Duration.ofSeconds(5),
            ZoneId.of("Asia/Seoul"),
            new BigDecimal("0.0005"),
            new BigDecimal("0.0020")
    );

    @Test
    void allowsWhenAllPoliciesPass() {
        // Verifies valid inputs pass all checks with ALLOW decision.
        RiskDecision result = service.evaluate(baseContext(new BigDecimal("20000"), Instant.parse("2026-02-14T00:00:00Z")));

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("RISK_CHECK_PASSED");
    }

    @Test
    void reducesOrderWhenCapLowerThanRequest() {
        // Verifies order notional is reduced when request exceeds policy caps.
        RiskDecision result = service.evaluate(baseContext(new BigDecimal("150000"), Instant.parse("2026-02-14T00:00:00Z")));

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.type()).isEqualTo(RiskDecisionType.ALLOW_WITH_LIMIT);
        assertThat(result.reasonCode()).isEqualTo("ORDER_REDUCED_BY_POLICY");
    }

    @Test
    void rejectsOnStaleMarketData() {
        // Verifies stale market snapshot causes fail-closed rejection.
        RiskDecision result = service.evaluate(baseContext(new BigDecimal("20000"), Instant.parse("2026-02-14T00:00:10Z")));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DATA_STALE");
    }

    @Test
    void approveAndReserveAffectsFollowingEvaluation() {
        // Verifies reservation reduces remaining capacity for subsequent evaluations.
        RiskDecision first = service.approveAndReserve(baseContext(new BigDecimal("90000"), Instant.parse("2026-02-14T00:00:00Z")));
        RiskDecision second = service.evaluate(baseContext(new BigDecimal("20000"), Instant.parse("2026-02-14T00:00:00Z")));

        assertThat(first.isAllowed()).isTrue();
        assertThat(first.reservationId()).isNotBlank();
        assertThat(second.isAllowed()).isTrue();
        assertThat(second.reasonCode()).isEqualTo("ORDER_REDUCED_BY_POLICY");
    }

    @Test
    void collectsMetricsByReasonAndDecisionType() {
        // Verifies observability counters aggregate by reason code and decision type.
        service.evaluate(baseContext(new BigDecimal("20000"), Instant.parse("2026-02-14T00:00:00Z")));
        service.evaluate(baseContext(new BigDecimal("20000"), Instant.parse("2026-02-14T00:00:10Z")));

        RiskMetricsSnapshot snapshot = service.snapshotMetrics();
        assertThat(snapshot.totalDecisions()).isEqualTo(2);
        assertThat(snapshot.allowCount()).isEqualTo(1);
        assertThat(snapshot.rejectCount()).isEqualTo(1);
        assertThat(snapshot.reasonCodeCounts()).containsEntry("RISK_CHECK_PASSED", 1L);
        assertThat(snapshot.reasonCodeCounts()).containsEntry("DATA_STALE", 1L);
        assertThat(snapshot.decisionTypeCounts()).containsEntry("ALLOW", 1L);
        assertThat(snapshot.decisionTypeCounts()).containsEntry("REJECT", 1L);
    }

    private RiskContext baseContext(BigDecimal requestNotionalKrw, Instant now) {
        BigDecimal price = new BigDecimal("10000000");
        BigDecimal quantity = requestNotionalKrw.divide(price, 8, java.math.RoundingMode.HALF_UP);
        RiskOrderRequest request = new RiskOrderRequest(
                "acct-1",
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.of(price, Asset.krw()),
                quantity,
                now
        );

        RiskAccountSnapshot account = new RiskAccountSnapshot(
                "acct-1",
                new BigDecimal("1000000"),
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.parse("2026-02-13T23:59:00Z")
        );

        RiskMarketSnapshot market = new RiskMarketSnapshot(
                "KRW-BTC",
                price,
                price,
                price,
                BigDecimal.ZERO,
                Instant.parse("2026-02-14T00:00:00Z"),
                Duration.ofSeconds(5)
        );

        return new RiskContext(request, account, market, policy, Clock.fixed(now, ZoneOffset.UTC));
    }
}
