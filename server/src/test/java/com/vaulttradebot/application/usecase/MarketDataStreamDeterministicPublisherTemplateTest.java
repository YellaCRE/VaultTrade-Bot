package com.vaulttradebot.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaulttradebot.domain.common.vo.Market;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Template tests for a future subscribe-style MarketDataPort.
 * This file is intentionally self-contained so it can be copied into stream adapter tests.
 */
class MarketDataStreamDeterministicPublisherTemplateTest {

    @Test
    void deliversScriptedEventsInDeterministicOrder() {
        DeterministicTickPublisher publisher = new DeterministicTickPublisher();
        MutableTimeProvider time = new MutableTimeProvider(Instant.parse("2026-02-15T10:00:00Z"));
        StreamDecisionProbe probe = new StreamDecisionProbe(time, Duration.ofSeconds(2));

        publisher.subscribe(Market.of("KRW-BTC"), probe);
        publisher.emit(tick(1, "2026-02-15T09:59:59Z", "50000000", "49990000", "50010000"));
        publisher.emit(tick(2, "2026-02-15T10:00:00Z", "50010000", "50000000", "50020000"));

        assertThat(probe.acceptedSequences()).containsExactly(1L, 2L);
        assertThat(probe.rejections()).isEmpty();
    }

    @Test
    void rejectsStaleTickWithControlledTime() {
        DeterministicTickPublisher publisher = new DeterministicTickPublisher();
        MutableTimeProvider time = new MutableTimeProvider(Instant.parse("2026-02-15T10:00:00Z"));
        StreamDecisionProbe probe = new StreamDecisionProbe(time, Duration.ofSeconds(2));

        publisher.subscribe(Market.of("KRW-BTC"), probe);
        publisher.emit(tick(1, "2026-02-15T09:59:59Z", "50000000", "49990000", "50010000"));
        time.advance(Duration.ofSeconds(3));
        publisher.emit(tick(2, "2026-02-15T10:00:00Z", "50020000", "50010000", "50030000"));

        assertThat(probe.acceptedSequences()).containsExactly(1L);
        assertThat(probe.rejections()).containsExactly("STALE_TICK");
    }

    @Test
    void ignoresOutOfOrderTickBySequenceGuard() {
        DeterministicTickPublisher publisher = new DeterministicTickPublisher();
        MutableTimeProvider time = new MutableTimeProvider(Instant.parse("2026-02-15T10:00:00Z"));
        StreamDecisionProbe probe = new StreamDecisionProbe(time, Duration.ofSeconds(10));

        publisher.subscribe(Market.of("KRW-BTC"), probe);
        publisher.emit(tick(5, "2026-02-15T10:00:00Z", "50000000", "49990000", "50010000"));
        publisher.emit(tick(4, "2026-02-15T09:59:59Z", "49980000", "49970000", "49990000"));

        assertThat(probe.acceptedSequences()).containsExactly(5L);
        assertThat(probe.rejections()).containsExactly("OUT_OF_ORDER");
    }

    @Test
    void supportsDisconnectAndReconnectScenario() {
        DeterministicTickPublisher publisher = new DeterministicTickPublisher();
        MutableTimeProvider time = new MutableTimeProvider(Instant.parse("2026-02-15T10:00:00Z"));
        StreamDecisionProbe first = new StreamDecisionProbe(time, Duration.ofSeconds(10));
        StreamDecisionProbe second = new StreamDecisionProbe(time, Duration.ofSeconds(10));

        publisher.subscribe(Market.of("KRW-BTC"), first);
        publisher.emit(tick(1, "2026-02-15T10:00:00Z", "50000000", "49990000", "50010000"));
        publisher.error(new IllegalStateException("stream disconnected"));

        publisher.subscribe(Market.of("KRW-BTC"), second);
        publisher.emit(tick(2, "2026-02-15T10:00:01Z", "50010000", "50000000", "50020000"));

        assertThat(first.acceptedSequences()).containsExactly(1L);
        assertThat(first.errors()).containsExactly("stream disconnected");
        assertThat(second.acceptedSequences()).containsExactly(2L);
    }

    @Test
    void appliesDeterministicBackpressurePolicyDropOldest() {
        DeterministicTickPublisher publisher = new DeterministicTickPublisher();
        BoundedQueueSubscriber subscriber = new BoundedQueueSubscriber(2);

        publisher.subscribe(Market.of("KRW-BTC"), subscriber);
        publisher.emit(tick(1, "2026-02-15T10:00:00Z", "50000000", "49990000", "50010000"));
        publisher.emit(tick(2, "2026-02-15T10:00:01Z", "50010000", "50000000", "50020000"));
        publisher.emit(tick(3, "2026-02-15T10:00:02Z", "50020000", "50010000", "50030000"));

        // With drop-oldest policy and capacity=2, only the newest two events remain.
        assertThat(subscriber.bufferedSequences()).containsExactly(2L, 3L);
    }

    private static TickEvent tick(long sequence, String exchangeTime, String last, String bid, String ask) {
        return new TickEvent(
                sequence,
                Instant.parse(exchangeTime),
                BigDecimal.valueOf(Long.parseLong(last)),
                BigDecimal.valueOf(Long.parseLong(bid)),
                BigDecimal.valueOf(Long.parseLong(ask))
        );
    }

    private interface TickSubscriber {
        void onTick(TickEvent event);

        void onError(Throwable error);
    }

    private static final class DeterministicTickPublisher {
        private final List<TickSubscriber> subscribers = new CopyOnWriteArrayList<>();

        void subscribe(Market market, TickSubscriber subscriber) {
            // Market parameter is included to mirror a future subscribe(port, symbol) signature.
            if (market == null || subscriber == null) {
                throw new IllegalArgumentException("market and subscriber must not be null");
            }
            subscribers.add(subscriber);
        }

        void emit(TickEvent event) {
            for (TickSubscriber subscriber : subscribers) {
                subscriber.onTick(event);
            }
        }

        void error(Throwable error) {
            for (TickSubscriber subscriber : subscribers) {
                subscriber.onError(error);
            }
            subscribers.clear();
        }
    }

    private record TickEvent(
            long sequence,
            Instant exchangeTime,
            BigDecimal lastPrice,
            BigDecimal bestBid,
            BigDecimal bestAsk
    ) {
    }

    private static final class MutableTimeProvider {
        private final AtomicReference<Instant> now;

        MutableTimeProvider(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        Instant now() {
            return now.get();
        }

        void advance(Duration step) {
            now.updateAndGet(current -> current.plus(step));
        }
    }

    private static final class StreamDecisionProbe implements TickSubscriber {
        private final MutableTimeProvider timeProvider;
        private final Duration staleAfter;
        private final List<Long> acceptedSequences = new ArrayList<>();
        private final List<String> rejections = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private long lastSequence = -1L;

        StreamDecisionProbe(MutableTimeProvider timeProvider, Duration staleAfter) {
            this.timeProvider = timeProvider;
            this.staleAfter = staleAfter;
        }

        @Override
        public void onTick(TickEvent event) {
            // Template guard #1: reject out-of-order updates before decision logic.
            if (event.sequence() <= lastSequence) {
                rejections.add("OUT_OF_ORDER");
                return;
            }
            // Template guard #2: reject stale ticks using controlled test time.
            if (event.exchangeTime().plus(staleAfter).isBefore(timeProvider.now())) {
                rejections.add("STALE_TICK");
                return;
            }
            acceptedSequences.add(event.sequence());
            lastSequence = event.sequence();
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error.getMessage());
        }

        List<Long> acceptedSequences() {
            return acceptedSequences;
        }

        List<String> rejections() {
            return rejections;
        }

        List<String> errors() {
            return errors;
        }
    }

    private static final class BoundedQueueSubscriber implements TickSubscriber {
        private final int capacity;
        private final ArrayDeque<Long> queue = new ArrayDeque<>();

        BoundedQueueSubscriber(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public void onTick(TickEvent event) {
            // Example backpressure policy for tests: keep newest events and drop oldest.
            if (queue.size() == capacity) {
                queue.removeFirst();
            }
            queue.addLast(event.sequence());
        }

        @Override
        public void onError(Throwable error) {
            // No-op for template.
        }

        List<Long> bufferedSequences() {
            return List.copyOf(queue);
        }
    }
}
