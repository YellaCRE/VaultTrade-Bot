package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.OrderOutboxTransactionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "jdbc")
public class JdbcOrderOutboxTransactionAdapter implements OrderOutboxTransactionPort {
    private final TransactionTemplate transactionTemplate;

    public JdbcOrderOutboxTransactionAdapter(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void execute(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
