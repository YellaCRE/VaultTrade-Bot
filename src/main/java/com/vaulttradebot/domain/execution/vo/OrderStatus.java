package com.vaulttradebot.domain.execution.vo;

public enum OrderStatus {
    NEW,
    OPEN,
    PARTIAL_FILLED,
    FILLED,
    CANCEL_REQUESTED,
    CANCELED,
    REJECTED
}
