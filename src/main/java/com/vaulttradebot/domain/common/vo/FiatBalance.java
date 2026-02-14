package com.vaulttradebot.domain.common.vo;

public record FiatBalance(
        AssetSymbol asset,
        Money available,
        Money locked
) implements AccountBalance {
    public FiatBalance {
        if (asset == null || available == null || locked == null) {
            throw new IllegalArgumentException("fiat balance fields must not be null");
        }
        if (!asset.isFiat()) {
            throw new IllegalArgumentException("fiat balance asset must be fiat");
        }
        if (!asset.equals(available.currency()) || !asset.equals(locked.currency())) {
            throw new IllegalArgumentException("money currency must match asset symbol");
        }
    }
}
