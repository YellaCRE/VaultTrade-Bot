package com.vaulttradebot.adapter.in;

import java.time.OffsetDateTime;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path
) {
}
