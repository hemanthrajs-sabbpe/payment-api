package com.example.payment_api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sabbpe/paybylink")
public class SabbPePayByLinkController {

    private final SabbPePaymentService sabbPePaymentService;
    private final PaymentSplitService paymentSplitService;
    private final PaymentSplitAllocationRepository allocationRepository;

    public SabbPePayByLinkController(
            SabbPePaymentService sabbPePaymentService,
            PaymentSplitService paymentSplitService,
            PaymentSplitAllocationRepository allocationRepository) {
        this.sabbPePaymentService = sabbPePaymentService;
        this.paymentSplitService = paymentSplitService;
        this.allocationRepository = allocationRepository;
    }

    /**
     * 1. Standalone SabbPe Pay By Link generation.
     * POST /api/sabbpe/paybylink/payments
     */
    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> createStandalonePayment(
            @Valid @RequestBody SabbPePaymentRequest request) {

        if (request.getSplitId() != null || request.getAllocationId() != null) {
            throw new IllegalArgumentException(
                    "Split or allocation IDs must not be provided for standalone payments.");
        }

        Map<String, Object> response = sabbPePaymentService.preparePayment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Split PBL - Generate links for all participants in a split.
     * POST /api/sabbpe/paybylink/splits/{splitId}/payment-links
     */
    @PostMapping("/splits/{splitId}/payment-links")
    public ResponseEntity<Map<String, Object>> initiateBulkPaymentLinks(
            @PathVariable Long splitId,
            @RequestBody PaymentSplitController.BulkPaymentLinksRequest request) {

        if (request == null
                || request.getParticipants() == null
                || request.getParticipants().isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required.");
        }

        paymentSplitService.authorizeSplitAccess(splitId);
        List<PaymentSplitAllocation> allocations =
                paymentSplitService.getAllocationsForCurrentUser(splitId);

        List<Map<String, Object>> results = new ArrayList<>();
        Set<Long> processedAllocationIds = new HashSet<>();

        for (PaymentSplitController.ParticipantContact participant : request.getParticipants()) {
            Map<String, Object> result = new LinkedHashMap<>();

            if (participant == null || participant.getAllocationId() == null) {
                result.put("status", "FAILED");
                result.put("message", "Allocation ID is required.");
                results.add(result);
                continue;
            }

            Long allocationId = participant.getAllocationId();
            result.put("allocationId", allocationId);

            if (!processedAllocationIds.add(allocationId)) {
                result.put("status", "FAILED");
                result.put("message", "Duplicate allocation ID in this request.");
                results.add(result);
                continue;
            }

            PaymentSplitAllocation allocation = allocations.stream()
                    .filter(item -> allocationId.equals(item.getId()))
                    .findFirst()
                    .orElse(null);

            if (allocation == null) {
                result.put("status", "FAILED");
                result.put("message", "Allocation does not belong to this split.");
                results.add(result);
                continue;
            }

            result.put("participantName", allocation.getParticipantName());
            result.put("amount", allocation.getAllocationAmount());

            if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
                result.put("status", "ALREADY_PAID");
                result.put("message", "This allocation is already marked successful.");
                results.add(result);
                continue;
            }

            try {
                validateParticipantContact(participant);

                SabbPePaymentRequest paymentRequest = new SabbPePaymentRequest();
                paymentRequest.setCustomerName(allocation.getParticipantName());
                paymentRequest.setAmount(allocation.getAllocationAmount());
                paymentRequest.setProductInfo("Split bill " + splitId + " - " + allocation.getParticipantName());
                paymentRequest.setEmail(participant.getEmail().trim());
                paymentRequest.setPhone(participant.getPhone().trim());
                paymentRequest.setSplitId(splitId);
                paymentRequest.setAllocationId(allocationId);

                Map<String, Object> paymentResult = sabbPePaymentService.preparePayment(paymentRequest);

                boolean linkCreated = !Boolean.FALSE.equals(paymentResult.get("linkCreated"));
                String resultingPaymentStatus = String.valueOf(paymentResult.get("paymentStatus"));

                if (linkCreated) {
                    result.put("status", "LINK_CREATED");
                } else if ("SUCCESS".equalsIgnoreCase(resultingPaymentStatus)) {
                    result.put("status", "ALREADY_PAID");
                } else {
                    result.put("status", "EXISTING_PAYMENT");
                }

                result.put("paymentId", paymentResult.get("paymentId"));
                result.put("email", participant.getEmail().trim());
                result.put("phone", participant.getPhone().trim());
                result.put("pblOrderRef", paymentResult.get("pblOrderRef"));
                result.put("linkStatus", paymentResult.get("linkStatus"));
                result.put("emailDeliveryStatus", paymentResult.get("emailDeliveryStatus"));
                result.put("emailDeliveryDetail", paymentResult.get("emailDeliveryDetail"));
                result.put("paymentStatus", paymentResult.get("paymentStatus"));
                result.put("payment", paymentResult);

            } catch (IllegalArgumentException ex) {
                result.put("status", "FAILED");
                result.put("message", ex.getMessage());
            } catch (Exception ex) {
                result.put("status", "FAILED");
                result.put("message", "Payment-link initiation failed. Check the backend logs.");
            }

            results.add(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("splitId", splitId);
        response.put("participantCount", results.size());
        response.put("results", results);

        return ResponseEntity.ok(response);
    }

    /**
     * 3. Split PBL - Generate / regenerate an individual participant link.
     * POST /api/sabbpe/paybylink/splits/{splitId}/allocations/{allocationId}/payments
     */
    @PostMapping("/splits/{splitId}/allocations/{allocationId}/payments")
    public ResponseEntity<Map<String, Object>> initiateIndividualParticipantLink(
            @PathVariable Long splitId,
            @PathVariable Long allocationId,
            @RequestBody(required = false) ParticipantContactUpdateRequest contact) {

        paymentSplitService.authorizeSplitAccess(splitId);

        List<PaymentSplitAllocation> allocations =
                paymentSplitService.getAllocationsForCurrentUser(splitId);

        PaymentSplitAllocation allocation = allocations.stream()
                .filter(item -> allocationId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Allocation #" + allocationId + " does not belong to split #" + splitId));

        if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "ALREADY_PAID",
                    "allocationId", allocationId,
                    "message", "This allocation is already marked SUCCESS. Regeneration is blocked."
            ));
        }

        String email = contact != null && contact.getEmail() != null ? contact.getEmail().trim() : null;
        String phone = contact != null && contact.getPhone() != null ? contact.getPhone().trim() : null;

        if (email == null || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("A valid email address is required for this participant.");
        }

        if (phone == null || !phone.matches("^\\+?[0-9]{10,15}$")) {
            throw new IllegalArgumentException("A valid mobile number is required for this participant.");
        }

        SabbPePaymentRequest paymentRequest = new SabbPePaymentRequest();
        paymentRequest.setCustomerName(allocation.getParticipantName());
        paymentRequest.setAmount(allocation.getAllocationAmount());
        paymentRequest.setProductInfo("Split bill " + splitId + " - " + allocation.getParticipantName());
        paymentRequest.setEmail(email);
        paymentRequest.setPhone(phone);
        paymentRequest.setSplitId(splitId);
        paymentRequest.setAllocationId(allocationId);

        Map<String, Object> paymentResult = sabbPePaymentService.preparePayment(paymentRequest);
        return ResponseEntity.ok(paymentResult);
    }

    private void validateParticipantContact(PaymentSplitController.ParticipantContact participant) {
        String email = participant.getEmail();
        String phone = participant.getPhone();

        if (email == null || !email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("A valid email address is required.");
        }

        if (phone == null || !phone.trim().matches("^\\+?[0-9]{10,15}$")) {
            throw new IllegalArgumentException("Phone must contain 10 to 15 digits, optionally starting with +.");
        }
    }

    public static class ParticipantContactUpdateRequest {
        private String email;
        private String phone;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }
}
