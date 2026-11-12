package com.example.payment_api;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // PAYMENT PROVIDER / UPSTREAM ERRORS
    @ExceptionHandler(SabbPeIntegrationException.class)
    public ResponseEntity<Map<String, String>> handleSabbPeIntegrationFailure(
            SabbPeIntegrationException exception) {

        Integer upstreamStatus = findProviderStatusCode(exception);

        if (upstreamStatus == null) {
            log.warn("SabbPe integration request failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        } else {
            log.warn("SabbPe integration request failed: exceptionType={}, upstreamStatus={}",
                    exception.getClass().getSimpleName(), upstreamStatus);
        }

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "message",
                        "SabbPe UAT request failed. Verify the configured URL and merchant settings, then check the backend log."
                ));
    }

    // PAYMENT NOT FOUND
    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handlePaymentNotFound(
            PaymentNotFoundException exception) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    // INVALID CUSTOM CALLBACK SECRET
    @ExceptionHandler(InvalidCallbackSecretException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleInvalidCallbackSecret(
            InvalidCallbackSecretException exception) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    // VALIDATION ERRORS
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationErrors(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return errors;
    }

    // INVALID REQUEST OR BUSINESS VALIDATION
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(
            IllegalArgumentException exception) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    // PESSIMISTIC LOCKING / DATABASE CONCURRENCY CONFLICTS & COMMIT-TIME DEADLOCKS
    @ExceptionHandler({
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.transaction.TransactionException.class,
            jakarta.persistence.PersistenceException.class
    })
    public ResponseEntity<Map<String, String>> handleConcurrencyLockingFailure(
            Exception exception) {

        if (isLockOrConcurrencyConflict(exception)) {
            log.warn("Database lock timeout or concurrency conflict encountered: exceptionType={}",
                    exception.getClass().getSimpleName());

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "The requested resource is currently locked by another operation. Please retry in a few moments."
                    ));
        }

        // Delegate to existing catch-all handler for unrelated transaction or persistence failures
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(handleUnexpectedException(exception));
    }

    private boolean isLockOrConcurrencyConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.springframework.dao.PessimisticLockingFailureException
                    || current instanceof jakarta.persistence.PessimisticLockException
                    || current instanceof jakarta.persistence.LockTimeoutException
                    || current instanceof org.hibernate.PessimisticLockException
                    || current instanceof org.hibernate.exception.LockTimeoutException
                    || current instanceof org.hibernate.exception.LockAcquisitionException) {
                return true;
            }
            if (current instanceof java.sql.SQLException sqlEx) {
                // Inspect primary exception and chained SQLExceptions via getNextException()
                java.sql.SQLException chain = sqlEx;
                while (chain != null) {
                    int errorCode = chain.getErrorCode();
                    String sqlState = chain.getSQLState();
                    if (errorCode == 1205 || errorCode == 1213 || "40001".equals(sqlState)) {
                        return true;
                    }
                    chain = chain.getNextException();
                }
            }
            current = current.getCause();
        }
        return false;
    }

    // EXPLICIT RESPONSE STATUS EXCEPTIONS (403, 404, 410, etc.)
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException exception) {

        String message = exception.getReason() != null
                ? exception.getReason()
                : exception.getStatusCode().toString();

        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", message));
    }

    // UNSUPPORTED HTTP METHOD
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Map<String, String> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {

        return Map.of(
                "message",
                "HTTP method not supported for this endpoint"
        );
    }

    // UNEXPECTED SERVER ERRORS
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleUnexpectedException(
            Exception exception) {

        Integer upstreamStatus = findProviderStatusCode(exception);

        if (upstreamStatus != null) {
            log.error("Unhandled upstream HTTP error in API flow: exceptionType={}, upstreamStatus={}",
                    exception.getClass().getName(), upstreamStatus);
        } else {
            log.error("Unhandled API exception: exceptionType={}",
                    exception.getClass().getName(), exception);
        }

        return Map.of(
                "message",
                "An unexpected server error occurred. Check the backend log for the exception type."
        );
    }

    // FIND UPSTREAM PROVIDER HTTP STATUS
    private Integer findProviderStatusCode(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof RestClientResponseException providerException) {
                return providerException.getStatusCode().value();
            }

            current = current.getCause();
        }

        return null;
    }
}