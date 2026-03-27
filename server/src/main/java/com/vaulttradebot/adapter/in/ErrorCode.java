package com.vaulttradebot.adapter.in;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    CIRCUIT_BREAKER_OPEN(HttpStatus.SERVICE_UNAVAILABLE),
    KILL_SWITCH_ACTIVE(HttpStatus.LOCKED),
    UPSTREAM_REQUEST_FAILED(HttpStatus.BAD_GATEWAY),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
