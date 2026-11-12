package com.example.payment_api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentSplitService {

    private final PaymentSplitRepository splitRepository;
    private final PaymentSplitAllocationRepository allocationRepository;
    private final PaymentRepository paymentRepository;
    private final SabbPeStatusService sabbPeStatusService;
    private final CurrentUserService currentUserService;
    private final BusinessMemberRepository businessMemberRepository;
    private final BusinessCategoryRepository businessCategoryRepository;

    public PaymentSplitService(
            PaymentSplitRepository splitRepository,
            PaymentSplitAllocationRepository allocationRepository,
            PaymentRepository paymentRepository,
            SabbPeStatusService sabbPeStatusService,
            CurrentUserService currentUserService,
            BusinessMemberRepository businessMemberRepository,
            BusinessCategoryRepository businessCategoryRepository) {
        this.splitRepository = splitRepository;
        this.allocationRepository = allocationRepository;
        this.paymentRepository = paymentRepository;
        this.sabbPeStatusService = sabbPeStatusService;
        this.currentUserService = currentUserService;
        this.businessMemberRepository = businessMemberRepository;
        this.businessCategoryRepository = businessCategoryRepository;
    }

    /**
     * Backward-compatible creation method. Existing callers create PERSONAL splits.
     */
    @Transactional
    public PaymentSplit createSplit(
            String splitType,
            BigDecimal totalAmount,
            String currencyCode,
            String paymentMode,
            BigDecimal platformFeePercentage) {
        return createSplit(
                splitType, totalAmount, currencyCode, paymentMode,
                platformFeePercentage, "PERSONAL", null, null);
    }

    /**
     * Backward-compatible overload for existing callers.
     */
    @Transactional
    public PaymentSplit createSplit(
            String splitType,
            BigDecimal totalAmount,
            String currencyCode,
            String paymentMode,
            BigDecimal platformFeePercentage,
            String splitScope,
            String organizationId) {
        return createSplit(
                splitType, totalAmount, currencyCode, paymentMode,
                platformFeePercentage, splitScope, organizationId, null);
    }

    /**
     * Creates a PERSONAL or BUSINESS split for the authenticated user.
     * BUSINESS creation requires an ACTIVE OWNER, ADMIN, or MANAGER membership
     * and an ACTIVE business category.
     */
    @Transactional
    public PaymentSplit createSplit(
            String splitType,
            BigDecimal totalAmount,
            String currencyCode,
            String paymentMode,
            BigDecimal platformFeePercentage,
            String splitScope,
            String organizationId,
            Long businessCategoryId) {

        Long currentUserId = currentUserService.getCurrentUserId();
        String normalizedScope = splitScope == null
                ? "PERSONAL" : splitScope.trim().toUpperCase(Locale.ROOT);

        if ("PERSONAL".equals(normalizedScope)) {
            if (organizationId != null && !organizationId.isBlank()) {
                throw new IllegalArgumentException(
                        "Personal splits must not be associated with an organization");
            }
            if (businessCategoryId != null) {
                throw new IllegalArgumentException(
                        "Personal splits must not have a business category");
            }
            organizationId = null;
        } else if ("BUSINESS".equals(normalizedScope)) {
            if (organizationId == null || organizationId.isBlank()) {
                throw new IllegalArgumentException(
                        "Organization ID is required for a business split");
            }
            requireBusinessSplitPermission(organizationId, currentUserId);
            if (businessCategoryId == null) {
                throw new IllegalArgumentException(
                        "Business category is required for a business split");
            }
            BusinessCategory category = businessCategoryRepository
                    .findById(businessCategoryId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Business category not found: " + businessCategoryId));
            if (!"ACTIVE".equalsIgnoreCase(category.getStatus())) {
                throw new IllegalArgumentException(
                        "Selected business category is not active");
            }
        } else {
            throw new IllegalArgumentException(
                    "Split scope must be PERSONAL or BUSINESS");
        }

        if (splitType == null || splitType.isBlank()) {
            splitType = "SECTOR_SPLIT";
        }

        if (totalAmount == null ||
                totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Total amount must be greater than zero");
        }

        if (paymentMode == null ||
                !(paymentMode.equals("INDIVIDUAL")
                        || paymentMode.equals("ONE_PAYS")
                        || paymentMode.equals("PERCENTAGE")
                        || paymentMode.equals("EQUAL")
                        || paymentMode.equals("CUSTOM")
                        || paymentMode.equals("ITEM_BASED")
                        || paymentMode.equals("MIXED"))) {
            throw new IllegalArgumentException(
                    "Payment mode must be EQUAL, CUSTOM, PERCENTAGE, ONE_PAYS, ITEM_BASED, MIXED, or INDIVIDUAL");
        }

        if ("PERCENTAGE".equals(paymentMode)) {
            // Both merchant and platform allocations must be positive.
            if (platformFeePercentage == null
                    || platformFeePercentage.compareTo(BigDecimal.ZERO) <= 0
                    || platformFeePercentage.compareTo(
                            new BigDecimal("100")) >= 0) {
                throw new IllegalArgumentException(
                        "Platform fee percentage must be greater than 0 and less than 100");
            }
        }

        BigDecimal normalizedTotal =
                totalAmount.setScale(2, RoundingMode.HALF_UP);

        PaymentSplit split = new PaymentSplit();
        split.setCreatedByUserId(currentUserId);
        split.setSplitScope(normalizedScope);
        split.setOrganizationId(organizationId);
        split.setBusinessCategoryId(businessCategoryId);
        split.setSplitReference(UUID.randomUUID().toString());
        split.setSplitType(splitType);
        split.setTotalAmount(normalizedTotal);
        split.setCurrencyCode(
                currencyCode == null || currencyCode.isBlank()
                        ? "INR" : currencyCode.toUpperCase());
        split.setPaymentMode(paymentMode);
        split.setPlatformFeePercentage(platformFeePercentage);
        split.setStatus("CREATED");

        // Save the split first so its generated ID can be used by allocations.
        PaymentSplit savedSplit = splitRepository.save(split);

        // Percentage mode automatically creates merchant and platform allocations.
        // INDIVIDUAL and ONE_PAYS retain their existing behavior.
        if ("PERCENTAGE".equals(paymentMode)) {
            BigDecimal feePercentage =
                    platformFeePercentage.setScale(2, RoundingMode.HALF_UP);
            BigDecimal merchantPercentage =
                    new BigDecimal("100.00").subtract(feePercentage);

            BigDecimal platformAmount = normalizedTotal
                    .multiply(feePercentage)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal merchantAmount = normalizedTotal.subtract(platformAmount);

            PaymentSplitAllocation merchant = new PaymentSplitAllocation();
            merchant.setSplitId(savedSplit.getId());
            merchant.setParticipantName("Merchant");
            merchant.setParticipantType("MERCHANT");
            merchant.setAllocationAmount(merchantAmount);
            merchant.setAllocationPercentage(merchantPercentage);
            merchant.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            merchant.setStatus("PENDING");

            PaymentSplitAllocation platform = new PaymentSplitAllocation();
            platform.setSplitId(savedSplit.getId());
            platform.setParticipantName("Platform Fee");
            platform.setParticipantType("PLATFORM");
            platform.setAllocationAmount(platformAmount);
            platform.setAllocationPercentage(feePercentage);
            platform.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            platform.setStatus("PENDING");

            allocationRepository.save(merchant);
            allocationRepository.save(platform);
        }

        return savedSplit;
    }

    /** Returns only splits owned by the current user or accessible through active business membership. */
    @Transactional(readOnly = true)
public List<PaymentSplit> getAllSplits() {

    Long userId = currentUserService.getCurrentUserId();

    Map<Long, PaymentSplit> visibleSplits = new LinkedHashMap<>();

    // Include only PERSONAL splits created by the current user.
    for (PaymentSplit split : splitRepository.findByCreatedByUserId(userId)) {

        String scope = split.getSplitScope() == null
                ? "PERSONAL"
                : split.getSplitScope().trim().toUpperCase(Locale.ROOT);

        if ("PERSONAL".equals(scope)) {
            visibleSplits.put(split.getId(), split);
        }
    }

    // Include BUSINESS splits only through active authorized memberships.
    List<BusinessMember> memberships =
            businessMemberRepository.findByUserIdAndMembershipStatus(
                    userId, "ACTIVE");

    for (BusinessMember membership : memberships) {

        if (!hasSplitManagementRole(membership.getMemberRole())) {
            continue;
        }

        List<PaymentSplit> businessSplits =
                splitRepository.findByOrganizationId(
                        membership.getOrganizationId());

        for (PaymentSplit split : businessSplits) {

            if ("BUSINESS".equalsIgnoreCase(split.getSplitScope())) {
                visibleSplits.put(split.getId(), split);
            }
        }
    }

    return new ArrayList<>(visibleSplits.values());
}
    @Transactional(readOnly = true)
    public PaymentSplit getSplitById(Long id) {
        return splitRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Split not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PaymentSplitAllocation> getAllocations(Long splitId) {
        getSplitById(splitId);
        return allocationRepository.findBySplitId(splitId);
    }

    /** User-facing lookup. Use this from authenticated controller routes. */
    @Transactional(readOnly = true)
    public PaymentSplit getSplitByIdForCurrentUser(Long splitId) {
        PaymentSplit split = getSplitById(splitId);
        authorizeSplitAccess(split);
        return split;
    }

    /** User-facing allocation lookup. */
    @Transactional(readOnly = true)
    public List<PaymentSplitAllocation> getAllocationsForCurrentUser(Long splitId) {
        getSplitByIdForCurrentUser(splitId);
        return allocationRepository.findBySplitId(splitId);
    }

    /**
     * Checks whether the logged-in user may manage this split.
     * PERSONAL: creator only. BUSINESS: active OWNER, ADMIN, or MANAGER membership.
     */
    @Transactional(readOnly = true)
    public void authorizeSplitAccess(Long splitId) {
        authorizeSplitAccess(getSplitById(splitId));
    }

    private void authorizeSplitAccess(PaymentSplit split) {
        Long userId = currentUserService.getCurrentUserId();
        String scope = split.getSplitScope() == null
                ? "PERSONAL" : split.getSplitScope().trim().toUpperCase(Locale.ROOT);

        if ("PERSONAL".equals(scope)) {
            if (split.getCreatedByUserId() == null
                    || !split.getCreatedByUserId().equals(userId)) {
                throw new SecurityException("You are not authorized to access this personal split");
            }
            return;
        }

        if ("BUSINESS".equals(scope)) {
            if (split.getOrganizationId() == null || split.getOrganizationId().isBlank()) {
                throw new SecurityException("Business split has no organization association");
            }
            requireBusinessSplitPermission(split.getOrganizationId(), userId);
            return;
        }

        throw new SecurityException("Unsupported split scope");
    }

    private void requireBusinessSplitPermission(String organizationId, Long userId) {
        BusinessMember membership = businessMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new SecurityException(
                        "You are not a member of this organization"));

        if (!"ACTIVE".equalsIgnoreCase(membership.getMembershipStatus())) {
            throw new SecurityException("Your organization membership is not active");
        }
        if (!hasSplitManagementRole(membership.getMemberRole())) {
            throw new SecurityException(
                    "Only OWNER, ADMIN, or MANAGER members can manage business splits");
        }
    }

    private boolean hasSplitManagementRole(String role) {
        if (role == null) {
            return false;
        }
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        return "OWNER".equals(normalizedRole)
                || "ADMIN".equals(normalizedRole)
                || "MANAGER".equals(normalizedRole);
    }

    @Transactional
    public PaymentSplitAllocation addAllocation(
            Long splitId,
            String participantName,
            String participantType,
            BigDecimal allocationAmount,
            BigDecimal allocationPercentage) {

        PaymentSplit split = getSplitByIdForCurrentUser(splitId);

        if ("PAID".equalsIgnoreCase(split.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot add allocations to a fully paid split");
        }

        if (participantName == null || participantName.isBlank()) {
            throw new IllegalArgumentException(
                    "Participant name is required");
        }

        if (participantType == null ||
                !(participantType.equals("FRIEND")
                        || participantType.equals("MERCHANT")
                        || participantType.equals("PLATFORM"))) {
            throw new IllegalArgumentException(
                    "Participant type must be FRIEND, MERCHANT, or PLATFORM");
        }

        if (allocationAmount == null
                || allocationAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Allocation amount must be greater than zero");
        }

        if (allocationPercentage != null
                && (allocationPercentage.compareTo(BigDecimal.ZERO) < 0
                || allocationPercentage.compareTo(
                        new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException(
                    "Allocation percentage must be between 0 and 100");
        }

        PaymentSplitAllocation allocation = new PaymentSplitAllocation();
        allocation.setSplitId(splitId);
        allocation.setParticipantName(participantName.trim());
        allocation.setParticipantType(participantType);
        allocation.setAllocationAmount(
                allocationAmount.setScale(2, RoundingMode.HALF_UP));
        allocation.setAllocationPercentage(allocationPercentage);
        allocation.setPaidAmount(BigDecimal.ZERO);
        allocation.setStatus("PENDING");

        return allocationRepository.save(allocation);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateSplit(Long splitId) {

        PaymentSplit split = getSplitByIdForCurrentUser(splitId);

        List<PaymentSplitAllocation> allocations =
                allocationRepository.findBySplitId(splitId);

        BigDecimal allocatedAmount = allocations.stream()
                .map(PaymentSplitAllocation::getAllocationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = split.getTotalAmount();

        BigDecimal remainingAmount = totalAmount
                .subtract(allocatedAmount)
                .setScale(2, RoundingMode.HALF_UP);

        boolean valid =
                allocatedAmount.compareTo(totalAmount) == 0;

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("splitId", splitId);
        result.put("totalAmount", totalAmount);
        result.put("allocatedAmount", allocatedAmount);
        result.put("remainingAmount", remainingAmount);
        result.put("allocationCount", allocations.size());
        result.put("valid", valid);
        result.put("message", valid
                ? "All allocations match the total bill"
                : "Allocations do not match the total bill");

        return result;
    }

    /**
     * Associates a newly created payment with an allocation.
     * The allocation remains PENDING until verified payment confirmation.
     */
    @Transactional
    public PaymentSplitAllocation linkPendingPayment(
            Long splitId,
            Long allocationId,
            Long paymentId) {

        PaymentSplit split = getSplitById(splitId);

        PaymentSplitAllocation allocation =
                allocationRepository.findById(allocationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Allocation not found: " + allocationId));

        if (!allocation.getSplitId().equals(splitId)) {
            throw new IllegalArgumentException(
                    "Allocation does not belong to this split");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId));

        if (!"PENDING".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a pending payment can be linked");
        }

        // A payment must not be linked to more than one allocation in this split.
        // Otherwise one successful transaction could incorrectly pay multiple shares.
        boolean paymentLinkedToAnotherAllocation =
                allocationRepository.findBySplitId(splitId).stream()
                        .anyMatch(existing ->
                                !existing.getId().equals(allocationId)
                                        && paymentId.equals(existing.getPaymentId()));

        if (paymentLinkedToAnotherAllocation) {
            throw new IllegalArgumentException(
                    "This payment is already linked to another allocation in this split");
        }

        if (payment.getAmount() == null) {
            throw new IllegalArgumentException(
                    "Payment amount is missing");
        }

        BigDecimal paymentAmount = BigDecimal.valueOf(payment.getAmount())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal allocationAmount = allocation.getAllocationAmount()
                .setScale(2, RoundingMode.HALF_UP);

        if (paymentAmount.compareTo(allocationAmount) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount does not match allocation amount");
        }

        if ("PAID".equalsIgnoreCase(split.getStatus())) {
            throw new IllegalArgumentException(
                    "This split is already fully paid");
        }

        if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
            throw new IllegalArgumentException(
                    "This allocation has already been paid");
        }

        if (allocation.getPaymentId() != null
                && !allocation.getPaymentId().equals(paymentId)) {

            Payment previousPayment = paymentRepository
                    .findById(allocation.getPaymentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "The allocation references a missing payment. "
                                    + "Linking another payment is blocked."));

            String previousLocalStatus = previousPayment.getStatus() == null
                    ? ""
                    : previousPayment.getStatus().trim().toUpperCase(Locale.ROOT);

            if ("SUCCESS".equals(previousLocalStatus)) {
                return recordSuccessfulPayment(
                        splitId, allocationId, previousPayment.getId());
            }

            if ("REFUNDED".equals(previousLocalStatus)) {
                throw new IllegalArgumentException(
                        "The previous payment is REFUNDED. A replacement cannot be linked.");
            }

            // Only query SabbPe PBL status if the previous payment is a genuine SabbPe PBL payment
            if (previousPayment.getPblOrderRef() == null
                    || previousPayment.getPblOrderRef().isBlank()
                    || (previousPayment.getGatewayProvider() != null && !"SABBPE_PBL".equalsIgnoreCase(previousPayment.getGatewayProvider()))) {
                // If previous payment had no PBL order reference or was created by a different provider,
                // do not call SabbPe PBL status API. Link the new payment safely.
                allocation.setPaymentId(paymentId);
                allocation.setPaidAmount(BigDecimal.ZERO);
                allocation.setStatus("PENDING");
                return allocationRepository.save(allocation);
            }

            SabbPeStatusService.SabbPePaymentStatusResponse previousGateway =
                    sabbPeStatusService.getVerifiedPaymentStatus(previousPayment);

            // Fail closed when the provider cannot supply a verifiable status.
            // A replacement payment must never be linked while the previous
            // payment's final state is unknown.
            if (previousGateway == null
                    || previousGateway.paymentStatus() == null
                    || previousGateway.paymentStatus().isBlank()) {
                throw new IllegalStateException(
                        "SabbPe returned no verifiable payment status. "
                                + "Linking another payment is blocked for safety.");
            }

            String verifiedStatus = previousGateway.paymentStatus()
                    .trim().toUpperCase(Locale.ROOT);

            switch (verifiedStatus) {
                case "SUCCESS":
                    previousPayment.setStatus("SUCCESS");
                    if (previousGateway.masterTransactionId() != null
                            && !previousGateway.masterTransactionId().isBlank()) {
                        previousPayment.setTransactionId(
                                previousGateway.masterTransactionId().trim());
                    }
                    paymentRepository.save(previousPayment);
                    return recordSuccessfulPayment(
                            splitId, allocationId, previousPayment.getId());

                case "TIMEOUT":
                case "FAILED":
                case "CANCELLED":
                    previousPayment.setStatus(verifiedStatus);
                    paymentRepository.save(previousPayment);
                    break;

                case "PENDING":
                    throw new IllegalArgumentException(
                            "SabbPe still reports the previous payment as PENDING. "
                                    + "A replacement cannot be linked.");

                case "REFUNDED":
                    throw new IllegalArgumentException(
                            "SabbPe reports the previous payment as REFUNDED. "
                                    + "A replacement cannot be linked.");

                default:
                    throw new IllegalStateException(
                            "Linking blocked for unsupported SabbPe status: "
                                    + verifiedStatus);
            }
        }

        allocation.setPaymentId(paymentId);
        allocation.setPaidAmount(BigDecimal.ZERO);
        allocation.setStatus("PENDING");

        return allocationRepository.save(allocation);
    }

    /**
     * Records a payment only after its status has been verified as SUCCESS.
     */
    @Transactional
    public PaymentSplitAllocation recordSuccessfulPayment(
            Long splitId,
            Long allocationId,
            Long paymentId) {

        PaymentSplit split = getSplitById(splitId);

        PaymentSplitAllocation allocation =
                allocationRepository.findById(allocationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Allocation not found: " + allocationId));

        if (!allocation.getSplitId().equals(splitId)) {
            throw new IllegalArgumentException(
                    "Allocation does not belong to this split");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId));

        if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalArgumentException(
                    "Payment must have SUCCESS status");
        }

        // Guard against recording the same payment as successful for two shares.
        boolean paymentLinkedToAnotherAllocation =
                allocationRepository.findBySplitId(splitId).stream()
                        .anyMatch(existing ->
                                !existing.getId().equals(allocationId)
                                        && paymentId.equals(existing.getPaymentId()));

        if (paymentLinkedToAnotherAllocation) {
            throw new IllegalArgumentException(
                    "This payment is already linked to another allocation in this split");
        }

        if (payment.getAmount() == null) {
            throw new IllegalArgumentException(
                    "Payment amount is missing");
        }

        BigDecimal paymentAmount =
                BigDecimal.valueOf(payment.getAmount())
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expectedAmount =
                allocation.getAllocationAmount()
                        .setScale(2, RoundingMode.HALF_UP);

        if (paymentAmount.compareTo(expectedAmount) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount does not match allocation amount");
        }

        if (allocation.getPaymentId() != null
                && !allocation.getPaymentId().equals(paymentId)) {
            throw new IllegalArgumentException(
                    "Payment is not linked to this allocation");
        }

        // If this allocation is already SUCCESS for the same payment,
        // do not change it, but continue to recalculate the parent split status.
        if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
            if (!paymentId.equals(allocation.getPaymentId())) {
                throw new IllegalArgumentException(
                        "This allocation has already been paid");
            }
        } else {
            allocation.setPaymentId(paymentId);
            allocation.setPaidAmount(paymentAmount);
            allocation.setStatus("SUCCESS");
            allocationRepository.save(allocation);
        }

        // Always recalculate parent status, including repeated success processing.
        List<PaymentSplitAllocation> allocations =
                allocationRepository.findBySplitId(splitId);

        boolean allPaid = !allocations.isEmpty()
                && allocations.stream().allMatch(
                        item -> "SUCCESS".equalsIgnoreCase(item.getStatus()));

        split.setStatus(allPaid ? "PAID" : "CREATED");
        splitRepository.save(split);

        return allocation;
    }

    /**
     * Records a verified failed/cancelled payment without marking it paid.
     */
    @Transactional
    public PaymentSplitAllocation recordFailedPayment(
            Long splitId,
            Long allocationId,
            Long paymentId,
            String failureStatus) {

        getSplitById(splitId);

        PaymentSplitAllocation allocation =
                allocationRepository.findById(allocationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Allocation not found: " + allocationId));

        if (!allocation.getSplitId().equals(splitId)) {
            throw new IllegalArgumentException(
                    "Allocation does not belong to this split");
        }

        if (allocation.getPaymentId() == null
                || !allocation.getPaymentId().equals(paymentId)) {
            throw new IllegalArgumentException(
                    "Payment is not linked to this allocation");
        }

        if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
            return allocation;
        }

        if (failureStatus == null
                || !List.of("FAILED", "CANCELLED", "TIMEOUT", "REFUNDED")
                        .contains(failureStatus.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Unsupported failure status");
        }

        allocation.setStatus(failureStatus.trim().toUpperCase(Locale.ROOT));
        allocation.setPaidAmount(BigDecimal.ZERO);

        return allocationRepository.save(allocation);
    }

    // Combined master-bill and allocations creation for the simplified API.
    @Transactional
    public PaymentSplit createSplitWithAllocations(
            String splitType,
            BigDecimal totalAmount,
            String currencyCode,
            String paymentMode,
            BigDecimal platformFeePercentage,
            List<AllocationInput> requestedAllocations) {
        return createSplitWithAllocations(
                splitType, totalAmount, currencyCode, paymentMode,
                platformFeePercentage, requestedAllocations, "PERSONAL", null, null);
    }

    /**
     * Backward-compatible overload. BUSINESS calls must supply a category
     * through the overload that accepts businessCategoryId.
     */
    @Transactional
    public PaymentSplit createSplitWithAllocations(
            String splitType,
            BigDecimal totalAmount,
            String currencyCode,
            String paymentMode,
            BigDecimal platformFeePercentage,
            List<AllocationInput> requestedAllocations,
            String splitScope,
            String organizationId) {
        return createSplitWithAllocations(
                splitType, totalAmount, currencyCode, paymentMode,
                platformFeePercentage, requestedAllocations,
                splitScope, organizationId, null);
    }

    @Transactional
    public PaymentSplit createSplitWithAllocations(
            String splitType,
            BigDecimal totalAmount,
            String currencyCode,
            String paymentMode,
            BigDecimal platformFeePercentage,
            List<AllocationInput> requestedAllocations,
            String splitScope,
            String organizationId,
            Long businessCategoryId) {

        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero.");
        }
        if (paymentMode == null) {
            throw new IllegalArgumentException("Payment mode is required.");
        }

        String normalizedMode = paymentMode.trim().toUpperCase(Locale.ROOT);
        String normalizedSplitType = splitType == null || splitType.isBlank()
                ? "SECTOR_SPLIT" : splitType.trim().toUpperCase(Locale.ROOT);
        if (!"INDIVIDUAL".equals(normalizedMode)
                && !"ONE_PAYS".equals(normalizedMode)
                && !"EQUAL".equals(normalizedMode)
                && !"CUSTOM".equals(normalizedMode)
                && !"PERCENTAGE".equals(normalizedMode)
                && !"ITEM_BASED".equals(normalizedMode)
                && !"MIXED".equals(normalizedMode)) {
            throw new IllegalArgumentException(
                    "Combined allocation creation supports EQUAL, CUSTOM, PERCENTAGE, ONE_PAYS, ITEM_BASED, MIXED, or INDIVIDUAL mode.");
        }
        if (requestedAllocations == null || requestedAllocations.isEmpty()) {
            throw new IllegalArgumentException("Add at least one allocation.");
        }
        if ("ONE_PAYS".equals(normalizedMode) && requestedAllocations.size() != 1) {
            throw new IllegalArgumentException(
                    "ONE_PAYS mode requires exactly one participant allocation.");
        }

        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocationSum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (int i = 0; i < requestedAllocations.size(); i++) {
            AllocationInput input = requestedAllocations.get(i);
            if (input == null) {
                throw new IllegalArgumentException("Allocation entries cannot be null.");
            }
            if (input.participantName() == null || input.participantName().isBlank()) {
                throw new IllegalArgumentException("Participant name is required for allocation " + (i + 1) + ".");
            }
            if (input.participantType() == null) {
                throw new IllegalArgumentException("Participant type is required for allocation " + (i + 1) + ".");
            }
            String participantType = input.participantType().trim().toUpperCase(Locale.ROOT);
            if (!List.of("FRIEND", "MERCHANT", "PLATFORM").contains(participantType)) {
                throw new IllegalArgumentException("Invalid participant type for allocation " + (i + 1) + ".");
            }
            if (input.allocationAmount() == null
                    || input.allocationAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Allocation amounts must be greater than zero.");
            }
            if (input.allocationPercentage() != null
                    && (input.allocationPercentage().compareTo(BigDecimal.ZERO) < 0
                    || input.allocationPercentage().compareTo(new BigDecimal("100")) > 0)) {
                throw new IllegalArgumentException("Allocation percentage must be between 0 and 100.");
            }
            allocationSum = allocationSum.add(
                    input.allocationAmount().setScale(2, RoundingMode.HALF_UP));
        }

        if (allocationSum.compareTo(normalizedTotal) != 0) {
            throw new IllegalArgumentException(
                    "Participant allocations must equal the total bill. Allocated: "
                            + allocationSum + ", total: " + normalizedTotal + ".");
        }

        // This public method is transactional. If any allocation fails, the new
        // master split and all allocations created in this call are rolled back.
        PaymentSplit savedSplit = createSplit(
                normalizedSplitType,
                normalizedTotal,
                currencyCode,
                normalizedMode,
                platformFeePercentage,
                splitScope,
                organizationId,
                businessCategoryId
        );

        for (AllocationInput input : requestedAllocations) {
            addAllocation(
                    savedSplit.getId(),
                    input.participantName(),
                    input.participantType().trim().toUpperCase(Locale.ROOT),
                    input.allocationAmount(),
                    input.allocationPercentage()
            );
        }

        return savedSplit;
    }

    public record AllocationInput(
            String participantName,
            String participantType,
            BigDecimal allocationAmount,
            BigDecimal allocationPercentage) {
    }
}
