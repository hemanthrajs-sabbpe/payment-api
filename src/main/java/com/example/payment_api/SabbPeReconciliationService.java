
package com.example.payment_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SabbPeReconciliationService {

    private static final Logger logger =
            LoggerFactory.getLogger(SabbPeReconciliationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentSplitAllocationRepository allocationRepository;
    private final PaymentSplitService paymentSplitService;
    private final SabbPeStatusService sabbPeStatusService;

    public SabbPeReconciliationService(
            PaymentRepository paymentRepository,
            PaymentSplitAllocationRepository allocationRepository,
            PaymentSplitService paymentSplitService,
            SabbPeStatusService sabbPeStatusService) {

        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.paymentSplitService = paymentSplitService;
        this.sabbPeStatusService = sabbPeStatusService;
    }

    public Map<String, Object> reconcilePendingPayments() {

        List<Payment> pendingPayments =
                paymentRepository
                        .findByStatusIgnoreCaseAndPblOrderRefIsNotNull("PENDING");

        int checked = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        int allocationsSynchronized = 0;

        logger.info(
                "[SabbPe Reconciliation] Starting. Pending payments: {}",
                pendingPayments.size()
        );

        for (Payment payment : pendingPayments) {
            checked++;

            try {
                boolean changed = reconcilePayment(payment);

                if (changed) {
                    updated++;
                } else {
                    unchanged++;
                }

                if (synchronizeSplitAllocation(payment.getId())) {
                    allocationsSynchronized++;
                }

            } catch (RuntimeException ex) {
                failed++;

                logger.error(
                        "[SabbPe Reconciliation] Failed for payment ID {}: {}",
                        payment.getId(),
                        ex.getMessage(),
                        ex
                );
            }
        }

        /*
         * Repair split allocations using payment statuses already saved
         * in the database. This repair does not call SabbPe or change
         * the saved payment status.
         */
        for (PaymentSplitAllocation allocation : allocationRepository.findAll()) {

            if (allocation.getPaymentId() == null
                    || "SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
                continue;
            }

            try {
                Payment linkedPayment = paymentRepository
                        .findById(allocation.getPaymentId())
                        .orElse(null);

                if (linkedPayment == null) {
                    continue;
                }

                String linkedStatus = linkedPayment.getStatus() == null
                        ? ""
                        : linkedPayment.getStatus()
                                .trim()
                                .toUpperCase(Locale.ROOT);

                if (!List.of(
                        "SUCCESS",
                        "FAILED",
                        "CANCELLED",
                        "TIMEOUT",
                        "REFUNDED"
                ).contains(linkedStatus)) {
                    continue;
                }

                if (synchronizeSplitAllocation(linkedPayment.getId())) {
                    allocationsSynchronized++;
                }

            } catch (RuntimeException ex) {
                failed++;

                logger.error(
                        "[SabbPe Reconciliation] Failed to synchronize "
                                + "allocation ID {} for payment ID {}: {}",
                        allocation.getId(),
                        allocation.getPaymentId(),
                        ex.getMessage(),
                        ex
                );
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checked", checked);
        result.put("updated", updated);
        result.put("unchanged", unchanged);
        result.put("failed", failed);
        result.put("allocationsSynchronized", allocationsSynchronized);

        logger.info(
                "[SabbPe Reconciliation] Finished. checked={}, updated={}, "
                        + "unchanged={}, failed={}, allocationsSynchronized={}",
                checked,
                updated,
                unchanged,
                failed,
                allocationsSynchronized
        );

        return result;
    }

    @Transactional
    public boolean reconcilePayment(Payment payment) {

        if (payment == null
                || payment.getId() == null
                || isBlank(payment.getPblOrderRef())
                || isBlank(payment.getMerchantOrderRef())
                || payment.getAmount() == null
                || payment.getAmount() <= 0) {

            throw new IllegalArgumentException(
                    "Payment is missing required reconciliation fields."
            );
        }

        logger.info(
                "[SabbPe Reconciliation] Checking payment ID {}",
                payment.getId()
        );

        /*
         * Verify the gateway result against the saved payment.
         * The status service validates references and amount.
         */
        SabbPeStatusService.SabbPePaymentStatusResponse response =
                sabbPeStatusService.getVerifiedPaymentStatus(payment);

        logger.info(
                "[SabbPe Reconciliation] Payment ID: {}, Gateway Status: {}, "
                        + "Gateway Amount: {}, PBL Reference: {}, Merchant Reference: {}",
                payment.getId(),
                response.paymentStatus(),
                response.amount(),
                response.pblOrderRef(),
                response.merchantOrderRef()
        );

        String gatewayStatus =
                response.paymentStatus().trim().toUpperCase(Locale.ROOT);

        String currentStatus = payment.getStatus() == null
                ? ""
                : payment.getStatus().trim().toUpperCase(Locale.ROOT);

        /*
         * Never downgrade an already successful payment.
         * Only fill a missing transaction ID when SabbPe confirms SUCCESS.
         */
        if ("SUCCESS".equals(currentStatus)) {

            if ("SUCCESS".equals(gatewayStatus)
                    && isBlank(payment.getTransactionId())
                    && !isBlank(response.masterTransactionId())) {

                payment.setTransactionId(response.masterTransactionId().trim());
                paymentRepository.save(payment);

                logger.info(
                        "[SabbPe Reconciliation] Filled missing transaction ID "
                                + "for already successful payment ID {}.",
                        payment.getId()
                );

                return true;
            }

            logger.info(
                    "[SabbPe Reconciliation] Payment ID {} is already SUCCESS; "
                            + "status will not be downgraded.",
                    payment.getId()
            );

            return false;
        }

        switch (gatewayStatus) {

            case "SUCCESS":
                payment.setStatus("SUCCESS");

                if (!isBlank(response.masterTransactionId())) {
                    payment.setTransactionId(
                            response.masterTransactionId().trim()
                    );
                }

                paymentRepository.save(payment);

                logger.info(
                        "[SabbPe Reconciliation] Payment ID {} updated to SUCCESS.",
                        payment.getId()
                );

                return true;

            case "FAILED":
            case "CANCELLED":
            case "TIMEOUT":
            case "REFUNDED":
                payment.setStatus(gatewayStatus);
                paymentRepository.save(payment);

                logger.info(
                        "[SabbPe Reconciliation] Payment ID {} updated to {}.",
                        payment.getId(),
                        gatewayStatus
                );

                return true;

            case "PENDING":
                if (!"PENDING".equals(currentStatus)) {
                    payment.setStatus("PENDING");
                    paymentRepository.save(payment);
                    return true;
                }

                return false;

            default:
                throw new IllegalStateException(
                        "Unsupported SabbPe payment status: " + gatewayStatus
                );
        }
    }

    /**
     * Synchronizes a split allocation according to the payment's saved
     * status. PaymentSplitService performs the existing amount and
     * relationship checks before updating the allocation.
     */
    @Transactional
    public boolean synchronizeSplitAllocation(Long paymentId) {

        if (paymentId == null) {
            return false;
        }

        Payment payment = paymentRepository.findById(paymentId).orElse(null);

        if (payment == null || isBlank(payment.getStatus())) {
            return false;
        }

        PaymentSplitAllocation allocation =
                allocationRepository.findByPaymentId(paymentId).orElse(null);

        if (allocation == null) {
            // This may be an ordinary payment rather than a split payment.
            return false;
        }

        String status = payment.getStatus()
                .trim()
                .toUpperCase(Locale.ROOT);

        String allocationStatus = allocation.getStatus() == null
                ? ""
                : allocation.getStatus().trim().toUpperCase(Locale.ROOT);

        if ("SUCCESS".equals(status)) {

            if ("SUCCESS".equals(allocationStatus)) {
                return false;
            }

            paymentSplitService.recordSuccessfulPayment(
                    allocation.getSplitId(),
                    allocation.getId(),
                    paymentId
            );

        } else if (List.of(
                "FAILED",
                "CANCELLED",
                "TIMEOUT",
                "REFUNDED"
        ).contains(status)) {

            if (status.equals(allocationStatus)) {
                return false;
            }

            paymentSplitService.recordFailedPayment(
                    allocation.getSplitId(),
                    allocation.getId(),
                    paymentId,
                    status
            );

        } else {
            // A pending payment must not be marked paid or failed.
            if ("SUCCESS".equals(allocationStatus)) {
                return false;
            }

            if ("PENDING".equals(status)
                    && "PENDING".equals(allocationStatus)) {
                return false;
            }

            return false;
        }

        logger.info(
                "[SabbPe Reconciliation] Synchronized payment ID {} "
                        + "with allocation ID {} in split ID {} as {}.",
                paymentId,
                allocation.getId(),
                allocation.getSplitId(),
                status
        );

        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
