package com.vaulttradebot.application.port.in;

import com.vaulttradebot.domain.execution.Order;
import com.vaulttradebot.application.query.MetricsSnapshot;
import com.vaulttradebot.application.query.PortfolioSnapshot;
import java.util.List;

public interface BotQueryUseCase {
    List<Order> listOrders();

    PortfolioSnapshot getPortfolioSnapshot();

    MetricsSnapshot getMetrics();
}
