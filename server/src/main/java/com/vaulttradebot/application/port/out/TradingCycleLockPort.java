package com.vaulttradebot.application.port.out;

public interface TradingCycleLockPort {
    boolean tryAcquire(String lockKey);

    void release(String lockKey);
}
