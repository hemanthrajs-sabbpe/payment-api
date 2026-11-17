package com.example.payment_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("SecurityException should return HTTP 403 Forbidden with exact message")
    void testHandleSecurityException() {
        SecurityException ex = new SecurityException("You are not a member of this organization");
        Map<String, String> response = handler.handleSecurityException(ex);

        assertNotNull(response);
        assertEquals("You are not a member of this organization", response.get("message"));
    }

    @Test
    @DisplayName("IllegalArgumentException should return HTTP 400 Bad Request with exact message")
    void testHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Organization ID is required for a business split");
        Map<String, String> response = handler.handleIllegalArgument(ex);

        assertNotNull(response);
        assertEquals("Organization ID is required for a business split", response.get("message"));
    }
}
