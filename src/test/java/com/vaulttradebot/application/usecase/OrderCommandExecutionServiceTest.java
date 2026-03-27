package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaulttradebot.adapter.out.InMemoryOrderOutboxTransactionAdapter;
import com.vaulttradebot.adapter.out.InMemoryOrderRepository;
import com.vaulttradebot.adapter.out.InMemoryOutboxRepository;
import com.vaulttradebot.adapter.out.InMemoryPortfolioRepository;
import com.vaulttradebot.adapter.out.PaperExchangeTradingAdapter;
import com.vaulttradebot.application.outbox.OutboxMessage;
import com.vaulttradebot.application.port.out.ClockPort;
import com.vaulttradebot.application.port.out.OutboxPayloadSerializer;
import com.vaulttradebot.domain.execution.event.OrderDomainEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderCommandExecutionServiceTest {

    @Test
    void executesOrderCommandRequestedAndPersistsFilledOrderAndPosition() {
        // Verifies outbox command execution reaches exchange and persists both order and portfolio state.
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        InMemoryPortfolioRepository portfolioRepository = new InMemoryPortfolioRepository();
        ClockPort clock = () -> Instant.parse("2026-03-27T12:00:00Z");
        OrderPersistenceService orderPersistenceService = new OrderPersistenceService(
                orderRepository,
                outboxRepository,
                portfolioRepository,
                new InMemoryOrderOutboxTransactionAdapter(orderRepository, outboxRepository, portfolioRepository),
                clock,
                serializer()
        );
        OrderCommandExecutionService service = new OrderCommandExecutionService(
                new PaperExchangeTradingAdapter(),
                orderRepository,
                orderPersistenceService,
                new ObjectMapper()
        );

        service.execute(message(clock.now()));

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findAll().getFirst().status().name()).isEqualTo("FILLED");
        assertThat(portfolioRepository.findByMarket("KRW-BTC")).isPresent();
        assertThat(portfolioRepository.findByMarket("KRW-BTC").orElseThrow().quantity())
                .isEqualByComparingTo("0.00200000");
        assertThat(outboxRepository.findAll()).isNotEmpty();
    }

    private OutboxMessage message(Instant now) {
        return new OutboxMessage(
                "msg-1",
                "TradingCycle",
                "cycle-1",
                "OrderCommandRequested",
                """
                {
                  "cycleId":"cycle-1",
                  "strategyId":"MovingAverageCrossStrategy",
                  "dataTimestamp":"2026-03-27T12:00:00Z",
                  "decision":"PLACE",
                  "reason":"signal confirmed",
                  "commandType":"CREATE",
                  "targetOrderId":"",
                  "market":"KRW-BTC",
                  "side":"BUY",
                  "orderType":"LIMIT",
                  "price":"50000000",
                  "quantity":"0.00200000",
                  "clientOrderId":"client-1"
                }
                """,
                1,
                now,
                now,
                null,
                0,
                null,
                now,
                null
        );
    }

    private OutboxPayloadSerializer serializer() {
        return new OutboxPayloadSerializer() {
            @Override
            public String serialize(OrderDomainEvent event) {
                return "{\"eventType\":\"" + event.getClass().getSimpleName() + "\"}";
            }

            @Override
            public int payloadVersion() {
                return 1;
            }
        };
    }
}
