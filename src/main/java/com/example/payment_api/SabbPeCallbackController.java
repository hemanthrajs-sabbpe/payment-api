package com.example.payment_api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/sabbpe")
public class SabbPeCallbackController {

    private final PaymentRepository paymentRepository;
    private final SabbPeStatusService sabbPeStatusService;
    private final PaymentSplitAllocationRepository allocationRepository;
    private final PaymentSplitService paymentSplitService;

    public SabbPeCallbackController(
            PaymentRepository paymentRepository,
            SabbPeStatusService sabbPeStatusService,
            PaymentSplitAllocationRepository allocationRepository,
            PaymentSplitService paymentSplitService) {

        this.paymentRepository = paymentRepository;
        this.sabbPeStatusService = sabbPeStatusService;
        this.allocationRepository = allocationRepository;
        this.paymentSplitService = paymentSplitService;
    }

    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<String> receiveCallback(
            @RequestBody(required = false)
            SabbPeCallbackRequest callback) {

        // 1. Validate incoming callback fields.
        if (callback == null
                || isBlank(callback.gateway())
                || isBlank(callback.product())
                || isBlank(callback.merchantOrderRef())
                || isBlank(callback.pblOrderRef())
                || isBlank(callback.paymentStatus())
                || callback.amount() == null) {

            return ResponseEntity.badRequest()
                    .body("Missing required callback fields");
        }

        // 2. Validate the incoming callback gateway.
        if (!"SABBPE".equalsIgnoreCase(callback.gateway().trim())) {
            return ResponseEntity.badRequest()
                    .body("Invalid gateway");
        }

        // 3. Validate the incoming callback product.
        if (!"PAY_BY_LINK".equalsIgnoreCase(callback.product().trim())) {
            return ResponseEntity.badRequest()
                    .body("Invalid product");
        }

        // 4. Validate the incoming amount.
        if (callback.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest()
                    .body("Invalid payment amount");
        }

        // 5. Find the existing payment using the merchant order reference.
        Optional<Payment> optionalPayment =
                paymentRepository.findByMerchantOrderRef(
                        callback.merchantOrderRef().trim());

        if (optionalPayment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Payment not found");
        }

        Payment payment = optionalPayment.get();

        // 6. Validate the Pay By Link reference.
        if (isBlank(payment.getPblOrderRef())
                || !payment.getPblOrderRef().trim()
                .equals(callback.pblOrderRef().trim())) {

            return ResponseEntity.badRequest()
                    .body("Pay By Link reference mismatch");
        }

        // 7. Validate the stored payment amount.
        if (payment.getAmount() == null) {
            return ResponseEntity.badRequest()
                    .body("Stored payment amount is missing");
        }

        BigDecimal storedAmount =
                BigDecimal.valueOf(payment.getAmount());

        if (storedAmount.compareTo(callback.amount()) != 0) {
            return ResponseEntity.badRequest()
                    .body("Callback amount mismatch");
        }

        // 8. Independently verify the payment status with SabbPe.
        // Do not update the payment or allocation if verification fails.
        final SabbPeStatusService.SabbPePaymentStatusResponse verifiedStatus;

        try {
            verifiedStatus = sabbPeStatusService.getVerifiedPaymentStatus(payment);

        } catch (RuntimeException ex) {

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Unable to verify payment status with SabbPe");
        }

        // 9. Validate that SabbPe returned all required status fields.
        if (verifiedStatus == null
                || isBlank(verifiedStatus.gateway())
                || isBlank(verifiedStatus.product())
                || isBlank(verifiedStatus.pblOrderRef())
                || isBlank(verifiedStatus.merchantOrderRef())
                || isBlank(verifiedStatus.paymentStatus())
                || verifiedStatus.amount() == null) {

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("SabbPe returned an incomplete status response");
        }

        // 10. NEW SAFETY CHECK:
        // Validate the gateway in SabbPe's independently verified response.
        if (!"SABBPE".equalsIgnoreCase(
                verifiedStatus.gateway().trim())) {

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("SabbPe status gateway mismatch");
        }

        // 11. NEW SAFETY CHECK:
        // Validate the product in SabbPe's independently verified response.
        if (!"PAY_BY_LINK".equalsIgnoreCase(
                verifiedStatus.product().trim())) {

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("SabbPe status product mismatch");
        }

        // 12. Verify the order references against our stored payment.
        if (!payment.getPblOrderRef().trim().equals(
                verifiedStatus.pblOrderRef().trim())
                || isBlank(payment.getMerchantOrderRef())
                || !payment.getMerchantOrderRef().trim().equals(
                verifiedStatus.merchantOrderRef().trim())) {

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("SabbPe status reference mismatch");
        }

        // 13. Verify the independently reported amount.
        if (storedAmount.compareTo(verifiedStatus.amount()) != 0) {

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("SabbPe status amount mismatch");
        }

        // 14. Use the independently verified status, not the callback status.
        String status = verifiedStatus.paymentStatus()
                .trim()
                .toUpperCase(Locale.ROOT);

        switch (status) {

            case "SUCCESS":

                // Save the independently verified payment result.
                // Do not downgrade or overwrite an existing transaction ID.
                boolean paymentChanged = false;

                if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
                    payment.setStatus("SUCCESS");
                    paymentChanged = true;
                }

                // A duplicate callback can safely fill a missing transaction ID.
                // Keep an existing transaction ID unchanged.
                if (isBlank(payment.getTransactionId())
                        && !isBlank(verifiedStatus.masterTransactionId())) {
                    payment.setTransactionId(
                            verifiedStatus.masterTransactionId().trim());
                    paymentChanged = true;
                }

                if (paymentChanged) {
                    paymentRepository.save(payment);
                }

                // Update a linked split allocation, if one exists.
                Optional<PaymentSplitAllocation> successfulAllocation =
                        allocationRepository.findByPaymentId(payment.getId());

                if (successfulAllocation.isPresent()) {

                    PaymentSplitAllocation allocation =
                            successfulAllocation.get();

                    paymentSplitService.recordSuccessfulPayment(
                            allocation.getSplitId(),
                            allocation.getId(),
                            payment.getId());
                }

                return ResponseEntity.ok(
                        "Payment status verified as SUCCESS");

            case "PENDING":

                // Never downgrade an already successful payment.
                if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {

                    payment.setStatus("PENDING");
                    paymentRepository.save(payment);
                }

                return ResponseEntity.accepted()
                        .body("Payment is still pending at SabbPe");

            case "FAILED":
            case "CANCELLED":
            case "TIMEOUT":
            case "REFUNDED":

                // Never overwrite SUCCESS with a failure status.
                if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {

                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(
                                    "Payment is already SUCCESS; "
                                    + "manual reconciliation required");
                }

                payment.setStatus(status);
                paymentRepository.save(payment);

                // Update the linked split allocation, if applicable.
                Optional<PaymentSplitAllocation> failedAllocation =
                        allocationRepository.findByPaymentId(payment.getId());

                if (failedAllocation.isPresent()) {

                    PaymentSplitAllocation allocation =
                            failedAllocation.get();

                    paymentSplitService.recordFailedPayment(
                            allocation.getSplitId(),
                            allocation.getId(),
                            payment.getId(),
                            status);
                }

                return ResponseEntity.ok(
                        "Payment status verified as " + status);

            default:

                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(
                                "SabbPe returned an unsupported payment status");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}