package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.OrderOutboxTransactionPort;
import com.vaulttradebot.domain.execution.Order;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulates atomic order+outbox write by snapshotting and restoring on failure. */
@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryOrderOutboxTransactionAdapter implements OrderOutboxTransactionPort {
    private final InMemoryOrderRepository orderRepository;
    private final InMemoryOutboxRepository outboxRepository;
    private final InMemoryTradingCycleSnapshotRepository cycleSnapshotRepository;
    private final Object txLock = new Object();

    public InMemoryOrderOutboxTransactionAdapter(
            InMemoryOrderRepository orderRepository,
            InMemoryOutboxRepository outboxRepository
    ) {
        this(orderRepository, outboxRepository, null);
    }

    @Autowired
    public InMemoryOrderOutboxTransactionAdapter(
            InMemoryOrderRepository orderRepository,
            InMemoryOutboxRepository outboxRepository,
            InMemoryTradingCycleSnapshotRepository cycleSnapshotRepository
    ) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.cycleSnapshotRepository = cycleSnapshotRepository;
    }

    @Override
    public void execute(Runnable action) {
        synchronized (txLock) {
            List<Order> orderSnapshot = orderRepository.snapshot();
            List<OutboxMessage> outboxSnapshot = outboxRepository.snapshot();
            java.util.concurrent.ConcurrentHashMap<String, com.vaulttradebot.application.usecase.TradingCycleSnapshot> cycleSnapshot =
                    cycleSnapshotRepository == null ? null : cycleSnapshotRepository.snapshot();
            try {
                action.run();
            } catch (RuntimeException e) {
                orderRepository.restore(orderSnapshot);
                outboxRepository.restore(outboxSnapshot);
                if (cycleSnapshotRepository != null && cycleSnapshot != null) {
                    cycleSnapshotRepository.restore(cycleSnapshot);
                }
                throw e;
            }
        }
    }
}
