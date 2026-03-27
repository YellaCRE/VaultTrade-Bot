package com.vaulttradebot.adapter.in;

import com.vaulttradebot.application.idempotency.IdempotencyConflictException;
import com.vaulttradebot.config.ApiTimeSupport;
import com.vaulttradebot.domain.ops.KillSwitchActiveException;
import com.vaulttradebot.domain.resilience.CircuitBreakerBypassException;
import com.vaulttradebot.domain.resilience.CircuitBreakerOpenException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.IDEMPOTENCY_CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(
            CircuitBreakerOpenException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.CIRCUIT_BREAKER_OPEN,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(KillSwitchActiveException.class)
    public ResponseEntity<ErrorResponse> handleKillSwitchActive(
            KillSwitchActiveException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.KILL_SWITCH_ACTIVE,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(CircuitBreakerBypassException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerBypass(
            CircuitBreakerBypassException exception,
            HttpServletRequest request
    ) {
        RuntimeException cause = exception.getCause();
        if (cause instanceof IllegalArgumentException illegalArgumentException) {
            return handleIllegalArgument(illegalArgumentException, request);
        }
        return buildResponse(
                ErrorCode.UPSTREAM_REQUEST_FAILED,
                resolveMessage(cause),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "request validation failed";
        }

        return buildResponse(
                ErrorCode.VALIDATION_ERROR,
                message,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause instanceof IllegalArgumentException illegalArgumentException) {
            return handleIllegalArgument(illegalArgumentException, request);
        }

        return buildResponse(
                ErrorCode.MALFORMED_REQUEST,
                "request body is missing or malformed",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.INVALID_REQUEST,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                ErrorCode.INTERNAL_ERROR,
                resolveMessage(exception),
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            ErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        var status = code.status();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        ApiTimeSupport.toApiTime(Instant.now()),
                        status.value(),
                        status.getReasonPhrase(),
                        code,
                        message,
                        request.getRequestURI()
                ));
    }

    private String formatFieldError(FieldError error) {
        String defaultMessage = error.getDefaultMessage();
        if (defaultMessage == null || defaultMessage.isBlank()) {
            return error.getField() + " is invalid";
        }
        return error.getField() + ": " + defaultMessage;
    }

    private String resolveMessage(Throwable throwable) {
        if (throwable == null) {
            return "unexpected error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
