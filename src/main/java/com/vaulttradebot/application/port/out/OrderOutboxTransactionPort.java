package com.vaulttradebot.application.port.out;

public interface OrderOutboxTransactionPort {
    void execute(Runnable action);
}
