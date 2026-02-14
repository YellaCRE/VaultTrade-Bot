package com.vaulttradebot.domain.risk;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Side;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.vaulttradebot.domain.risk.event.RiskOrderRequest;
import com.vaulttradebot.domain.risk.snapshot.RiskAccountSnapshot;
import com.vaulttradebot.domain.risk.snapshot.RiskMarketSnapshot;
import com.vaulttradebot.domain.risk.vo.RiskContext;
import org.junit.jupiter.api.Test;

class RiskContextTest {

    @Test
    void rejectsAccountMismatch() {
        // Verifies context validation rejects request/account identity mismatches.
        RiskOrderRequest request = new RiskOrderRequest(
                "acct-a",
                Market.of("KRW-BTC"),
                Side.BUY,
                Money.of(new BigDecimal("100"), Asset.krw()),
                new BigDecimal("1"),
                Instant.parse("2026-02-14T00:00:00Z")
        );

        RiskAccountSnapshot account = new RiskAccountSnapshot(
                "acct-b",
                new BigDecimal("1000000"),
                new BigDecimal("900000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );

        RiskMarketSnapshot market = new RiskMarketSnapshot(
                "KRW-BTC",
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("100"),
                BigDecimal.ZERO,
                Instant.parse("2026-02-14T00:00:00Z"),
                Duration.ofSeconds(5)
        );

        RiskPolicy policy = new RiskPolicy(
                new BigDecimal("5000"),
                new BigDecimal("100000"),
                new BigDecimal("0.30"),
                new BigDecimal("0.03"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                ZoneId.of("Asia/Seoul"),
                new BigDecimal("0.0005"),
                new BigDecimal("0.0020")
        );

        Clock clock = Clock.fixed(Instant.parse("2026-02-14T00:00:00Z"), ZoneOffset.UTC);

        assertThatThrownBy(() -> new RiskContext(request, account, market, policy, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account id mismatch");
    }
}
