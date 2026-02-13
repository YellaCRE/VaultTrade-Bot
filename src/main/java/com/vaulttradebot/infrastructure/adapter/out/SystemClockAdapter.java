package com.vaulttradebot.infrastructure.adapter.out;

import com.vaulttradebot.application.port.out.ClockPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClockAdapter implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
