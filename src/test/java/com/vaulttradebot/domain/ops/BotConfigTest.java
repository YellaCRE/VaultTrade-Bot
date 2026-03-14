package com.vaulttradebot.domain.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BotConfigTest {

    @Test
    void acceptsKrwQuotedMarketSymbol() {
        BotConfig config = new BotConfig(
                "KRW-BTC",
                true,
                new BigDecimal("1000000"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                30L,
                new BigDecimal("50000000")
        );

        assertThat(config.marketSymbol()).isEqualTo("KRW-BTC");
    }

    @Test
    void rejectsUnsupportedQuoteCurrency() {
        assertThatThrownBy(() -> new BotConfig(
                "USDT-BTC",
                true,
                new BigDecimal("1000000"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                30L,
                new BigDecimal("50000000")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quote currency must be KRW");
    }

    @Test
    void rejectsInvalidMarketSymbolFormat() {
        assertThatThrownBy(() -> new BotConfig(
                "BTCKRW",
                true,
                new BigDecimal("1000000"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                30L,
                new BigDecimal("50000000")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QUOTE-BASE");
    }
}
