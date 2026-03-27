package com.vaulttradebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VaultTradeBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(VaultTradeBotApplication.class, args);
    }
}
