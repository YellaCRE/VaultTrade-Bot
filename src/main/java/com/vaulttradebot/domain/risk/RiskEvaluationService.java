package com.vaulttradebot.domain.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.vaulttradebot.domain.risk.snapshot.RiskMetricsSnapshot;
import com.vaulttradebot.domain.risk.vo.RiskContext;
import com.vaulttradebot.domain.risk.vo.RiskDecisionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RiskEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(RiskEvaluationService.class);

    private final ConcurrentHashMap<String, Object> accountLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Reservation>> reservationsByAccount =
            new ConcurrentHashMap<>();
    private final AtomicLong totalDecisions = new AtomicLong(0);
    private final AtomicLong allowDecisions = new AtomicLong(0);
    private final AtomicLong rejectDecisions = new AtomicLong(0);
    private final AtomicLong allowWithLimitDecisions = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> reasonCodeCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> decisionTypeCounts = new ConcurrentHashMap<>();

    /** Evaluates risk rules without creating a reservation. */
    public RiskDecision evaluate(RiskContext context) {
        try {
            BigDecimal reserved = totalReserved(context.orderRequest().accountId(), context.now());
            RiskDecision decision = evaluateInternal(context.withReservedCash(reserved));
            return emit(context, decision, "evaluate");
        } catch (Exception e) {
            RiskDecision decision =
                    RiskDecision.reject("POLICY_ENGINE_ERROR", "risk engine failure: " + e.getMessage(), Map.of());
            return emit(context, decision, "evaluate");
        }
    }

    /** Evaluates risk rules and atomically reserves approved notional on success. */
    public RiskDecision approveAndReserve(RiskContext context) {
        Object lock = accountLocks.computeIfAbsent(context.orderRequest().accountId(), key -> new Object());
        synchronized (lock) {
            try {
                BigDecimal reserved = totalReserved(context.orderRequest().accountId(), context.now());
                RiskContext withReserved = context.withReservedCash(reserved);
                RiskDecision decision = evaluateInternal(withReserved);
                if (!decision.isAllowed()) {
                    return emit(context, decision, "approveAndReserve");
                }

                String reservationId = UUID.randomUUID().toString();
                Reservation reservation = new Reservation(
                        reservationId,
                        context.orderRequest().accountId(),
                        decision.approvedOrderKrw(),
                        context.now().plus(Duration.ofMinutes(3))
                );
                reservationsByAccount
                        .computeIfAbsent(context.orderRequest().accountId(), key -> new ConcurrentHashMap<>())
                        .put(reservationId, reservation);
                return emit(context, decision.withReservationId(reservationId), "approveAndReserve");
            } catch (Exception e) {
                RiskDecision decision =
                        RiskDecision.reject("POLICY_ENGINE_ERROR", "risk engine failure: " + e.getMessage(), Map.of());
                return emit(context, decision, "approveAndReserve");
            }
        }
    }

    /** Releases a previously created reservation for the account. */
    public void releaseReservation(String accountId, String reservationId) {
        if (accountId == null || accountId.isBlank() || reservationId == null || reservationId.isBlank()) {
            return;
        }
        ConcurrentHashMap<String, Reservation> reservations = reservationsByAccount.get(accountId);
        if (reservations != null) {
            reservations.remove(reservationId);
        }
    }

    /** Returns immutable counters for risk decisions and reason codes. */
    public RiskMetricsSnapshot snapshotMetrics() {
        return new RiskMetricsSnapshot(
                totalDecisions.get(),
                allowDecisions.get(),
                rejectDecisions.get(),
                allowWithLimitDecisions.get(),
                toImmutableCountMap(reasonCodeCounts),
                toImmutableCountMap(decisionTypeCounts)
        );
    }

    /** Applies pre-trade policy checks and returns allow/reject decision. */
    private RiskDecision evaluateInternal(RiskContext context) {
        RiskPolicy policy = context.policy();
        BigDecimal requested = context.requestedOrderKrwConservative();
        BigDecimal availableAfterReservation = context.accountSnapshot().availableCashKrw()
                .subtract(context.accountSnapshot().reservedCashKrw())
                .max(BigDecimal.ZERO);
        BigDecimal maxExposureKrw = context.accountSnapshot().referenceEquityKrw()
                .multiply(policy.maxExposureRatio());
        BigDecimal remainingExposureKrw = maxExposureKrw.subtract(context.accountSnapshot().currentExposureKrw());
        BigDecimal dailyLossRatio = ratio(context.accountSnapshot().dailyLossKrw(), context.accountSnapshot().referenceEquityKrw());

        Map<String, String> metrics = Map.of(
                "requestedKrw", requested.toPlainString(),
                "availableKrw", availableAfterReservation.toPlainString(),
                "reservedKrw", context.accountSnapshot().reservedCashKrw().toPlainString(),
                "remainingExposureKrw", remainingExposureKrw.max(BigDecimal.ZERO).toPlainString(),
                "dailyLossRatio", dailyLossRatio.toPlainString()
        );

        if (isMarketDataStale(context)) {
            return RiskDecision.reject("DATA_STALE", "market data is stale", metrics);
        }

        if (dailyLossRatio.compareTo(policy.maxDailyLossRatio()) > 0) {
            return RiskDecision.reject("DAILY_LOSS_LIMIT_EXCEEDED", "daily loss ratio exceeds policy", metrics);
        }

        Instant lastOrderAt = context.accountSnapshot().lastOrderAt();
        if (lastOrderAt != null) {
            Duration elapsed = Duration.between(lastOrderAt, context.now());
            if (elapsed.compareTo(policy.cooldown()) < 0) {
                return RiskDecision.reject("COOLDOWN_ACTIVE", "cooldown is active", metrics);
            }
        }

        if (remainingExposureKrw.signum() <= 0) {
            return RiskDecision.reject("EXPOSURE_LIMIT_EXCEEDED", "no remaining exposure capacity", metrics);
        }

        BigDecimal capped = requested
                .min(policy.maxOrderKrw())
                .min(availableAfterReservation)
                .min(remainingExposureKrw)
                .setScale(0, RoundingMode.DOWN);

        if (capped.compareTo(policy.minOrderKrw()) < 0) {
            return RiskDecision.reject("ORDER_CAP_BELOW_MINIMUM", "order cap is below minimum tradable notional", metrics);
        }

        if (capped.compareTo(requested) < 0) {
            return RiskDecision.allowWithLimit(
                    "ORDER_REDUCED_BY_POLICY",
                    "order size reduced by policy constraints",
                    capped,
                    metrics
            );
        }

        return RiskDecision.allow("RISK_CHECK_PASSED", "risk checks passed", capped, metrics);
    }

    /** Checks whether market data is older than the effective stale threshold. */
    private boolean isMarketDataStale(RiskContext context) {
        Duration snapshotStaleAfter = context.marketSnapshot().staleAfter();
        Duration policyStaleAfter = context.policy().marketDataStaleAfter();
        Duration effectiveStaleAfter = snapshotStaleAfter.compareTo(policyStaleAfter) <= 0
                ? snapshotStaleAfter
                : policyStaleAfter;
        Instant staleThreshold = context.marketSnapshot().asOf().plus(effectiveStaleAfter);
        return staleThreshold.isBefore(context.now());
    }

    /** Sums active reservations and removes expired reservations. */
    private BigDecimal totalReserved(String accountId, Instant referenceNow) {
        ConcurrentHashMap<String, Reservation> reservations = reservationsByAccount.get(accountId);
        if (reservations == null || reservations.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Reservation reservation : reservations.values()) {
            if (reservation.expiresAt().isBefore(referenceNow)) {
                reservations.remove(reservation.reservationId());
                continue;
            }
            total = total.add(reservation.amountKrw());
        }
        return total;
    }

    /** Returns ratio with fixed scale and zero guard on denominator. */
    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    /** Records metrics and structured logs for each risk decision. */
    private RiskDecision emit(RiskContext context, RiskDecision decision, String operation) {
        recordMetrics(decision);
        logDecision(context, decision, operation);
        return decision;
    }

    /** Increments decision counters used for observability snapshots. */
    private void recordMetrics(RiskDecision decision) {
        totalDecisions.incrementAndGet();
        switch (decision.type()) {
            case ALLOW -> allowDecisions.incrementAndGet();
            case REJECT -> rejectDecisions.incrementAndGet();
            case ALLOW_WITH_LIMIT -> allowWithLimitDecisions.incrementAndGet();
        }
        reasonCodeCounts.computeIfAbsent(decision.reasonCode(), key -> new AtomicLong(0)).incrementAndGet();
        decisionTypeCounts.computeIfAbsent(decision.type().name(), key -> new AtomicLong(0)).incrementAndGet();
    }

    /** Writes structured decision logs with reason code and computed metrics. */
    private void logDecision(RiskContext context, RiskDecision decision, String operation) {
        if (decision.type() == RiskDecisionType.REJECT) {
            log.warn(
                    "risk_decision operation={} accountId={} market={} side={} type={} reasonCode={} requestedKrw={} approvedKrw={} reservationId={} metrics={}",
                    operation,
                    context.orderRequest().accountId(),
                    context.orderRequest().market().value(),
                    context.orderRequest().side(),
                    decision.type(),
                    decision.reasonCode(),
                    context.requestedOrderKrwConservative().toPlainString(),
                    decision.approvedOrderKrw() == null ? "0" : decision.approvedOrderKrw().toPlainString(),
                    decision.reservationId() == null ? "-" : decision.reservationId(),
                    decision.metrics()
            );
            return;
        }

        log.info(
                "risk_decision operation={} accountId={} market={} side={} type={} reasonCode={} requestedKrw={} approvedKrw={} reservationId={} metrics={}",
                operation,
                context.orderRequest().accountId(),
                context.orderRequest().market().value(),
                context.orderRequest().side(),
                decision.type(),
                decision.reasonCode(),
                context.requestedOrderKrwConservative().toPlainString(),
                decision.approvedOrderKrw().toPlainString(),
                decision.reservationId() == null ? "-" : decision.reservationId(),
                decision.metrics()
        );
    }

    /** Converts atomic counters to immutable map snapshot. */
    private Map<String, Long> toImmutableCountMap(ConcurrentHashMap<String, AtomicLong> source) {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return Map.copyOf(result);
    }

    private record Reservation(
            String reservationId,
            String accountId,
            BigDecimal amountKrw,
            Instant expiresAt
    ) {
    }
}
