package com.vaulttradebot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vaulttradebot.application.port.out.LoadMarketSnapshotPort;
import com.vaulttradebot.domain.shared.MarketSnapshot;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnalyzeMarketServiceTest {
    @Test
    void analyzeReturnsSnapshotFromPort() {
        LoadMarketSnapshotPort port = Mockito.mock(LoadMarketSnapshotPort.class);
        AnalyzeMarketService service = new AnalyzeMarketService(port);
        MarketSnapshot snapshot = new MarketSnapshot("BTC", new BigDecimal("123.45"));
        when(port.loadSnapshot("BTC")).thenReturn(snapshot);

        MarketSnapshot result = service.analyze("BTC");

        assertThat(result).isEqualTo(snapshot);
    }
}
