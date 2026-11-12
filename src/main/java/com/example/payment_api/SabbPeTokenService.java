package com.example.payment_api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SabbPeTokenService {

    private static final Logger log =
            LoggerFactory.getLogger(SabbPeTokenService.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern SECRET_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"?(?:sabbpe_password|password|sabbpe_token|access_token|token|authorization|secret|merchant_key|salt)\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^,}\\s]+)"
    );

    @Value("${sabbpe.base-url}")
    private String baseUrl;

    @Value("${sabbpe.token-path}")
    private String tokenPath;

    @Value("${sabbpe.merchant-id}")
    private String merchantId;

    @Value("${sabbpe.user-id}")
    private String userId;

    @Value("${sabbpe.password}")
    private String password;

    // Optional and account-specific. When blank, the field is omitted.
    @Value("${sabbpe.service-code:}")
    private String serviceCode;

    private final RestTemplate restTemplate = new RestTemplate();

    public SabbPeTokenResponse generateToken(String merchantOrderRef) {

        if (isBlank(baseUrl) || isBlank(tokenPath)) {
            throw new IllegalStateException(
                    "SabbPe base URL and token path must be configured.");
        }

        if (isBlank(merchantId)
                || isBlank(userId)
                || isBlank(password)) {
            throw new IllegalStateException(
                    "SabbPe merchant credentials are missing.");
        }

        if (isBlank(merchantOrderRef)) {
            throw new IllegalArgumentException(
                    "Merchant order reference is required.");
        }

        String timestamp = LocalDateTime.now()
                .format(TIMESTAMP_FORMATTER);

        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("sabbpe_userid", userId);
        requestBody.put("sabbpe_merchantid", merchantId);
        requestBody.put("sabbpe_password", password);
        requestBody.put("timestamp", timestamp);
        requestBody.put(
                "merchant_order_ref",
                merchantOrderRef.trim());

        if (!isBlank(serviceCode)) {
            requestBody.put("service_code", serviceCode.trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(requestBody, headers);

        String url = baseUrl.replaceAll("/+$", "")
                + "/"
                + tokenPath.replaceAll("^/+", "");

        try {
            ResponseEntity<SabbPeTokenResponse> response =
                    restTemplate.postForEntity(
                            url,
                            request,
                            SabbPeTokenResponse.class);

            SabbPeTokenResponse result = response.getBody();

            if (result == null) {
                throw new SabbPeIntegrationException(
                        "SabbPe returned an empty token response.");
            }

            if (Boolean.FALSE.equals(result.status())) {
                String providerMessage = safeProviderMessage(result.message());
                log.warn("SabbPe rejected token request: status=false; provider message length={}",
                        providerMessage.length());
                throw new SabbPeIntegrationException(
                        "SabbPe rejected the token request. Verify the UAT URL, "
                                + "merchant credentials, optional service code, "
                                + "timestamp, and unique merchant order reference.");
            }

            if (isBlank(result.sabbpeToken())) {
                throw new SabbPeIntegrationException(
                        "SabbPe did not return a usable authentication token.");
            }

            return result;

        } catch (RestClientResponseException ex) {

            int statusCode = ex.getStatusCode().value();
            String responseBody = ex.getResponseBodyAsString();

            // Sanitize likely credential and token fields before logging.
            String safeBody = sanitizeResponseBody(responseBody);

            log.error(
                    "SabbPe token request failed: HTTP {}, responseBody={}",
                    statusCode,
                    isBlank(safeBody) ? "<empty>" : safeBody);

            // Keep the exception message generic to avoid propagating
            // a provider response that might contain sensitive information.
            throw new SabbPeIntegrationException(
                    "SabbPe token request failed with HTTP "
                            + statusCode
                            + ". Verify the configured UAT URL and merchant settings.",
                    ex);
        }
    }

    private String sanitizeResponseBody(String responseBody) {
        if (isBlank(responseBody)) {
            return "";
        }

        // Limit log size and redact common secret fields.
        String limitedBody = responseBody.length() > 1500
                ? responseBody.substring(0, 1500)
                : responseBody;

        return SECRET_FIELD_PATTERN
                .matcher(limitedBody)
                .replaceAll("$1[REDACTED]");
    }

    private String safeProviderMessage(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() > 240
                ? normalized.substring(0, 240)
                : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SabbPeTokenResponse(
            @JsonProperty("sabbpe_token")
            String sabbpeToken,
            @JsonProperty("status")
            Boolean status,
            @JsonProperty("message")
            String message
    ) {
    }
}
