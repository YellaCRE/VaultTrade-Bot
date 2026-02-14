package com.vaulttradebot.domain.portfolio;

import com.vaulttradebot.domain.common.vo.Asset;
import com.vaulttradebot.domain.common.vo.Market;
import com.vaulttradebot.domain.common.vo.Money;
import com.vaulttradebot.domain.common.vo.Price;
import com.vaulttradebot.domain.common.vo.Quantity;
import com.vaulttradebot.domain.portfolio.event.BalanceAdjusted;
import com.vaulttradebot.domain.portfolio.event.BuyFilled;
import com.vaulttradebot.domain.portfolio.event.FeeCharged;
import com.vaulttradebot.domain.portfolio.event.SellFilled;
import com.vaulttradebot.domain.portfolio.snapshot.PositionSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public class Position {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int PRICE_SCALE = 8;

    private final Market market;
    private Quantity quantity;
    private Price avgPrice;
    private BigDecimal realizedPnL;
    private long version;
    private Instant updatedAt;

    private Position(
            Market market,
            Quantity quantity,
            Price avgPrice,
            BigDecimal realizedPnL,
            long version,
            Instant updatedAt
    ) {
        requireNotNull(market, "market");
        requireNotNull(quantity, "quantity");
        requireNotNull(avgPrice, "avgPrice");
        requireNotNull(realizedPnL, "realizedPnL");
        requireNotNull(updatedAt, "updatedAt");

        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }

        this.market = market;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.realizedPnL = realizedPnL.setScale(0, RoundingMode.HALF_UP);
        this.version = version;
        this.updatedAt = updatedAt;
        assertInvariants();
    }

    /** Creates a new empty position for a specific market. */
    public static Position open(Market market, Instant occurredAt) {
        return new Position(
                market,
                Quantity.of(ZERO),
                Price.of(ZERO, Asset.krw()),
                ZERO,
                0L,
                occurredAt == null ? Instant.now() : occurredAt
        );
    }

    /** Rebuilds a position aggregate from a persisted snapshot. */
    public static Position restore(PositionSnapshot snapshot) {
        requireNotNull(snapshot, "snapshot");
        return new Position(
                Market.of(snapshot.marketSymbol()),
                Quantity.of(snapshot.quantity()),
                Price.of(snapshot.avgPrice(), Asset.krw()),
                snapshot.realizedPnL(),
                snapshot.version(),
                snapshot.updatedAt()
        );
    }

    /** Applies a buy fill and updates quantity/average price including fee. */
    public void apply(BuyFilled event) {
        verifyEvent(event.market());
        BigDecimal previousCost = totalInvestment();
        BigDecimal executionCost = event.price().amount().multiply(event.quantity().value());
        BigDecimal nextQuantity = quantity.value().add(event.quantity().value());
        BigDecimal totalCostWithFee = previousCost.add(executionCost).add(event.fee().amount());

        this.quantity = Quantity.of(nextQuantity);
        this.avgPrice = Price.of(
                totalCostWithFee.divide(nextQuantity, PRICE_SCALE, RoundingMode.HALF_UP),
                Asset.krw()
        );
        touch(event.occurredAt());
    }

    /** Applies a sell fill and accumulates realized PnL without changing avg price. */
    public void apply(SellFilled event) {
        verifyEvent(event.market());
        if (event.quantity().value().compareTo(quantity.value()) > 0) {
            throw new IllegalStateException("cannot sell more than current quantity");
        }

        BigDecimal pnl = event.price()
                .amount()
                .subtract(avgPrice.value())
                .multiply(event.quantity().value())
                .subtract(event.fee().amount());
        this.realizedPnL = this.realizedPnL.add(pnl).setScale(0, RoundingMode.HALF_UP);
        this.quantity = Quantity.of(quantity.value().subtract(event.quantity().value()));
        if (quantity.value().signum() == 0) {
            this.avgPrice = Price.of(ZERO, Asset.krw());
        }
        touch(event.occurredAt());
    }

    /** Applies an extra fee adjustment to avg price or realized PnL based on holding state. */
    public void apply(FeeCharged event) {
        verifyEvent(event.market());
        if (quantity.value().signum() == 0) {
            this.realizedPnL = realizedPnL.subtract(event.fee().amount()).setScale(0, RoundingMode.HALF_UP);
        } else {
            BigDecimal nextAvg = totalInvestment()
                    .add(event.fee().amount())
                    .divide(quantity.value(), PRICE_SCALE, RoundingMode.HALF_UP);
            this.avgPrice = Price.of(nextAvg, Asset.krw());
        }
        touch(event.occurredAt());
    }

    /** Applies an external balance sync correction from exchange state. */
    public void apply(BalanceAdjusted event) {
        verifyEvent(event.market());
        this.quantity = event.quantity();
        this.avgPrice = event.avgPrice();
        touch(event.occurredAt());
    }

    /** Returns invested capital based on current average price and quantity. */
    public BigDecimal totalInvestment() {
        return avgPrice.value().multiply(quantity.value());
    }

    /** Returns mark-to-market value at the given current price. */
    public BigDecimal marketValue(Money currentPrice) {
        requireNotNull(currentPrice, "currentPrice");
        return currentPrice.amount().multiply(quantity.value());
    }

    /** Returns unrealized PnL from current price versus average entry price. */
    public BigDecimal unrealizedPnL(Money currentPrice) {
        requireNotNull(currentPrice, "currentPrice");
        return currentPrice.amount()
                .subtract(avgPrice.value())
                .multiply(quantity.value())
                .setScale(0, RoundingMode.HALF_UP);
    }

    /** Returns total PnL as realized plus unrealized profit/loss. */
    public BigDecimal totalPnL(Money currentPrice) {
        return realizedPnL.add(unrealizedPnL(currentPrice)).setScale(0, RoundingMode.HALF_UP);
    }

    /** Creates a persistable snapshot of the current aggregate state. */
    public PositionSnapshot toSnapshot() {
        return new PositionSnapshot(
                market.value(),
                quantity.value(),
                avgPrice.value(),
                realizedPnL,
                version,
                updatedAt
        );
    }

    /** Returns the market identity of this position aggregate. */
    public Market market() {
        return market;
    }

    /** Returns current holding quantity. */
    public BigDecimal quantity() {
        return quantity.value();
    }

    /** Returns average entry price as money value. */
    public Money averageEntryPrice() {
        return Money.of(avgPrice.value(), avgPrice.unitCurrency());
    }

    /** Returns average entry price as price value object. */
    public Price avgPrice() {
        return avgPrice;
    }

    /** Returns current quantity as quantity value object. */
    public Quantity qty() {
        return quantity;
    }

    /** Returns cumulative realized profit/loss. */
    public BigDecimal realizedPnL() {
        return realizedPnL;
    }

    /** Returns optimistic-lock version of this aggregate. */
    public long version() {
        return version;
    }

    /** Returns last update timestamp of this aggregate. */
    public Instant updatedAt() {
        return updatedAt;
    }

    private void verifyEvent(Market eventMarket) {
        requireNotNull(eventMarket, "event market");
        if (!Objects.equals(this.market, eventMarket)) {
            throw new IllegalArgumentException("event market does not match position market");
        }
    }

    private void touch(Instant occurredAt) {
        this.version++;
        this.updatedAt = occurredAt == null ? Instant.now() : occurredAt;
        assertInvariants();
    }

    private void assertInvariants() {
        if (quantity.value().signum() < 0) {
            throw new IllegalStateException("quantity must be >= 0");
        }
        if (quantity.value().signum() == 0 && avgPrice.value().signum() != 0) {
            throw new IllegalStateException("avgPrice must be 0 when quantity is 0");
        }
        if (avgPrice.value().signum() < 0) {
            throw new IllegalStateException("avgPrice must be >= 0");
        }
    }

    private static void requireNotNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
