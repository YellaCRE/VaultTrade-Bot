package com.vaulttradebot.adapter.out.upbit;

import com.vaulttradebot.adapter.out.upbit.dto.UpbitMinuteCandleResponse;
import com.vaulttradebot.adapter.out.upbit.dto.UpbitTickerResponse;
import com.vaulttradebot.config.VaultMarketDataProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UpbitQuotationClient {
    private static final DateTimeFormatter UPBIT_TO_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RestClient restClient;

    public UpbitQuotationClient(RestClient.Builder restClientBuilder, VaultMarketDataProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getUpbit().getBaseUrl())
                .build();
    }

    public UpbitTickerResponse getTicker(String market) {
        try {
            UpbitTickerResponse[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/ticker")
                            .queryParam("markets", market)
                            .build())
                    .retrieve()
                    .body(UpbitTickerResponse[].class);
            if (body == null || body.length != 1 || body[0] == null) {
                throw new IllegalStateException("upbit ticker response was empty for market " + market);
            }
            return body[0];
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch upbit ticker for market " + market, ex);
        }
    }

    public List<UpbitMinuteCandleResponse> getMinuteCandles(String market, int unit, int count, Instant to) {
        try {
            UpbitMinuteCandleResponse[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/candles/minutes/{unit}")
                            .queryParam("market", market)
                            .queryParam("count", count)
                            .queryParam("to", UPBIT_TO_FORMAT.format(to.atOffset(ZoneOffset.UTC)))
                            .build(unit))
                    .retrieve()
                    .body(UpbitMinuteCandleResponse[].class);
            if (body == null) {
                return List.of();
            }
            return List.of(body);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "failed to fetch upbit minute candles for market %s and unit %d".formatted(market, unit),
                    ex
            );
        }
    }
}
