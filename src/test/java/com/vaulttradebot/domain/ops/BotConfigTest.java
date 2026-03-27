package com.vaulttradebot.domain.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BotConfigTest {

    @Test
    void acceptsKrwQuotedMarketSymbol() {
        // Verifies the config accepts the supported KRW-quoted market symbol format.
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
        // Verifies non-KRW quote currencies are rejected by config validation.
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
        // Verifies malformed market symbols are rejected when they do not follow QUOTE-BASE format.
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
