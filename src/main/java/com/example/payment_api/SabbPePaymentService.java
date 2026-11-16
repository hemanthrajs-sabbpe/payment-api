package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SabbPePaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(SabbPePaymentService.class);

    private final PaymentRepository paymentRepository;
    private final SabbPeTokenService sabbPeTokenService;
    private final PaymentSplitService paymentSplitService;
    private final SabbPeStatusService sabbPeStatusService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailFrom;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${sabbpe.base-url}")
    private String baseUrl;

    @Value("${sabbpe.frontend-url}")
    private String frontendUrl;

    @Value("${sabbpe.initiate-path:/sabbpe/v1/paybylink/generate}")
    private String initiatePath;

    private final PaymentReceiptTokenService receiptTokenService;

    public SabbPePaymentService(
            PaymentRepository paymentRepository,
            SabbPeTokenService sabbPeTokenService,
            PaymentSplitService paymentSplitService,
            SabbPeStatusService sabbPeStatusService,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            PaymentReceiptTokenService receiptTokenService,
            @Value("${otp.mail.from:}") String mailFrom) {

        this.paymentRepository = paymentRepository;
        this.sabbPeTokenService = sabbPeTokenService;
        this.paymentSplitService = paymentSplitService;
        this.sabbPeStatusService = sabbPeStatusService;
        this.mailSenderProvider = mailSenderProvider;
        this.receiptTokenService = receiptTokenService;
        this.mailFrom = mailFrom;
    }

    public Map<String, Object> preparePayment(
            SabbPePaymentRequest request) {

        validateConfiguration(request);

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero.");
        }

        if (isBlank(request.getCustomerName())
                || isBlank(request.getEmail())
                || isBlank(request.getPhone())) {

            throw new IllegalArgumentException(
                    "Customer name, email, and phone are required.");
        }

        /*
         * Both IDs must be provided for a split payment.
         * If neither is provided, normal SabbPe payments continue
         * to work as before.
         */
        Long splitId = request.getSplitId();
        Long allocationId = request.getAllocationId();

        boolean isSplitPayment =
                splitId != null || allocationId != null;

        if (isSplitPayment
                && (splitId == null || allocationId == null)) {

            throw new IllegalArgumentException(
                    "Both splitId and allocationId are required "
                            + "for a split payment.");
        }

        PaymentSplitAllocation allocation = null;

        if (isSplitPayment) {

            // Enforce split ownership/business membership at the service layer.
            // This remains safe even if another controller or internal caller
            // invokes preparePayment without performing its own access check.
            paymentSplitService.authorizeSplitAccess(splitId);

            PaymentSplit split =
                    paymentSplitService.getSplitByIdForCurrentUser(splitId);

            allocation = paymentSplitService
                    .getAllocationsForCurrentUser(splitId)
                    .stream()
                    .filter(item -> allocationId.equals(item.getId()))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Allocation does not belong to this split."));

            BigDecimal requestedAmount =
                    request.getAmount()
                            .setScale(2, RoundingMode.HALF_UP);

            BigDecimal allocationAmount =
                    allocation.getAllocationAmount()
                            .setScale(2, RoundingMode.HALF_UP);

            if (requestedAmount.compareTo(allocationAmount) != 0) {
                throw new IllegalArgumentException(
                        "Payment amount must match the allocation amount.");
            }

            if (!allocation.getParticipantName().trim()
                    .equalsIgnoreCase(
                            request.getCustomerName().trim())) {

                throw new IllegalArgumentException(
                        "Customer name must match the allocation participant.");
            }

            if ("PAID".equalsIgnoreCase(split.getStatus())) {
                throw new IllegalArgumentException(
                        "This split has already been fully paid.");
            }

            if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
                throw new IllegalArgumentException(
                        "This allocation has already been paid.");
            }

            if (allocation.getPaymentId() != null) {

                Payment previousPayment = paymentRepository
                        .findById(allocation.getPaymentId())
                        .orElseThrow(() -> new IllegalStateException(
                                "The allocation references a missing payment. "
                                        + "Replacement is blocked for safety."));

                String previousLocalStatus = previousPayment.getStatus() == null
                        ? ""
                        : previousPayment.getStatus().trim().toUpperCase(Locale.ROOT);

                if ("SUCCESS".equals(previousLocalStatus)) {
                    paymentSplitService.recordSuccessfulPayment(
                            splitId, allocationId, previousPayment.getId());

                    return existingPaymentResponse(
                            previousPayment, splitId, allocationId,
                            "SUCCESS", null,
                            "The existing payment is successful. No replacement link was created.");
                }

                if ("REFUNDED".equals(previousLocalStatus)) {
                    throw new IllegalArgumentException(
                            "The existing payment is REFUNDED. Replacement is blocked.");
                }

                // If previous payment was not a PBL payment (e.g. dynamic gateway QR), or lacks PBL references,
                // do not call SabbPe PBL status service; allow creating a fresh PBL link.
                if (isBlank(previousPayment.getPblOrderRef())
                        || !"SABBPE_PBL".equalsIgnoreCase(previousPayment.getGatewayProvider())) {
                    log.info("Previous payment #{} on allocation #{} is not a PBL payment (provider={}). Proceeding to create fresh PBL link.",
                            previousPayment.getId(), allocationId, previousPayment.getGatewayProvider());
                } else {
                    SabbPeStatusService.SabbPePaymentStatusResponse previousGateway =
                            sabbPeStatusService.getVerifiedPaymentStatus(previousPayment);

                    /*
                     * Fail closed if the provider status response is missing or
                     * incomplete. Never create a replacement link when the
                     * previous payment's status could not be verified.
                     */
                    if (previousGateway == null
                            || isBlank(previousGateway.paymentStatus())) {
                        throw new IllegalStateException(
                                "SabbPe returned no verifiable payment status. "
                                        + "Replacement is blocked for safety.");
                    }

                    String verifiedStatus = previousGateway.paymentStatus()
                            .trim().toUpperCase(Locale.ROOT);

                    switch (verifiedStatus) {
                        case "SUCCESS":
                            previousPayment.setStatus("SUCCESS");
                            if (!isBlank(previousGateway.masterTransactionId())) {
                                previousPayment.setTransactionId(
                                        previousGateway.masterTransactionId().trim());
                            }
                            paymentRepository.save(previousPayment);

                            paymentSplitService.recordSuccessfulPayment(
                                    splitId, allocationId, previousPayment.getId());

                            return existingPaymentResponse(
                                    previousPayment, splitId, allocationId,
                                    "SUCCESS", previousGateway.linkStatus(),
                                    "SabbPe confirms the existing payment is successful. No replacement link was created.");

                        case "PENDING":
                            previousPayment.setStatus("PENDING");
                            paymentRepository.save(previousPayment);

                            String currentLinkStatus = previousGateway.linkStatus() == null
                                    ? "UNKNOWN"
                                    : previousGateway.linkStatus().trim().toUpperCase(Locale.ROOT);

                            String pendingMessage;

                            if ("EXPIRED".equals(currentLinkStatus)) {
                                pendingMessage =
                                        "SabbPe reports that the existing link is EXPIRED, "
                                        + "but the payment status remains PENDING. "
                                        + "A replacement is blocked until the provider confirms "
                                        + "that the old link can no longer accept payment.";
                            } else if ("DELIVERY_FAILED".equals(currentLinkStatus)) {
                                pendingMessage =
                                        "SabbPe reports DELIVERY_FAILED, but the payment status "
                                        + "remains PENDING. Verify the existing payment before "
                                        + "attempting another link.";
                            } else {
                                pendingMessage =
                                        "SabbPe still reports the existing payment as PENDING "
                                        + "with link status " + currentLinkStatus
                                        + ". No replacement link was created.";
                            }

                            return existingPaymentResponse(
                                    previousPayment,
                                    splitId,
                                    allocationId,
                                    "PENDING",
                                    previousGateway.linkStatus(),
                                    pendingMessage);

                        case "REFUNDED":
                            previousPayment.setStatus("REFUNDED");
                            paymentRepository.save(previousPayment);

                            return existingPaymentResponse(
                                    previousPayment, splitId, allocationId,
                                    "REFUNDED", previousGateway.linkStatus(),
                                    "SabbPe reports the existing payment as REFUNDED. Replacement is blocked.");

                        case "TIMEOUT":
                        case "FAILED":
                        case "CANCELLED":
                            // A fresh link is considered only after SabbPe confirms
                            // a terminal, non-successful status for this exact payment.
                            previousPayment.setStatus(verifiedStatus);
                            paymentRepository.save(previousPayment);
                            break;

                        default:
                            throw new IllegalStateException(
                                    "Replacement blocked for unsupported SabbPe status: "
                                            + verifiedStatus);
                    }
                }
            }
        }

        String merchantOrderRef = "SABBPE-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();

        Payment payment = new Payment(
                request.getCustomerName(),
                request.getAmount().doubleValue(),
                "PENDING"
        );

        payment.setMerchantOrderRef(merchantOrderRef);
        payment.setGatewayProvider("SABBPE_PBL");
        payment = paymentRepository.save(payment);

        try {

            // Step 1: Generate a fresh token for this order.
            SabbPeTokenService.SabbPeTokenResponse tokenResponse =
                    sabbPeTokenService.generateToken(merchantOrderRef);

            // Step 2: Prepare customer details.
            Map<String, String> customer = new LinkedHashMap<>();

            customer.put(
                    "firstname",
                    request.getCustomerName().trim());

            customer.put(
                    "email",
                    request.getEmail().trim());

            customer.put(
                    "phone",
                    request.getPhone().trim());

            // Step 3: Prepare the Pay By Link request.
            Map<String, Object> requestBody = new LinkedHashMap<>();

            requestBody.put(
                    "sabbpe_token",
                    tokenResponse.sabbpeToken());

            requestBody.put(
                    "amount",
                    request.getAmount());

            // Easebuzz requires productinfo/description to be alphanumeric only (no '#' or special characters)
            String safeDescription = request.getProductInfo() != null
                    ? request.getProductInfo().replaceAll("[^a-zA-Z0-9 ]", " ").replaceAll("\\s+", " ").trim()
                    : "Payment";
            if (safeDescription.isBlank()) {
                safeDescription = "Payment";
            }

            requestBody.put(
                    "description",
                    safeDescription);

            // Return to this exact saved payment's result page. SabbPe may append
            // pbl_order_ref to this URL; the local payment ID lets the app load
            // the matching database record without trusting query-string status.
            String token = receiptTokenService != null ? receiptTokenService.generateReceiptToken(payment) : "";
            String tokenQuery = (token != null && !token.isBlank()) ? ("?token=" + token) : "";

            String resultUrl = frontendUrl.replaceAll("/+$", "")
                    + "/payment-result/" + payment.getId() + tokenQuery;
            requestBody.put("redirect_url", resultUrl);

            requestBody.put(
                    "customer",
                    customer);

            // Preserve the existing delivery channels.
            Map<String, Object> delivery = new LinkedHashMap<>();

            delivery.put(
                    "channels",
                    List.of("EMAIL", "WHATSAPP"));

            requestBody.put("delivery", delivery);

            // Step 4: Prepare HTTP headers.
            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(
                    List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> httpRequest =
                    new HttpEntity<>(requestBody, headers);

            String url = baseUrl.replaceAll("/+$", "")
                    + "/"
                    + initiatePath.replaceAll("^/+", "");

            // Step 5: Call the existing SabbPe API.
            ResponseEntity<SabbPePayByLinkResponse> response =
                    restTemplate.postForEntity(
                            url,
                            httpRequest,
                            SabbPePayByLinkResponse.class);

            SabbPePayByLinkResponse result = response.getBody();

            if (result == null
                    || !Boolean.TRUE.equals(result.status())
                    || isBlank(result.pblOrderRef())) {

                String message = result == null
                        ? "Empty response from SabbPe."
                        : result.message();

                throw new SabbPeIntegrationException(
                        "SabbPe Pay By Link generation failed. "
                                + (isBlank(message)
                                    ? "Check the UAT response and merchant configuration."
                                    : "Provider message: " + safeMessage(message)));
            }

            // Step 6: Save the payment-link reference.
            payment.setPblOrderRef(result.pblOrderRef());
            payment.setGatewayProvider("SABBPE_PBL");

            String effectiveUrl = resolvePaymentUrl(result);
            if (!isBlank(effectiveUrl)) {
                payment.setPaymentUrl(effectiveUrl);
            }
            payment.setStatus("PENDING");

            payment = paymentRepository.save(payment);

            // Step 6b: Deliver local payment link notification email if SMTP sender is available.
            // As per official SabbPe PBL specification, the customer checkout link is delivered by SabbPe
            // via Email/WhatsApp. If notification delivery fails (HTTP 503), our local SMTP email informs
            // the customer of the acknowledged PBL reference and order details without fabricating a URL.
            String localEmailStatus = "NOT_ATTEMPTED";
            if (!isBlank(request.getEmail()) && (!isBlank(payment.getPaymentUrl()) || !isBlank(result.pblOrderRef()))) {
                String trackingUrl = frontendUrl.replaceAll("/+$", "") + "/payment-result/" + payment.getId() + tokenQuery;
                boolean sent = sendPaymentLinkEmailSafely(
                        request.getEmail().trim(),
                        request.getCustomerName().trim(),
                        request.getAmount(),
                        merchantOrderRef,
                        result.pblOrderRef(),
                        payment.getPaymentUrl(),
                        trackingUrl
                );
                localEmailStatus = sent ? "SMTP_ACCEPTED" : "FAILED";
            }

            /*
             * Step 7: Link the generated payment to the allocation.
             * The allocation remains PENDING until the callback
             * independently verifies the payment with SabbPe.
             */
            if (isSplitPayment) {

                paymentSplitService.linkPendingPayment(
                        splitId,
                        allocationId,
                        payment.getId());
            }

            // Step 8: Return the response.
            Map<String, Object> apiResponse =
                    new LinkedHashMap<>();

            apiResponse.put("linkCreated", true);

            apiResponse.put(
                    "paymentId",
                    payment.getId());

            apiResponse.put(
                    "merchantOrderRef",
                    merchantOrderRef);

            apiResponse.put(
                    "pblOrderRef",
                    result.pblOrderRef());

            apiResponse.put(
                    "amount",
                    request.getAmount());

            apiResponse.put(
                    "customerEmail",
                    request.getEmail().trim());

            apiResponse.put(
                    "customerPhone",
                    request.getPhone().trim());

            apiResponse.put(
                    "paymentStatus",
                    "PENDING");

            apiResponse.put("gatewayProvider", "SABBPE_PBL");

            if (payment.getPaymentUrl() != null) {
                apiResponse.put("paymentUrl", payment.getPaymentUrl());
            }

            apiResponse.put("localEmailDelivery", localEmailStatus);

            apiResponse.put(
                    "linkStatus",
                    result.linkStatus());

            Map<String, Object> deliveryMap = result.delivery();
            apiResponse.put("delivery", deliveryMap);

            String emailStatus = null;
            String emailDetail = null;
            String whatsappStatus = null;
            String whatsappDetail = null;

            if (deliveryMap != null) {
                Object emailObj = deliveryMap.get("email");
                if (emailObj instanceof Map<?, ?> em) {
                    Object st = em.get("status");
                    Object dt = em.get("detail");
                    emailStatus = st != null ? String.valueOf(st) : null;
                    emailDetail = dt != null ? String.valueOf(dt) : null;
                }
                Object waObj = deliveryMap.get("whatsapp");
                if (waObj instanceof Map<?, ?> wa) {
                    Object wst = wa.get("status");
                    Object wdt = wa.get("detail");
                    whatsappStatus = wst != null ? String.valueOf(wst) : null;
                    whatsappDetail = wdt != null ? String.valueOf(wdt) : null;
                }
            }

            apiResponse.put("emailDeliveryStatus", emailStatus);
            apiResponse.put("emailDeliveryDetail", emailDetail);
            apiResponse.put("whatsappDeliveryStatus", whatsappStatus);
            apiResponse.put("whatsappDeliveryDetail", whatsappDetail);

            apiResponse.put(
                    "message",
                    result.message());

            if (receiptTokenService != null) {
                apiResponse.put("receiptToken", receiptTokenService.generateReceiptToken(payment));
            }

            if (isSplitPayment) {
                apiResponse.put("splitId", splitId);
                apiResponse.put("allocationId", allocationId);
            }

            return apiResponse;

        } catch (RestClientResponseException ex) {

            // Log a bounded, redacted provider diagnostic. Never log request bodies,
            // authentication headers, tokens, passwords, or full customer details.
            log.warn(
                    "SabbPe Pay By Link generation failed: upstreamStatus={}, providerResponse={}",
                    ex.getStatusCode().value(),
                    safeProviderDiagnostic(ex.getResponseBodyAsString())
            );

            payment.setStatus("INITIATION_FAILED");
            paymentRepository.save(payment);

            throw new SabbPeIntegrationException(
                    "SabbPe returned HTTP "
                            + ex.getStatusCode().value()
                            + " while generating the payment link. "
                            + "Verify the configured UAT URL and merchant settings.",
                    ex);

        } catch (RuntimeException ex) {

            /*
             * Do not delete the payment record.
             * Preserve the failure status for troubleshooting.
             */
            if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
                // The provider may already have created a link before a later
                // local step failed. Do not label that existing link as failed.
                payment.setStatus(isBlank(payment.getPblOrderRef())
                        ? "INITIATION_FAILED"
                        : "PENDING");
                paymentRepository.save(payment);
            }

            throw ex;
        }
    }

    private Map<String, Object> existingPaymentResponse(
            Payment payment,
            Long splitId,
            Long allocationId,
            String verifiedStatus,
            String linkStatus,
            String message) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("linkCreated", false);
        response.put("paymentId", payment.getId());
        response.put("merchantOrderRef", payment.getMerchantOrderRef());
        response.put("pblOrderRef", payment.getPblOrderRef());
        response.put("amount", payment.getAmount());
        response.put("paymentStatus", verifiedStatus);
        response.put("linkStatus", linkStatus);
        response.put("gatewayProvider", payment.getGatewayProvider() != null ? payment.getGatewayProvider() : "SABBPE_PBL");
        if (payment.getPaymentUrl() != null && !payment.getPaymentUrl().isBlank()) {
            response.put("paymentUrl", payment.getPaymentUrl());
        }
        response.put("localEmailDelivery", "NOT_ATTEMPTED (EXISTING_PAYMENT)");
        response.put("message", message);
        if (receiptTokenService != null) {
            response.put("receiptToken", receiptTokenService.generateReceiptToken(payment));
        }
        response.put("splitId", splitId);
        response.put("allocationId", allocationId);
        return response;
    }

    private void validateConfiguration(
            SabbPePaymentRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Payment request cannot be null.");
        }

        if (isBlank(baseUrl)
                || isBlank(initiatePath)
                || isBlank(frontendUrl)) {

            throw new IllegalStateException(
                    "SabbPe base URL, Pay By Link path, "
                            + "and frontend redirect URL must be configured.");
        }
    }

    private String safeProviderDiagnostic(String value) {
        if (value == null || value.isBlank()) return "<empty>";
        String safe = value.replaceAll("[\\r\\n\\t]", " ").trim();
        // Redact common JSON/form fields that may contain credentials or PII.
        safe = safe.replaceAll(
                "(?i)(\"(?:sabbpe_token|token|password|secret|email|phone|mobile|firstname|customer_name|merchant_id|merchantid|authorization|api_key|salt)\"\\s*:\\s*\")[^\"]*(\")",
                "$1[REDACTED]$2");
        safe = safe.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*", "$1[REDACTED]");
        safe = safe.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[REDACTED_EMAIL]");
        safe = safe.replaceAll("\\b(?:\\+?91[- ]?)?[6-9][0-9]{9}\\b", "[REDACTED_PHONE]");
        return safe.length() > 300 ? safe.substring(0, 300) + "…" : safe;
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() > 240
                ? normalized.substring(0, 240)
                : normalized;
    }

    private String resolvePaymentUrl(SabbPePayByLinkResponse result) {
        if (result == null) {
            return null;
        }

        String[] candidates = new String[] {
                result.linkUrl(),
                result.paymentUrl(),
                result.checkoutUrl(),
                result.paymentLink()
        };

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                String trimmed = candidate.trim();
                // Validate that the URL uses HTTP or HTTPS before saving or returning
                if (trimmed.regionMatches(true, 0, "http://", 0, 7)
                        || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
                    return trimmed;
                }
            }
        }

        return null;
    }

    private boolean sendPaymentLinkEmailSafely(
            String recipientEmail,
            String customerName,
            BigDecimal amount,
            String merchantOrderRef,
            String pblOrderRef,
            String paymentUrl,
            String trackingUrl) {

        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                log.info("JavaMailSender is not configured. Skipping local email dispatch for PBL ref: {}", pblOrderRef);
                return false;
            }

            if (isBlank(recipientEmail) || (isBlank(paymentUrl) && isBlank(pblOrderRef))) {
                return false;
            }

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            if (!isBlank(mailFrom)) {
                helper.setFrom(mailFrom, "SabbPe Payment Platform");
            }
            helper.setTo(recipientEmail.trim());
            helper.setSubject("Payment Request: " + pblOrderRef);

            String htmlContent = buildPaymentRequestHtml(
                    customerName,
                    amount,
                    merchantOrderRef,
                    pblOrderRef,
                    paymentUrl,
                    trackingUrl
            );

            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Payment link HTML email successfully accepted by SMTP server for recipient {} (PBL order: {})",
                    safeEmailMask(recipientEmail), pblOrderRef);
            return true;

        } catch (Exception ex) {
            // Email delivery failures must not cause payment link generation to fail
            log.warn("Payment link email delivery failed for PBL order {}: {}",
                    pblOrderRef, ex.getMessage());
            return false;
        }
    }

    private String buildPaymentRequestHtml(
            String customerName,
            BigDecimal amount,
            String merchantOrderRef,
            String pblOrderRef,
            String paymentUrl,
            String trackingUrl) {

        String safeCustomer = escapeHtml(customerName != null ? customerName : "Customer");
        String safePblRef = escapeHtml(pblOrderRef != null ? pblOrderRef : "—");
        String safeMerchantRef = escapeHtml(merchantOrderRef != null ? merchantOrderRef : "—");
        String formattedAmount = String.format(Locale.US, "%.2f", amount != null ? amount : BigDecimal.ZERO);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"en\">");
        sb.append("<head>");
        sb.append("<meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>Payment Request</title>");
        sb.append("</head>");
        sb.append("<body style=\"margin:0;padding:0;background-color:#0b0f19;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#f1f5f9;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color:#0b0f19;padding:40px 15px;\">");
        sb.append("<tr><td align=\"center\">");

        // Container card
        sb.append("<table role=\"presentation\" width=\"100%\" max-width=\"540\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"max-width:540px;background-color:#141c2e;border:1px solid #1e293b;border-radius:16px;box-shadow:0 20px 40px rgba(0,0,0,0.5);overflow:hidden;\">");

        // Top Accent Bar
        sb.append("<tr><td style=\"height:4px;background:linear-gradient(90deg,#3b82f6,#8b5cf6);\"></td></tr>");

        // Header Section
        sb.append("<tr><td style=\"padding:32px 32px 20px 32px;text-align:center;\">");
        sb.append("<div style=\"display:inline-block;padding:8px 14px;background-color:#1e293b;border:1px solid #334155;border-radius:9999px;font-size:12px;font-weight:600;letter-spacing:0.05em;color:#94a3b8;margin-bottom:14px;\">");
        sb.append("OFFICIAL INVOICE REQUEST");
        sb.append("</div>");
        sb.append("<h1 style=\"margin:0 0 6px 0;font-size:24px;font-weight:700;color:#ffffff;letter-spacing:-0.02em;\">Payment Request</h1>");
        sb.append("<p style=\"margin:0;font-size:14px;color:#94a3b8;\">Requested by <strong style=\"color:#e2e8f0;\">PBL SIAN</strong></p>");
        sb.append("</td></tr>");

        // Amount Display Box
        sb.append("<tr><td style=\"padding:0 32px 24px 32px;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background:linear-gradient(180deg,#18243c,#131d31);border:1px solid #243552;border-radius:12px;padding:22px;text-align:center;\">");
        sb.append("<tr><td>");
        sb.append("<span style=\"font-size:12px;text-transform:uppercase;letter-spacing:0.08em;color:#94a3b8;font-weight:600;\">Amount Payable</span>");
        sb.append("<div style=\"font-size:36px;font-weight:800;color:#ffffff;margin:8px 0 4px 0;\">₹").append(formattedAmount).append("</div>");
        sb.append("<span style=\"font-size:12px;color:#64748b;\">Indian Rupee (INR) · Tax Inclusive</span>");
        sb.append("</td></tr>");
        sb.append("</table>");
        sb.append("</td></tr>");

        // Pay Now CTA Button
        sb.append("<tr><td style=\"padding:0 32px 28px 32px;text-align:center;\">");
        if (!isBlank(paymentUrl)) {
            sb.append("<a href=\"").append(escapeHtml(paymentUrl)).append("\" target=\"_blank\" style=\"display:block;background:linear-gradient(135deg,#2563eb,#1d4ed8);color:#ffffff;text-decoration:none;font-weight:700;font-size:16px;padding:16px 24px;border-radius:10px;box-shadow:0 8px 20px rgba(37,99,235,0.35);letter-spacing:0.02em;\">");
            sb.append("Pay Now &rarr;");
            sb.append("</a>");
            sb.append("<p style=\"margin:10px 0 0 0;font-size:12px;color:#64748b;\">Instant checkout via UPI, Debit/Credit Card, Net Banking</p>");
        } else {
            sb.append("<div style=\"padding:14px;background-color:#1e293b;border-radius:10px;font-size:13px;color:#94a3b8;\">");
            sb.append("Payment link registration acknowledged (Reference: <strong style=\"color:#f1f5f9;\">").append(safePblRef).append("</strong>)");
            sb.append("</div>");
        }
        sb.append("</td></tr>");

        // Order Details Table
        sb.append("<tr><td style=\"padding:0 32px 28px 32px;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"border-collapse:separate;border-spacing:0;background-color:#0f172a;border:1px solid #1e293b;border-radius:10px;overflow:hidden;\">");

        // Row 1: Customer
        sb.append("<tr>");
        sb.append("<td style=\"padding:12px 16px;font-size:13px;color:#94a3b8;border-bottom:1px solid #1e293b;\">Billed To</td>");
        sb.append("<td style=\"padding:12px 16px;font-size:13px;font-weight:600;color:#f8fafc;text-align:right;border-bottom:1px solid #1e293b;\">").append(safeCustomer).append("</td>");
        sb.append("</tr>");

        // Row 2: PBL Order Ref
        sb.append("<tr>");
        sb.append("<td style=\"padding:12px 16px;font-size:13px;color:#94a3b8;border-bottom:1px solid #1e293b;\">PBL Order Ref</td>");
        sb.append("<td style=\"padding:12px 16px;font-size:13px;font-family:monospace;font-weight:600;color:#38bdf8;text-align:right;border-bottom:1px solid #1e293b;\">").append(safePblRef).append("</td>");
        sb.append("</tr>");

        // Row 3: Merchant Ref
        sb.append("<tr>");
        sb.append("<td style=\"padding:12px 16px;font-size:13px;color:#94a3b8;\">Merchant Order Ref</td>");
        sb.append("<td style=\"padding:12px 16px;font-size:12px;font-family:monospace;color:#94a3b8;text-align:right;\">").append(safeMerchantRef).append("</td>");
        sb.append("</tr>");

        sb.append("</table>");
        sb.append("</td></tr>");

        // Status / Receipt Tracking Link
        if (!isBlank(trackingUrl)) {
            sb.append("<tr><td style=\"padding:0 32px 24px 32px;text-align:center;\">");
            sb.append("<a href=\"").append(escapeHtml(trackingUrl)).append("\" target=\"_blank\" style=\"font-size:13px;color:#60a5fa;text-decoration:none;font-weight:500;\">");
            sb.append("Track payment status &amp; view receipt &rarr;");
            sb.append("</a>");
            sb.append("</td></tr>");
        }

        // Footer Section
        sb.append("<tr><td style=\"padding:20px 32px 28px 32px;border-top:1px solid #1e293b;text-align:center;background-color:#0d1322;\">");
        sb.append("<p style=\"margin:0 0 6px 0;font-size:12px;color:#64748b;font-weight:600;\">SECURE PAYMENT GATEWAY · 256-BIT ENCRYPTION</p>");
        sb.append("<p style=\"margin:0;font-size:11px;color:#475569;line-height:1.5;\">Powered by SabbPe Payment Platform &amp; Easebuzz. This is an automated billing notification sent to your email.</p>");
        sb.append("</td></tr>");

        sb.append("</table>"); // End card

        sb.append("</td></tr></table>"); // End body
        sb.append("</body></html>");

        return sb.toString();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safeEmailMask(String email) {
        if (email == null || email.isBlank()) return "<empty>";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SabbPePayByLinkResponse(

            @JsonProperty("status")
            Boolean status,

            @JsonProperty("pbl_order_ref")
            String pblOrderRef,

            @JsonProperty("merchant_order_ref")
            String merchantOrderRef,

            @JsonProperty("link_status")
            String linkStatus,

            @JsonProperty("delivery")
            Map<String, Object> delivery,

            @JsonProperty("payment_url")
            String paymentUrl,

            @JsonProperty("link_url")
            String linkUrl,

            @JsonProperty("checkout_url")
            String checkoutUrl,

            @JsonProperty("payment_link")
            String paymentLink,

            @JsonProperty("message")
            String message
    ) {
    }
}
