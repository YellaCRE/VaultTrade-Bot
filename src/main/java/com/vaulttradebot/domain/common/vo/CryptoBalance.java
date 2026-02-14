package com.vaulttradebot.domain.common.vo;

import java.util.Optional;

public record CryptoBalance(
        AssetSymbol asset,
        Quantity available,
        Quantity locked,
        Optional<Price> avgBuyPrice
) implements AccountBalance {
    public CryptoBalance {
        if (asset == null || available == null || locked == null || avgBuyPrice == null) {
            throw new IllegalArgumentException("crypto balance fields must not be null");
        }
        if (asset.isFiat()) {
            throw new IllegalArgumentException("crypto balance asset must be crypto");
        }
    }

    public Quantity total() {
        return available.add(locked);
    }
}
