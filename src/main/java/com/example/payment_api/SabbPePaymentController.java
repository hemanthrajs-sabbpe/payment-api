package com.example.payment_api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sabbpe/payments")
public class SabbPePaymentController {

    private final SabbPePaymentService sabbPePaymentService;

    public SabbPePaymentController(
            SabbPePaymentService sabbPePaymentService) {
        this.sabbPePaymentService = sabbPePaymentService;
    }

    /**
     * Standard PBL generation endpoint for standalone payments or individual split allocations.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPayment(
            @Valid @RequestBody SabbPePaymentRequest request) {

        Map<String, Object> response =
                sabbPePaymentService.preparePayment(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Explicit regenerate endpoint for standalone or split-allocation PBL.
     * Enforces regeneration safety rules:
     * - Disallows regeneration if an existing payment is already SUCCESS or REFUNDED.
     * - Generates a fresh unique merchant_order_ref and requests a fresh SabbPe token.
     * - Preserves prior payment record history and creates a fresh native PBL session.
     */
    @PostMapping("/regenerate")
    public ResponseEntity<Map<String, Object>> regeneratePayment(
            @Valid @RequestBody SabbPePaymentRequest request) {

        Map<String, Object> response =
                sabbPePaymentService.preparePayment(request);

        return ResponseEntity.ok(response);
    }
}
