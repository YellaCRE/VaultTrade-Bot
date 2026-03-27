package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.OrderOutboxTransactionPort;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.portfolio.PositionSnapshot;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
    private final InMemoryPortfolioRepository portfolioRepository;
    private final Object txLock = new Object();

    public InMemoryOrderOutboxTransactionAdapter(
            InMemoryOrderRepository orderRepository,
            InMemoryOutboxRepository outboxRepository
    ) {
        this(orderRepository, outboxRepository, null, null);
    }

    @Autowired
    public InMemoryOrderOutboxTransactionAdapter(
            InMemoryOrderRepository orderRepository,
            InMemoryOutboxRepository outboxRepository,
            InMemoryTradingCycleSnapshotRepository cycleSnapshotRepository,
            InMemoryPortfolioRepository portfolioRepository
    ) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.cycleSnapshotRepository = cycleSnapshotRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public InMemoryOrderOutboxTransactionAdapter(
            InMemoryOrderRepository orderRepository,
            InMemoryOutboxRepository outboxRepository,
            InMemoryTradingCycleSnapshotRepository cycleSnapshotRepository
    ) {
        this(orderRepository, outboxRepository, cycleSnapshotRepository, null);
    }

    public InMemoryOrderOutboxTransactionAdapter(
            InMemoryOrderRepository orderRepository,
            InMemoryOutboxRepository outboxRepository,
            InMemoryPortfolioRepository portfolioRepository
    ) {
        this(orderRepository, outboxRepository, null, portfolioRepository);
    }

    @Override
    public void execute(Runnable action) {
        synchronized (txLock) {
            List<Order> orderSnapshot = orderRepository.snapshot();
            List<OutboxMessage> outboxSnapshot = outboxRepository.snapshot();
            java.util.concurrent.ConcurrentHashMap<String, com.vaulttradebot.application.usecase.TradingCycleSnapshot> cycleSnapshot =
                    cycleSnapshotRepository == null ? null : cycleSnapshotRepository.snapshot();
            ConcurrentHashMap<String, PositionSnapshot> portfolioSnapshot =
                    portfolioRepository == null ? null : portfolioRepository.snapshot();
            try {
                action.run();
            } catch (RuntimeException e) {
                orderRepository.restore(orderSnapshot);
                outboxRepository.restore(outboxSnapshot);
                if (cycleSnapshotRepository != null && cycleSnapshot != null) {
                    cycleSnapshotRepository.restore(cycleSnapshot);
                }
                if (portfolioRepository != null && portfolioSnapshot != null) {
                    portfolioRepository.restore(portfolioSnapshot);
                }
                throw e;
            }
        }
    }
}
