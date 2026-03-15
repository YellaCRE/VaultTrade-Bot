package com.vaulttradebot.adapter.in;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaulttradebot.application.idempotency.IdempotencyConflictException;
import com.vaulttradebot.application.port.in.BotConfigUseCase;
import com.vaulttradebot.application.port.in.BotControlUseCase;
import com.vaulttradebot.application.port.in.RunTradingCycleUseCase;
import com.vaulttradebot.domain.resilience.CircuitBreakerOpenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {BotController.class, ConfigController.class})
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BotControlUseCase botControlUseCase;

    @MockBean
    private RunTradingCycleUseCase runTradingCycleUseCase;

    @MockBean
    private BotConfigUseCase botConfigUseCase;

    @Test
    void mapsIdempotencyConflictToConflictResponse() throws Exception {
        // Reused idempotency keys with different payloads should surface as a 409 API error.
        when(runTradingCycleUseCase.runCycle())
                .thenThrow(new IdempotencyConflictException("same idempotency key was used with different payload"));

        mockMvc.perform(post("/api/bot/cycle"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("same idempotency key was used with different payload"))
                .andExpect(jsonPath("$.path").value("/api/bot/cycle"));
    }

    @Test
    void mapsCircuitBreakerOpenToServiceUnavailable() throws Exception {
        // Open breaker state means the upstream path is temporarily unavailable to callers.
        when(runTradingCycleUseCase.runCycle())
                .thenThrow(new CircuitBreakerOpenException("upbit-trading"));

        mockMvc.perform(post("/api/bot/cycle"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CIRCUIT_BREAKER_OPEN"))
                .andExpect(jsonPath("$.message").value("circuit breaker is open: upbit-trading"));
    }

    @Test
    void mapsInvalidRequestToBadRequest() throws Exception {
        // Domain-style argument validation failures should be normalized into a 400 response.
        when(botConfigUseCase.updateConfig(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("marketSymbol must not be blank"));

        mockMvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "marketSymbol": "",
                                  "paperTrading": true,
                                  "initialCashKrw": 1000000,
                                  "maxOrderKrw": 100000,
                                  "maxExposureRatio": 0.30,
                                  "maxDailyLossRatio": 0.03,
                                  "cooldownSeconds": 30,
                                  "buyThresholdPrice": 50000000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("marketSymbol must not be blank"))
                .andExpect(jsonPath("$.path").value("/api/config"));
    }

    @Test
    void mapsMalformedJsonToBadRequest() throws Exception {
        // Broken JSON should fail before controller logic and still return the shared error shape.
        String malformedJson = "{\"marketSymbol\":\"KRW-BTC\"";

        mockMvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("request body is missing or malformed"))
                .andExpect(jsonPath("$.path").value("/api/config"));
    }
}
