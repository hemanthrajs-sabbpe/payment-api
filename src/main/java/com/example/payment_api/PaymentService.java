package com.example.payment_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EasebuzzService easebuzzService;
    private final PaymentSplitAllocationRepository allocationRepository;
    private final PaymentSplitService paymentSplitService;

    @Value("${payment.callback.secret}")
    private String callbackSecret;

    @Value("${easebuzz.success-url:http://localhost:8080/api/payments/easebuzz/response}")
    private String successUrl;

    @Value("${easebuzz.failure-url:http://localhost:8080/api/payments/easebuzz/response}")
    private String failureUrl;

    public PaymentService(
            PaymentRepository paymentRepository,
            EasebuzzService easebuzzService,
            PaymentSplitAllocationRepository allocationRepository,
            PaymentSplitService paymentSplitService) {
        this.paymentRepository = paymentRepository;
        this.easebuzzService = easebuzzService;
        this.allocationRepository = allocationRepository;
        this.paymentSplitService = paymentSplitService;
    }

    // CREATE PAYMENT: client-supplied status is not trusted.
    public Payment createPayment(PaymentRequest request) {
        Payment payment = new Payment(
                request.getCustomerName(),
                request.getAmount(),
                "PENDING"
        );

        return paymentRepository.save(payment);
    }

    // GET ALL PAYMENTS
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    // GET PAYMENT BY ID
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    // UPDATE PAYMENT: ordinary API requests cannot change payment status.
    @Transactional
    public Payment updatePayment(Long id, PaymentRequest request) {
        Payment existingPayment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        // Keep gateway-controlled payment details immutable through this endpoint.
        if (hasGatewayReference(existingPayment)) {
            throw new IllegalStateException(
                    "Gateway-linked payments cannot be edited through the general update endpoint");
        }

        if ("SUCCESS".equalsIgnoreCase(normalizeStatus(existingPayment.getStatus()))) {
            throw new IllegalStateException("A successful payment cannot be edited");
        }

        existingPayment.setCustomerName(request.getCustomerName());
        existingPayment.setAmount(request.getAmount());
        // Intentionally do not copy request.getStatus().
        return paymentRepository.save(existingPayment);
    }

    // DELETE PAYMENT
    public void deletePayment(Long id) {
        Payment existingPayment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        if (hasGatewayReference(existingPayment)
                || "SUCCESS".equalsIgnoreCase(normalizeStatus(existingPayment.getStatus()))) {
            throw new IllegalStateException(
                    "Gateway-linked or successful payment records cannot be deleted through this endpoint");
        }

        paymentRepository.delete(existingPayment);
    }

    // INITIATE EASEBUZZ SANDBOX PAYMENT
    // Repository saves commit independently; provider failures can persist INITIATION_FAILED.
    public Map<String, Object> initiateEasebuzzPayment(
            EasebuzzPaymentRequest request) {

        String transactionId = "TXN-" + UUID.randomUUID()
                .toString().replace("-", "");

        Payment payment = new Payment(
                request.getFirstname(),
                request.getAmount().doubleValue(),
                "PENDING"
        );

        payment.setTransactionId(transactionId);
        payment = paymentRepository.save(payment);

        try {
            String checkoutUrl = easebuzzService.initiatePayment(
                    transactionId,
                    request.getAmount(),
                    request.getProductinfo(),
                    request.getFirstname(),
                    request.getEmail(),
                    request.getPhone(),
                    successUrl,
                    failureUrl
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("paymentId", payment.getId());
            response.put("transactionId", transactionId);
            response.put("amount", request.getAmount());
            response.put("status", "PENDING");
            response.put("checkoutUrl", checkoutUrl);
            response.put("message",
                    "Payment initiated. Complete checkout to continue.");

            return response;

        } catch (RuntimeException ex) {
            payment.setStatus("INITIATION_FAILED");
            paymentRepository.save(payment);
            throw ex;
        }
    }

    // EXISTING CUSTOM CALLBACK — retained, but cannot independently declare SUCCESS.
    @Transactional
    public Payment updatePaymentStatus(PaymentCallbackRequest request) {

        if (callbackSecret == null || request.getSecret() == null
                || !callbackSecret.equals(request.getSecret())) {
            throw new InvalidCallbackSecretException();
        }

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() ->
                        new PaymentNotFoundException(request.getPaymentId()));

        String requestedStatus = normalizeStatus(request.getStatus());
        String currentStatus = normalizeStatus(payment.getStatus());

        if ("SUCCESS".equals(requestedStatus)) {
            throw new IllegalArgumentException(
                    "SUCCESS must be established through a verified payment-gateway callback or status check");
        }

        if (!"FAILED".equals(requestedStatus)
                && !"CANCELLED".equals(requestedStatus)
                && !"TIMEOUT".equals(requestedStatus)) {
            throw new IllegalArgumentException("Unsupported callback payment status");
        }

        if ("SUCCESS".equals(currentStatus)) {
            // Never downgrade a successful payment from a later callback.
            return payment;
        }

        payment.setStatus(requestedStatus);
        return paymentRepository.save(payment);
    }

    // PROCESS EASEBUZZ CALLBACK
    @Transactional
    public Payment processEasebuzzCallback(
            EasebuzzCallbackRequest callback) {

        // Verify the callback hash before trusting any callback fields.
        if (!easebuzzService.verifyCallback(callback)) {
            throw new IllegalArgumentException(
                    "Invalid Easebuzz callback hash");
        }

        if (callback.getTxnid() == null || callback.getTxnid().isBlank()
                || callback.getAmount() == null || callback.getAmount().isBlank()
                || callback.getStatus() == null || callback.getStatus().isBlank()) {
            throw new IllegalArgumentException(
                    "Easebuzz callback is missing required transaction fields");
        }

        // Find the original payment by transaction ID.
        Payment payment = paymentRepository
                .findByTransactionId(callback.getTxnid())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment transaction not found"));

        // Verify the amount.
        final BigDecimal receivedAmount;
        try {
            receivedAmount = new BigDecimal(callback.getAmount().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid amount in Easebuzz callback", ex);
        }

        BigDecimal storedAmount = BigDecimal.valueOf(payment.getAmount());

        if (receivedAmount.compareTo(storedAmount) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount does not match");
        }

        String result = callback.getStatus().trim().toLowerCase(Locale.ROOT);
        String currentStatus = normalizeStatus(payment.getStatus());

        if ("SUCCESS".equals(currentStatus)) {
            // A later failure/unknown callback must not downgrade a verified success.
            if ("success".equals(result)) {
                return payment;
            }
            throw new IllegalArgumentException(
                    "A successful payment cannot be downgraded by a later callback");
        }

        if ("success".equals(result)) {
            payment.setStatus("SUCCESS");
        } else if ("failure".equals(result) || "failed".equals(result)) {
            payment.setStatus("FAILED");
        } else {
            throw new IllegalArgumentException(
                    "Unrecognized Easebuzz payment status");
        }

        Payment savedPayment = paymentRepository.save(payment);

        // If this payment is linked to a split allocation, synchronize the allocation safely
        if ("SUCCESS".equalsIgnoreCase(savedPayment.getStatus())) {
            try {
                Optional<PaymentSplitAllocation> allocOpt = allocationRepository.findByPaymentId(savedPayment.getId());
                if (allocOpt.isPresent()) {
                    PaymentSplitAllocation alloc = allocOpt.get();
                    paymentSplitService.recordSuccessfulPayment(
                            alloc.getSplitId(),
                            alloc.getId(),
                            savedPayment.getId()
                    );
                }
            } catch (Exception ex) {
                // Safeguard: do not fail callback transaction if allocation synchronization encounters non-critical error
            }
        }

        return savedPayment;
    }

    private boolean hasGatewayReference(Payment payment) {
        return isNotBlank(payment.getTransactionId())
                || isNotBlank(payment.getMerchantOrderRef())
                || isNotBlank(payment.getPblOrderRef());
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }
}
