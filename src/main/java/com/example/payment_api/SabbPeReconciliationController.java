
package com.example.payment_api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sabbpe/reconciliation")
public class SabbPeReconciliationController {

    private final SabbPeReconciliationService reconciliationService;
    private final PaymentRepository paymentRepository;
    private final PaymentReceiptTokenService receiptTokenService;

    public SabbPeReconciliationController(
            SabbPeReconciliationService reconciliationService,
            PaymentRepository paymentRepository,
            PaymentReceiptTokenService receiptTokenService) {
        this.reconciliationService = reconciliationService;
        this.paymentRepository = paymentRepository;
        this.receiptTokenService = receiptTokenService;
    }

    /**
     * Verifies one saved SabbPe payment against the provider and persists the
     * verified result. The browser redirect alone is never treated as proof
     * of payment.
     * Public access is permitted ONLY when authorized by a payment-bound receipt token.
     * Authenticated merchant users with a valid JWT may also reconcile their payments.
     */
    @PostMapping("/payments/{paymentId}/reconcile")
    public ResponseEntity<?> reconcileOnePayment(
            @PathVariable Long paymentId,
            @RequestParam(value = "receiptToken", required = false) String receiptTokenParam,
            @RequestHeader(value = "X-Receipt-Token", required = false) String receiptTokenHeader) {

        Payment payment = paymentRepository.findById(paymentId).orElse(null);

        if (payment == null) {
            return ResponseEntity.notFound().build();
        }

        // Authorization check: either valid authenticated session OR valid unguessable receiptToken
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null && !"anonymousUser".equals(auth.getPrincipal());

        String receiptToken = (receiptTokenHeader != null && !receiptTokenHeader.isBlank())
                ? receiptTokenHeader
                : receiptTokenParam;

        boolean tokenValid = receiptToken != null && receiptTokenService.validateReceiptToken(paymentId, receiptToken, payment);

        if (!isAuthenticated && !tokenValid) {
            return ResponseEntity.status(403)
                    .body(Map.of(
                            "message",
                            "Forbidden: Reconciling this payment requires a valid receipt authorization token or active session."
                    ));
        }

        if (payment.getPblOrderRef() == null
                || payment.getPblOrderRef().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Payment has no SabbPe Pay By Link reference"
                    ));
        }

        try {
            reconciliationService.reconcilePayment(payment);
            reconciliationService.synchronizeSplitAllocation(paymentId);

            Payment updated = paymentRepository.findById(paymentId)
                    .orElse(payment);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("paymentId", updated.getId());
            result.put("status", updated.getStatus());
            result.put("transactionId", updated.getTransactionId());
            result.put("merchantOrderRef", updated.getMerchantOrderRef());
            result.put("pblOrderRef", updated.getPblOrderRef());
            if (tokenValid || receiptTokenService != null) {
                result.put("receiptToken", receiptTokenService.generateReceiptToken(updated));
            }

            return ResponseEntity.ok(result);

        } catch (RuntimeException ex) {
            // Do not expose provider response bodies, credentials, or raw exceptions.
            return ResponseEntity.status(502)
                    .body(Map.of(
                            "message",
                            "SabbPe status could not be verified. "
                                    + "The saved payment status was not assumed successful."
                    ));
        }
    }

    /**
     * Repairs a split allocation using the payment's saved status.
     * Does not call the SabbPe provider.
     */
    @PostMapping("/payments/{paymentId}/repair-allocation")
    public ResponseEntity<?> repairAllocation(
            @PathVariable Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId).orElse(null);

        if (payment == null) {
            return ResponseEntity.notFound().build();
        }

        String status = payment.getStatus() == null
                ? ""
                : payment.getStatus().trim()
                        .toUpperCase(java.util.Locale.ROOT);

        if (!java.util.List.of(
                "SUCCESS",
                "FAILED",
                "CANCELLED",
                "TIMEOUT",
                "REFUNDED"
        ).contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Payment does not have a supported terminal status."
                    ));
        }

        try {
            boolean synchronizedAllocation =
                    reconciliationService.synchronizeSplitAllocation(paymentId);

            return ResponseEntity.ok(Map.of(
                    "paymentId", paymentId,
                    "paymentStatus", status,
                    "allocationSynchronized", synchronizedAllocation,
                    "message", synchronizedAllocation
                            ? "Allocation repaired from saved payment status."
                            : "No allocation change was needed or no allocation was found."
            ));

        } catch (RuntimeException ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Allocation repair failed."));
        }
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runReconciliation() {

        Map<String, Object> result =
                reconciliationService.reconcilePendingPayments();

        return ResponseEntity.ok(result);
    }
}
