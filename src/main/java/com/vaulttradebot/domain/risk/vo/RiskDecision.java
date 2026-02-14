package com.vaulttradebot.domain.risk.vo;

import java.math.BigDecimal;
import java.util.Map;

public record RiskDecision(
        RiskDecisionType type,
        String reasonCode,
        String reason,
        BigDecimal approvedOrderKrw,
        String reservationId,
        Map<String, String> metrics
) {
    public RiskDecision {
        if (type == null || reasonCode == null || reason == null || metrics == null) {
            throw new IllegalArgumentException("risk decision fields must not be null");
        }
        if ((type == RiskDecisionType.ALLOW || type == RiskDecisionType.ALLOW_WITH_LIMIT)
                && (approvedOrderKrw == null || approvedOrderKrw.signum() <= 0)) {
            throw new IllegalArgumentException("approvedOrderKrw must be positive for allowed decisions");
        }
    }

    /** Creates an ALLOW decision with approved notional and metrics. */
    public static RiskDecision allow(String reasonCode, String reason, BigDecimal approvedOrderKrw, Map<String, String> metrics) {
        return new RiskDecision(RiskDecisionType.ALLOW, reasonCode, reason, approvedOrderKrw, null, Map.copyOf(metrics));
    }

    /** Creates an ALLOW_WITH_LIMIT decision when order size must be reduced. */
    public static RiskDecision allowWithLimit(
            String reasonCode,
            String reason,
            BigDecimal approvedOrderKrw,
            Map<String, String> metrics
    ) {
        return new RiskDecision(RiskDecisionType.ALLOW_WITH_LIMIT, reasonCode, reason, approvedOrderKrw, null, Map.copyOf(metrics));
    }

    /** Creates a REJECT decision with rejection reason metadata. */
    public static RiskDecision reject(String reasonCode, String reason, Map<String, String> metrics) {
        return new RiskDecision(RiskDecisionType.REJECT, reasonCode, reason, null, null, Map.copyOf(metrics));
    }

    /** Returns true when this decision allows order execution. */
    public boolean isAllowed() {
        return type == RiskDecisionType.ALLOW || type == RiskDecisionType.ALLOW_WITH_LIMIT;
    }

    /** Attaches reservation id after successful approve-and-reserve flow. */
    public RiskDecision withReservationId(String reservationId) {
        return new RiskDecision(type, reasonCode, reason, approvedOrderKrw, reservationId, metrics);
    }
}
