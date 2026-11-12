package com.example.payment_api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sabbpe/gateway")
public class SabbPeGatewayPaymentController {

    private static final Logger log =
            LoggerFactory.getLogger(SabbPeGatewayPaymentController.class);

    private final SabbPeGatewayPaymentService gatewayPaymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitService paymentSplitService;
    private final PaymentSplitAllocationRepository allocationRepository;

    public SabbPeGatewayPaymentController(
            SabbPeGatewayPaymentService gatewayPaymentService,
            PaymentRepository paymentRepository,
            PaymentSplitService paymentSplitService,
            PaymentSplitAllocationRepository allocationRepository) {
        this.gatewayPaymentService = gatewayPaymentService;
        this.paymentRepository = paymentRepository;
        this.paymentSplitService = paymentSplitService;
        this.allocationRepository = allocationRepository;
    }

    /**
     * Merchant / frontend initiates a SabbPe Gateway payment to get the payment_url for QR code display.
     */
    /**
     * Initiate a SabbPe Gateway QR payment for a specific split allocation.
     * Generates a dynamic QR code payment URL linked to this allocation.
     */
    @PostMapping("/splits/{splitId}/allocations/{allocationId}/pay")
    public ResponseEntity<SabbPeGatewayPaymentResponse> createGatewayPaymentForAllocation(
            @PathVariable Long splitId,
            @PathVariable Long allocationId,
            @RequestBody(required = false) Map<String, String> contactDetails) {

        String email = contactDetails != null ? contactDetails.get("email") : null;
        String phone = contactDetails != null ? contactDetails.get("phone") : null;

        log.info("Request to create Gateway payment for Split #{} Allocation #{}", splitId, allocationId);
        SabbPeGatewayPaymentResponse response = gatewayPaymentService.initiateGatewayPaymentForAllocation(
                splitId, allocationId, email, phone);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

        @PostMapping("/payments")
    public ResponseEntity<SabbPeGatewayPaymentResponse> createGatewayPayment(
            @Valid @RequestBody SabbPeGatewayPaymentRequest request) {

        log.info("Received request to create SabbPe Gateway payment for amount: {}", request.getAmount());
        SabbPeGatewayPaymentResponse response = gatewayPaymentService.initiateGatewayPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get payment status by database payment ID.
     */
    @GetMapping("/payments/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable Long id) {
        return paymentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Get payment status by merchant order reference.
     */
    @GetMapping("/payments/by-order-ref/{merchantOrderRef}")
    public ResponseEntity<Payment> getPaymentByOrderRef(@PathVariable String merchantOrderRef) {
        return paymentRepository.findByMerchantOrderRef(merchantOrderRef)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Authoritative server-to-server callback from SabbPe Gateway.
     * Idempotent: safe against retries and ensures final SUCCESS is preserved.
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> handleGatewayCallback(
            @RequestBody SabbPeGatewayCallbackRequest callbackRequest) {

        log.info("Received SabbPe Gateway callback for merchant_order_ref: {}, status: {}",
                callbackRequest != null ? callbackRequest.getMerchantOrderRef() : "null",
                callbackRequest != null ? callbackRequest.getStatus() : "null");

        if (callbackRequest == null || callbackRequest.getMerchantOrderRef() == null
                || callbackRequest.getMerchantOrderRef().trim().isEmpty()) {
            log.warn("Ignored malformed SabbPe Gateway callback: missing merchant_order_ref");
            return ResponseEntity.badRequest().body(Map.of("message", "merchant_order_ref is required"));
        }

        String merchantOrderRef = callbackRequest.getMerchantOrderRef().trim();
        Optional<Payment> paymentOpt = paymentRepository.findByMerchantOrderRef(merchantOrderRef);

        if (paymentOpt.isEmpty()) {
            log.warn("SabbPe Gateway callback received for unknown merchantOrderRef: {}", merchantOrderRef);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Payment record not found"));
        }

        Payment payment = paymentOpt.get();

        // Amount validation safeguard
        if (callbackRequest.getAmount() != null) {
            double diff = Math.abs(payment.getAmount() - callbackRequest.getAmount());
            if (diff > 0.01) {
                log.error("Amount mismatch in Gateway callback for {}. Expected: {}, Received: {}",
                        merchantOrderRef, payment.getAmount(), callbackRequest.getAmount());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Amount mismatch"));
            }
        }

        // Preserve final SUCCESS state from being downgraded
        String currentStatus = payment.getStatus();
        if ("SUCCESS".equalsIgnoreCase(currentStatus)) {
            log.info("Payment #{} already in final SUCCESS status. Callback acknowledged without state downgrade.", payment.getId());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Already confirmed"));
        }

        // Update transaction references if available
        if (callbackRequest.getMasterTransactionId() != null && !callbackRequest.getMasterTransactionId().trim().isEmpty()) {
            payment.setMasterTransactionId(callbackRequest.getMasterTransactionId().trim());
        }

        if (callbackRequest.getPaymentMethod() != null && !callbackRequest.getPaymentMethod().trim().isEmpty()) {
            payment.setPaymentMethod(callbackRequest.getPaymentMethod().trim());
        }

        // Map callback status safely
        String incomingStatus = callbackRequest.getStatus();
        if (incomingStatus != null) {
            String normalized = incomingStatus.trim().toUpperCase();
            switch (normalized) {
                case "SUCCESS":
                    payment.setStatus("SUCCESS");
                    break;
                case "FAILED":
                    payment.setStatus("FAILED");
                    break;
                case "CANCELLED":
                    payment.setStatus("CANCELLED");
                    break;
                case "PENDING":
                    payment.setStatus("PENDING");
                    break;
                default:
                    log.warn("Unknown status '{}' received in Gateway callback for #{}", incomingStatus, payment.getId());
                    payment.setStatus(normalized);
                    break;
            }
        }

        paymentRepository.save(payment);
        log.info("Payment #{} status updated to '{}' from Gateway callback.", payment.getId(), payment.getStatus());

        // Automatically reconcile split allocation if this payment is linked to one
        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            try {
                Optional<PaymentSplitAllocation> allocOpt = allocationRepository.findByPaymentId(payment.getId());
                if (allocOpt.isPresent()) {
                    PaymentSplitAllocation alloc = allocOpt.get();
                    log.info("Reconciling split allocation #{} for split #{} with successful Gateway payment #{}",
                            alloc.getId(), alloc.getSplitId(), payment.getId());
                    paymentSplitService.recordSuccessfulPayment(
                            alloc.getSplitId(),
                            alloc.getId(),
                            payment.getId()

                    );
                }
            } catch (Exception ex) {
                log.error("Failed to automatically reconcile split allocation for payment #{}: {}", payment.getId(), ex.getMessage(), ex);
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "paymentStatus", payment.getStatus(),
                "merchantOrderRef", merchantOrderRef
        ));
    }
}
