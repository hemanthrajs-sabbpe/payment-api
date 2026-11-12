package com.example.payment_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/payment-splits")
public class PaymentSplitController {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentSplitController.class);

    private final PaymentSplitService paymentSplitService;
    private final SabbPePaymentService sabbPePaymentService;
    private final BusinessCategoryRepository businessCategoryRepository;

    public PaymentSplitController(
            PaymentSplitService paymentSplitService,
            SabbPePaymentService sabbPePaymentService,
            BusinessCategoryRepository businessCategoryRepository) {

        this.paymentSplitService = paymentSplitService;
        this.sabbPePaymentService = sabbPePaymentService;
        this.businessCategoryRepository = businessCategoryRepository;
    }

    // =========================================================
    // 1. CREATE MASTER BILL AND ALLOCATIONS
    // =========================================================

    @PostMapping
    public ResponseEntity<CreateSplitResponse> createSplit(
            @RequestBody CreateSplitRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request body is required.");
        }

        PaymentSplit split;
        List<PaymentSplitAllocation> allocations;

        if (request.getAllocations() != null
                && !request.getAllocations().isEmpty()) {

            List<PaymentSplitService.AllocationInput> inputs =
                    new ArrayList<>();

            for (AllocationRequest allocation :
                    request.getAllocations()) {

                if (allocation == null) {
                    throw new IllegalArgumentException(
                            "Allocation entries cannot be null.");
                }

                inputs.add(
                        new PaymentSplitService.AllocationInput(
                                allocation.getParticipantName(),
                                allocation.getParticipantType(),
                                allocation.getAllocationAmount(),
                                allocation.getAllocationPercentage()
                        )
                );
            }

            split = paymentSplitService.createSplitWithAllocations(
                    request.getSplitType(),
                    request.getTotalAmount(),
                    request.getCurrencyCode(),
                    request.getPaymentMode(),
                    request.getPlatformFeePercentage(),
                    inputs,
                    request.getSplitScope(),
                    request.getOrganizationId(),
                    request.getBusinessCategoryId()
            );

            allocations =
                    paymentSplitService.getAllocationsForCurrentUser(split.getId());

        } else {

            // Preserve support for creating a split without allocations.
            split = paymentSplitService.createSplit(
                    request.getSplitType(),
                    request.getTotalAmount(),
                    request.getCurrencyCode(),
                    request.getPaymentMode(),
                    request.getPlatformFeePercentage(),
                    request.getSplitScope(),
                    request.getOrganizationId(),
                    request.getBusinessCategoryId()
            );

            allocations =
                    paymentSplitService.getAllocationsForCurrentUser(split.getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateSplitResponse(split, allocations));
    }

    // =========================================================
    // 2. GET ACTIVE BUSINESS CATEGORIES
    // GET /api/payment-splits/business-categories
    // =========================================================

    @GetMapping("/business-categories")
    public ResponseEntity<List<BusinessCategory>> getActiveBusinessCategories() {
        return ResponseEntity.ok(
                businessCategoryRepository.findByStatus("ACTIVE"));
    }

    // =========================================================
    // 3. GET ALL MASTER BILLS
    // =========================================================

    @GetMapping
    public ResponseEntity<List<PaymentSplit>> getAllSplits() {

        return ResponseEntity.ok(
                paymentSplitService.getAllSplits());
    }

    // =========================================================
    // 3. GET ONE MASTER BILL
    // =========================================================

    @GetMapping("/{splitId}")
    public ResponseEntity<Map<String, Object>> getSplitById(
            @PathVariable Long splitId) {

        // Keep the existing endpoint, but return the master bill together
        // with a summary and the current status of each allocation.
        PaymentSplit split = paymentSplitService.getSplitByIdForCurrentUser(splitId);
        List<PaymentSplitAllocation> allocations =
                paymentSplitService.getAllocationsForCurrentUser(splitId);

        BigDecimal paidAmount = BigDecimal.ZERO.setScale(2);
        int paidParticipants = 0;
        int pendingParticipants = 0;
        int failedParticipants = 0;
        List<Map<String, Object>> participants = new ArrayList<>();

        for (PaymentSplitAllocation allocation : allocations) {
            String allocationStatus = allocation.getStatus() == null
                    ? "UNKNOWN"
                    : allocation.getStatus().toUpperCase(Locale.ROOT);

            BigDecimal participantPaidAmount =
                    allocation.getPaidAmount() == null
                            ? BigDecimal.ZERO.setScale(2)
                            : allocation.getPaidAmount().setScale(2, RoundingMode.HALF_UP);

            if ("SUCCESS".equals(allocationStatus)) {
                paidParticipants++;
                paidAmount = paidAmount.add(participantPaidAmount);
            } else if ("PENDING".equals(allocationStatus)
                    || "CREATED".equals(allocationStatus)) {
                pendingParticipants++;
            } else {
                failedParticipants++;
            }

            Map<String, Object> participant = new LinkedHashMap<>();
            participant.put("allocationId", allocation.getId());
            participant.put("participantName", allocation.getParticipantName());
            participant.put("participantType", allocation.getParticipantType());
            participant.put("allocationAmount", allocation.getAllocationAmount());
            participant.put("allocationPercentage", allocation.getAllocationPercentage());
            participant.put("paymentId", allocation.getPaymentId());
            participant.put("paidAmount", participantPaidAmount);
            participant.put("allocationStatus", allocationStatus);
            participants.add(participant);
        }

        BigDecimal remainingAmount = split.getTotalAmount()
                .subtract(paidAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalParticipants", allocations.size());
        summary.put("paidParticipants", paidParticipants);
        summary.put("pendingParticipants", pendingParticipants);
        summary.put("failedOrOtherParticipants", failedParticipants);
        summary.put("totalAmount", split.getTotalAmount());
        summary.put("paidAmount", paidAmount);
        summary.put("remainingAmount", remainingAmount);
        summary.put("allParticipantsPaid", !allocations.isEmpty()
                && paidParticipants == allocations.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("masterBill", split);
        response.put("summary", summary);
        response.put("participants", participants);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // 4. ADD AN ALLOCATION
    // =========================================================

    @PostMapping("/{splitId}/allocations")
    public ResponseEntity<PaymentSplitAllocation> addAllocation(
            @PathVariable Long splitId,
            @RequestBody AllocationRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Allocation request body is required.");
        }

        PaymentSplitAllocation allocation =
                paymentSplitService.addAllocation(
                        splitId,
                        request.getParticipantName(),
                        request.getParticipantType(),
                        request.getAllocationAmount(),
                        request.getAllocationPercentage()
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(allocation);
    }

    // =========================================================
    // 5. GET ALLOCATIONS FOR A MASTER BILL
    // =========================================================

    @GetMapping("/{splitId}/allocations")
    public ResponseEntity<List<PaymentSplitAllocation>> getAllocations(
            @PathVariable Long splitId) {

        return ResponseEntity.ok(
                paymentSplitService.getAllocationsForCurrentUser(splitId));
    }

    // =========================================================
    // 6. VALIDATE THE MASTER BILL
    // =========================================================

    @GetMapping("/{splitId}/validate")
    public ResponseEntity<Map<String, Object>> validateSplit(
            @PathVariable Long splitId) {

        return ResponseEntity.ok(
                paymentSplitService.validateSplit(splitId));
    }

    // =========================================================
    // 7. BULK PAYMENT-LINK INITIATION
    //
    // POST /api/payment-splits/{splitId}/payment-links
    //
    // Contact details are supplied dynamically by the frontend.
    // Participant name and amount come from existing allocations.
    // =========================================================

    @PostMapping("/{splitId}/payment-links")
    public ResponseEntity<Map<String, Object>> initiateBulkPaymentLinks(
            @PathVariable Long splitId,
            @RequestBody BulkPaymentLinksRequest request) {

        if (request == null
                || request.getParticipants() == null
                || request.getParticipants().isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one participant is required.");
        }

        // Verify ownership or active business-management membership before
        // reading allocations or initiating any provider payment-link request.
        paymentSplitService.authorizeSplitAccess(splitId);

        List<PaymentSplitAllocation> allocations =
                paymentSplitService.getAllocationsForCurrentUser(splitId);

        List<Map<String, Object>> results = new ArrayList<>();
        Set<Long> processedAllocationIds = new HashSet<>();

        for (ParticipantContact participant :
                request.getParticipants()) {

            Map<String, Object> result = new LinkedHashMap<>();

            if (participant == null
                    || participant.getAllocationId() == null) {

                result.put("status", "FAILED");
                result.put("message", "Allocation ID is required.");

                results.add(result);
                continue;
            }

            Long allocationId = participant.getAllocationId();

            result.put("allocationId", allocationId);

            // Prevent the same allocation from being processed twice in one
            // bulk request, which could otherwise trigger repeated link attempts.
            if (!processedAllocationIds.add(allocationId)) {
                result.put("status", "FAILED");
                result.put("message",
                        "Duplicate allocation ID in this request.");

                results.add(result);
                continue;
            }

            // Find the allocation inside this master bill.
            PaymentSplitAllocation allocation = allocations.stream()
                    .filter(item ->
                            allocationId.equals(item.getId()))
                    .findFirst()
                    .orElse(null);

            if (allocation == null) {

                result.put("status", "FAILED");
                result.put("message",
                        "Allocation does not belong to this split.");

                results.add(result);
                continue;
            }

            result.put(
                    "participantName",
                    allocation.getParticipantName());

            result.put(
                    "amount",
                    allocation.getAllocationAmount());

            // Never generate another link for an already-paid allocation.
            if ("SUCCESS".equalsIgnoreCase(
                    allocation.getStatus())) {

                result.put("status", "ALREADY_PAID");
                result.put("message",
                        "This allocation is already marked successful.");

                results.add(result);
                continue;
            }

            /*
             * Do not reject every existing payment here.
             * SabbPePaymentService verifies the old payment with the gateway
             * and either preserves it or permits a replacement after a
             * verified terminal status.
             */

            try {

                // Validate each participant's contact details.
                validateParticipantContact(participant);

                // Build a request using trusted allocation details.
                SabbPePaymentRequest paymentRequest =
                        new SabbPePaymentRequest();

                paymentRequest.setCustomerName(
                        allocation.getParticipantName());

                paymentRequest.setAmount(
                        allocation.getAllocationAmount());

                paymentRequest.setProductInfo(
                        "Split bill "
                        + splitId
                        + " - "
                        + allocation.getParticipantName());

                paymentRequest.setEmail(
                        participant.getEmail().trim());

                paymentRequest.setPhone(
                        participant.getPhone().trim());

                paymentRequest.setSplitId(splitId);

                paymentRequest.setAllocationId(allocationId);

                /*
                 * Use the existing SabbPe payment service.
                 * The existing service generates the link and
                 * links the new payment to the allocation.
                 */
                Map<String, Object> paymentResult =
                        sabbPePaymentService.preparePayment(
                                paymentRequest);

                boolean linkCreated =
                        !Boolean.FALSE.equals(paymentResult.get("linkCreated"));
                String resultingPaymentStatus =
                        String.valueOf(paymentResult.get("paymentStatus"));

                if (linkCreated) {
                    result.put("status", "LINK_CREATED");
                } else if ("SUCCESS".equalsIgnoreCase(resultingPaymentStatus)) {
                    result.put("status", "ALREADY_PAID");
                } else {
                    result.put("status", "EXISTING_PAYMENT");
                }

                if (paymentResult.get("paymentUrl") != null) {
                    result.put("paymentUrl", paymentResult.get("paymentUrl"));
                }
                if (paymentResult.get("pblOrderRef") != null) {
                    result.put("pblOrderRef", paymentResult.get("pblOrderRef"));
                }
                if (paymentResult.get("paymentId") != null) {
                    result.put("paymentId", paymentResult.get("paymentId"));
                }
                if (paymentResult.get("localEmailDelivery") != null) {
                    result.put("localEmailDelivery", paymentResult.get("localEmailDelivery"));
                }

                result.put("payment", paymentResult);

            } catch (IllegalArgumentException ex) {

                result.put("status", "FAILED");
                result.put("message", ex.getMessage());

            } catch (Exception ex) {

                log.error(
                        "Payment-link initiation failed for splitId={}, allocationId={}, exceptionType={}",
                        splitId,
                        allocationId,
                        ex.getClass().getSimpleName());

                // Keep sensitive exception details out of the API response.
                result.put("status", "FAILED");
                result.put("message",
                        "Payment-link initiation failed. "
                        + "Check the backend logs.");
            }

            results.add(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("splitId", splitId);
        response.put("participantCount", results.size());
        response.put("results", results);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // 8. CONTACT VALIDATION
    // =========================================================

    private void validateParticipantContact(
            ParticipantContact participant) {

        String email = participant.getEmail();
        String phone = participant.getPhone();

        if (email == null
                || !email.trim().matches(
                "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {

            throw new IllegalArgumentException(
                    "A valid email address is required.");
        }

        if (phone == null
                || !phone.trim().matches("^\\+?[0-9]{10,15}$")) {

            throw new IllegalArgumentException(
                    "Phone must contain 10 to 15 digits, "
                    + "optionally starting with +.");
        }
    }

    // =========================================================
    // REQUEST DTO: CREATE MASTER BILL
    // =========================================================

    public static class CreateSplitRequest {

        private String splitType;
        private BigDecimal totalAmount;
        private String currencyCode;
        private String paymentMode;
        private BigDecimal platformFeePercentage;
        private String splitScope = "PERSONAL";
        private String organizationId;
        private Long businessCategoryId;
        private List<AllocationRequest> allocations;

        public String getSplitType() {
            return splitType;
        }

        public void setSplitType(String splitType) {
            this.splitType = splitType;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public String getCurrencyCode() {
            return currencyCode;
        }

        public void setCurrencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
        }

        public String getPaymentMode() {
            return paymentMode;
        }

        public void setPaymentMode(String paymentMode) {
            this.paymentMode = paymentMode;
        }

        public BigDecimal getPlatformFeePercentage() {
            return platformFeePercentage;
        }

        public void setPlatformFeePercentage(
                BigDecimal platformFeePercentage) {
            this.platformFeePercentage = platformFeePercentage;
        }

        public String getSplitScope() {
            return splitScope;
        }

        public void setSplitScope(String splitScope) {
            this.splitScope = splitScope;
        }

        public String getOrganizationId() {
            return organizationId;
        }

        public void setOrganizationId(String organizationId) {
            this.organizationId = organizationId;
        }

        public Long getBusinessCategoryId() {
            return businessCategoryId;
        }

        public void setBusinessCategoryId(Long businessCategoryId) {
            this.businessCategoryId = businessCategoryId;
        }

        public List<AllocationRequest> getAllocations() {
            return allocations;
        }

        public void setAllocations(
                List<AllocationRequest> allocations) {
            this.allocations = allocations;
        }
    }

    // =========================================================
    // REQUEST DTO: ALLOCATION
    // =========================================================

    public static class AllocationRequest {

        private String participantName;
        private String participantType;
        private BigDecimal allocationAmount;
        private BigDecimal allocationPercentage;

        public String getParticipantName() {
            return participantName;
        }

        public void setParticipantName(String participantName) {
            this.participantName = participantName;
        }

        public String getParticipantType() {
            return participantType;
        }

        public void setParticipantType(String participantType) {
            this.participantType = participantType;
        }

        public BigDecimal getAllocationAmount() {
            return allocationAmount;
        }

        public void setAllocationAmount(
                BigDecimal allocationAmount) {
            this.allocationAmount = allocationAmount;
        }

        public BigDecimal getAllocationPercentage() {
            return allocationPercentage;
        }

        public void setAllocationPercentage(
                BigDecimal allocationPercentage) {
            this.allocationPercentage = allocationPercentage;
        }
    }

    // =========================================================
    // REQUEST DTO: BULK PAYMENT LINKS
    // =========================================================

    public static class BulkPaymentLinksRequest {

        private List<ParticipantContact> participants;

        public List<ParticipantContact> getParticipants() {
            return participants;
        }

        public void setParticipants(
                List<ParticipantContact> participants) {
            this.participants = participants;
        }
    }

    // =========================================================
    // REQUEST DTO: PARTICIPANT CONTACT DETAILS
    // =========================================================

    public static class ParticipantContact {

        private Long allocationId;
        private String email;
        private String phone;

        public Long getAllocationId() {
            return allocationId;
        }

        public void setAllocationId(Long allocationId) {
            this.allocationId = allocationId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    // =========================================================
    // RESPONSE DTO: MASTER BILL AND ALLOCATIONS
    // =========================================================

    public static class CreateSplitResponse {

        private final PaymentSplit split;
        private final List<PaymentSplitAllocation> allocations;

        public CreateSplitResponse(
                PaymentSplit split,
                List<PaymentSplitAllocation> allocations) {

            this.split = split;
            this.allocations = allocations;
        }

        public PaymentSplit getSplit() {
            return split;
        }

        public List<PaymentSplitAllocation> getAllocations() {
            return allocations;
        }

        public Long getId() {
            return split != null ? split.getId() : null;
        }

        public String getSplitType() {
            return split != null ? split.getSplitType() : null;
        }

        public BigDecimal getTotalAmount() {
            return split != null ? split.getTotalAmount() : null;
        }

        public String getCurrencyCode() {
            return split != null ? split.getCurrencyCode() : null;
        }

        public String getPaymentMode() {
            return split != null ? split.getPaymentMode() : null;
        }

        public BigDecimal getPlatformFeePercentage() {
            return split != null ? split.getPlatformFeePercentage() : null;
        }

        public String getStatus() {
            return split != null ? split.getStatus() : null;
        }
    }
}
