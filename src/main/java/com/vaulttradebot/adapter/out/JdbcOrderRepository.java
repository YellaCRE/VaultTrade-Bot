package com.vaulttradebot.adapter.out;

import com.vaulttradebot.application.port.out.OrderRepository;
import com.vaulttradebot.domain.common.vo.IdempotencyKey;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.common.vo.Side;
import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.domain.execution.vo.OrderId;
import com.vaulttradebot.domain.execution.vo.OrderStatus;
import com.vaulttradebot.domain.execution.vo.OrderType;
import com.vaulttradebot.domain.execution.vo.StrategyId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.persistence.mode", havingValue = "jdbc")
public class JdbcOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Order save(Order order) {
        int updated = jdbcTemplate.update(
                """
                UPDATE orders
                SET market=?, order_type=?, side=?, quantity=?, price_krw=?, minimum_profit_price_krw=?,
                    strategy_id=?, idempotency_key=?, created_at=?, status=?, executed_quantity=?,
                    executed_amount_krw=?, version=?
                WHERE id=?
                """,
                order.market().value(),
                order.orderType().name(),
                order.side().name(),
                order.quantity(),
                order.price().amount(),
                order.minimumProfitPrice() == null ? null : order.minimumProfitPrice().amount(),
                order.strategyId().value(),
                order.idempotencyKey().value(),
                Timestamp.from(order.createdAt()),
                order.status().name(),
                order.executedQuantity().value(),
                order.executedAmount().amount(),
                order.version(),
                order.id()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO orders(
                        id, market, order_type, side, quantity, price_krw, minimum_profit_price_krw,
                        strategy_id, idempotency_key, created_at, status, executed_quantity, executed_amount_krw, version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    order.id(),
                    order.market().value(),
                    order.orderType().name(),
                    order.side().name(),
                    order.quantity(),
                    order.price().amount(),
                    order.minimumProfitPrice() == null ? null : order.minimumProfitPrice().amount(),
                    order.strategyId().value(),
                    order.idempotencyKey().value(),
                    Timestamp.from(order.createdAt()),
                    order.status().name(),
                    order.executedQuantity().value(),
                    order.executedAmount().amount(),
                    order.version()
            );
        }
        return order;
    }

    @Override
    public List<Order> findAll() {
        return jdbcTemplate.query("SELECT * FROM orders ORDER BY created_at ASC", this::mapOrder);
    }

    private Order mapOrder(ResultSet rs, int rowNum) throws SQLException {
        String minProfitRaw = rs.getString("minimum_profit_price_krw");
        Money minimumProfitPrice = minProfitRaw == null ? null : Money.krw(rs.getBigDecimal("minimum_profit_price_krw"));
        return Order.rehydrate(
                OrderId.of(rs.getString("id")),
                Market.of(rs.getString("market")),
                OrderType.valueOf(rs.getString("order_type")),
                Side.valueOf(rs.getString("side")),
                Quantity.of(rs.getBigDecimal("quantity")),
                Price.of(rs.getBigDecimal("price_krw"), Market.of(rs.getString("market")).quote()),
                minimumProfitPrice,
                StrategyId.of(rs.getString("strategy_id")),
                IdempotencyKey.of(rs.getString("idempotency_key")),
                toInstant(rs.getTimestamp("created_at")),
                OrderStatus.valueOf(rs.getString("status")),
                Quantity.of(rs.getBigDecimal("executed_quantity")),
                Money.krw(rs.getBigDecimal("executed_amount_krw")),
                rs.getLong("version")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }
}
