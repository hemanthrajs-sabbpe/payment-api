package com.example.payment_api;

/**
 * Indicates that a configured payment provider request failed or returned
 * an unusable response. The original cause is retained for server-side
 * diagnostics but is not sent to the API client.
 */
public class SabbPeIntegrationException extends RuntimeException {

    public SabbPeIntegrationException(String message) {
        super(message);
    }

    public SabbPeIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
