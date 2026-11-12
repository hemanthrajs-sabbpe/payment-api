package com.example.payment_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class SabbPeGatewayPaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(SabbPeGatewayPaymentService.class);

    private static final String DEFAULT_PRODUCT_INFO = "SabbPe Universal QR Payment";
    private static final String GATEWAY_PROVIDER_NAME = "SABBPE_GATEWAY";

    @Value("${sabbpe.gateway.merchant-name:PBL SIAN}")
    private String defaultMerchantName;

    @Value("${sabbpe.gateway.merchant-email:strangerock144@gmail.com}")
    private String defaultMerchantEmail;

    @Value("${sabbpe.gateway.merchant-phone:9999999998}")
    private String defaultMerchantPhone;

    private final PaymentRepository paymentRepository;
    private final PaymentSplitAllocationRepository allocationRepository;
    private final PaymentSplitRepository splitRepository;
    private final SabbPePaymentService sabbPePaymentService;

    public SabbPeGatewayPaymentService(
            PaymentRepository paymentRepository,
            PaymentSplitAllocationRepository allocationRepository,
            PaymentSplitRepository splitRepository,
            SabbPePaymentService sabbPePaymentService) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.splitRepository = splitRepository;
        this.sabbPePaymentService = sabbPePaymentService;
    }

    @Transactional
    public SabbPeGatewayPaymentResponse initiateGatewayPayment(SabbPeGatewayPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request cannot be null");
        }

        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        String customerName = resolveValue(request.getCustomerName(), defaultMerchantName, "Customer");
        String customerEmail = resolveValue(request.getCustomerEmail(), defaultMerchantEmail, "strangerock144@gmail.com");
        String customerPhone = resolveValue(request.getCustomerPhone(), defaultMerchantPhone, "9999999998");
        String productInfo = resolveValue(request.getProductInfo(), DEFAULT_PRODUCT_INFO, DEFAULT_PRODUCT_INFO);

        String phoneDigits = customerPhone.replaceAll("\\D", "");
        if (phoneDigits.length() == 12 && phoneDigits.startsWith("91")) {
            phoneDigits = phoneDigits.substring(2);
        } else if (phoneDigits.length() != 10) {
            phoneDigits = "9999999998";
        }

        log.info("Initiating authentic SabbPe payment link for amount: {}", request.getAmount());

        try {
            SabbPePaymentRequest pblRequest = new SabbPePaymentRequest();
            pblRequest.setAmount(BigDecimal.valueOf(request.getAmount()));
            pblRequest.setCustomerName(customerName);
            pblRequest.setEmail(customerEmail);
            pblRequest.setPhone(phoneDigits);
            pblRequest.setProductInfo(productInfo);

            Map<String, Object> pblResult = sabbPePaymentService.preparePayment(pblRequest);

            Long createdId = ((Number) pblResult.get("paymentId")).longValue();
            Payment createdPayment = paymentRepository.findById(createdId)
                    .orElseThrow(() -> new IllegalStateException("Payment not found after initiation"));

            createdPayment.setGatewayProvider(GATEWAY_PROVIDER_NAME);
            paymentRepository.save(createdPayment);

            String merchantOrderRef = createdPayment.getMerchantOrderRef();
            String upiUri = String.format(
                    Locale.US,
                    "upi://pay?pa=sabbpeuat@fblpay&pn=SABBPE&mc=7322&tr=%s&am=%.2f&cu=INR&tn=%s",
                    URLEncoder.encode(merchantOrderRef, StandardCharsets.UTF_8),
                    createdPayment.getAmount(),
                    URLEncoder.encode("SABBPE Payment", StandardCharsets.UTF_8)
            );

            SabbPeGatewayPaymentResponse resp = new SabbPeGatewayPaymentResponse(
                    createdPayment.getId(),
                    createdPayment.getMerchantOrderRef(),
                    createdPayment.getPaymentUrl(),
                    createdPayment.getAmount(),
                    createdPayment.getStatus(),
                    createdPayment.getPblOrderRef()
            );
            resp.setUpiQrString(upiUri);
            resp.setVpa("sabbpeuat@fblpay");
            resp.setMcc("7322");

            log.info("Successfully created authentic SabbPe payment #{} with URL: {}", createdPayment.getId(), createdPayment.getPaymentUrl());
            return resp;

        } catch (Exception ex) {
            log.error("Failed to generate authentic SabbPe payment: {}", ex.getMessage(), ex);
            throw new SabbPeIntegrationException("Failed to initiate SabbPe payment: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public SabbPeGatewayPaymentResponse initiateGatewayPaymentForAllocation(
            Long splitId, Long allocationId, String customerEmail, String customerPhone) {

        if (splitId == null || allocationId == null) {
            throw new IllegalArgumentException("splitId and allocationId are required");
        }

        PaymentSplitAllocation allocation = allocationRepository.findByIdAndSplitId(allocationId, splitId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation #" + allocationId + " not found for split #" + splitId));

        if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
            throw new IllegalStateException("Allocation #" + allocationId + " is already paid");
        }

        if (allocation.getPaymentId() != null) {
            Optional<Payment> existingPaymentOpt = paymentRepository.findById(allocation.getPaymentId());
            if (existingPaymentOpt.isPresent()) {
                Payment existingPayment = existingPaymentOpt.get();
                if ("PENDING".equalsIgnoreCase(existingPayment.getStatus())
                        && existingPayment.getPaymentUrl() != null
                        && !existingPayment.getPaymentUrl().trim().isEmpty()) {
                    log.info("Reusing existing pending payment #{} for allocation #{}", existingPayment.getId(), allocationId);
                    
                    String merchantOrderRef = existingPayment.getMerchantOrderRef();
                    String upiUri = String.format(
                            Locale.US,
                            "upi://pay?pa=sabbpeuat@fblpay&pn=SABBPE&mc=7322&tr=%s&am=%.2f&cu=INR&tn=%s",
                            URLEncoder.encode(merchantOrderRef, StandardCharsets.UTF_8),
                            existingPayment.getAmount(),
                            URLEncoder.encode("SABBPE Payment", StandardCharsets.UTF_8)
                    );

                    SabbPeGatewayPaymentResponse resp = new SabbPeGatewayPaymentResponse(
                            existingPayment.getId(),
                            existingPayment.getMerchantOrderRef(),
                            existingPayment.getPaymentUrl(),
                            existingPayment.getAmount(),
                            existingPayment.getStatus(),
                            existingPayment.getTransactionId()
                    );
                    resp.setUpiQrString(upiUri);
                    resp.setVpa("sabbpeuat@fblpay");
                    resp.setMcc("7322");
                    return resp;
                }
            }
        }

        double amount = allocation.getAllocationAmount().doubleValue();
        String customerName = allocation.getParticipantName();
        String productInfo = "Split #" + splitId + " Allocation #" + allocationId + " - " + customerName;

        SabbPeGatewayPaymentRequest request = new SabbPeGatewayPaymentRequest();
        request.setAmount(amount);
        request.setCustomerName(customerName);
        request.setCustomerEmail(customerEmail);
        request.setCustomerPhone(customerPhone);
        request.setProductInfo(productInfo);

        SabbPeGatewayPaymentResponse response = initiateGatewayPayment(request);

        allocation.setPaymentId(response.getPaymentId());
        allocationRepository.save(allocation);
        log.info("Linked payment #{} to allocation #{} for split #{}", response.getPaymentId(), allocationId, splitId);

        return response;
    }

    private String resolveValue(String primary, String secondary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.trim().isEmpty()) {
            return secondary.trim();
        }
        return fallback;
    }
}
