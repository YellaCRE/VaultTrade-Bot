package com.vaulttradebot.domain.common.vo;

public sealed interface AccountBalance permits FiatBalance, CryptoBalance {
    AssetSymbol asset();
}
