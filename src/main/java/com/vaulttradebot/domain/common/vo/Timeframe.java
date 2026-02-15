package com.vaulttradebot.domain.common.vo;

import java.time.Duration;

/** Supported candle intervals with their fixed duration. */
public enum Timeframe {
    M1(Duration.ofMinutes(1)),
    M5(Duration.ofMinutes(5)),
    M15(Duration.ofMinutes(15)),
    H1(Duration.ofHours(1));

    private final Duration duration;

    Timeframe(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
