package com.vaulttradebot.application.port.in;

import com.vaulttradebot.domain.common.Order;
import com.vaulttradebot.domain.ops.MetricsSnapshot;
import com.vaulttradebot.domain.portfolio.PortfolioSnapshot;
import java.util.List;

public interface BotQueryUseCase {
    List<Order> listOrders();

    PortfolioSnapshot getPortfolioSnapshot();

    MetricsSnapshot getMetrics();
}
