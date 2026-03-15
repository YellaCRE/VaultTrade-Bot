package com.vaulttradebot.adapter.in;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path
) {
}
