package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationAdapter implements NotificationPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void notify(String message) {
        log.warn("BOT ALERT: {}", message);
    }
}
