package com.vaulttradebot.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionSnapshot(
        String marketSymbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal realizedPnL,
        long version,
        Instant updatedAt
) {
    /** Validates persisted snapshot invariants before reconstruction. */
    public PositionSnapshot {
        if (marketSymbol == null || marketSymbol.isBlank()) {
            throw new IllegalArgumentException("marketSymbol must not be blank");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity must be >= 0");
        }
        if (avgPrice == null || avgPrice.signum() < 0) {
            throw new IllegalArgumentException("avgPrice must be >= 0");
        }
        if (realizedPnL == null) {
            throw new IllegalArgumentException("realizedPnL must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        if (quantity.signum() == 0 && avgPrice.signum() != 0) {
            throw new IllegalArgumentException("avgPrice must be 0 when quantity is 0");
        }
    }
}
