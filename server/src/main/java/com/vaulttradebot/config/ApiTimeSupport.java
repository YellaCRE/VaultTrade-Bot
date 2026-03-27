package com.vaulttradebot.config;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class ApiTimeSupport {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    private ApiTimeSupport() {
    }

    public static OffsetDateTime toApiTime(Instant instant) {
        return instant == null ? null : instant.atZone(API_ZONE).toOffsetDateTime();
    }
}
