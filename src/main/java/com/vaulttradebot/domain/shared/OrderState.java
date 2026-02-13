package com.vaulttradebot.domain.shared;

public enum OrderState {
    CREATED,
    SUBMITTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED
}
