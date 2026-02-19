package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.TradingCycleLockPort;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTradingCycleLockAdapter implements TradingCycleLockPort {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String lockKey) {
        ReentrantLock lock = locks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        // Try-lock avoids overlapping cycle execution for the same strategy+pair key.
        return lock.tryLock();
    }

    @Override
    public void release(String lockKey) {
        ReentrantLock lock = locks.get(lockKey);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
