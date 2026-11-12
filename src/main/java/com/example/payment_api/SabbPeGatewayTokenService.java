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
public class SabbPeGatewayTokenService {

    private static final Logger log =
            LoggerFactory.getLogger(SabbPeGatewayTokenService.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern SECRET_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"?(?:sabbpe_password|password|sabbpe_token|access_token|token|authorization|secret|merchant_key|salt)\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^,}\\s]+)"
    );

    @Value("${sabbpe.gateway.base-url}")
    private String baseUrl;

    @Value("${sabbpe.gateway.token-path}")
    private String tokenPath;

    @Value("${sabbpe.gateway.merchant-id}")
    private String merchantId;

    @Value("${sabbpe.gateway.user-id}")
    private String userId;

    @Value("${sabbpe.gateway.password}")
    private String password;

    @Value("${sabbpe.gateway.service-code:${sabbpe.service-code:PBL}}")
    private String serviceCode;

    private final RestTemplate restTemplate = new RestTemplate();

    public SabbPeGatewayTokenService() {
    }

    public String generateGatewayToken(String merchantOrderRef) {
        if (merchantOrderRef == null || merchantOrderRef.trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant order reference is required to generate SabbPe Gateway token");
        }

        validateConfiguration();

        String url = normalizeBaseUrl(baseUrl) + normalizePath(tokenPath);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);

        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("sabbpe_userid", userId);
        requestBody.put("sabbpe_merchantid", merchantId);
        requestBody.put("sabbpe_password", password);
        requestBody.put("timestamp", timestamp);
        requestBody.put("merchant_order_ref", merchantOrderRef);
        if (serviceCode != null && !serviceCode.trim().isEmpty()) {
            requestBody.put("service_code", serviceCode.trim());
        }

        if (serviceCode != null && !serviceCode.trim().isEmpty()) {
            requestBody.put("service_code", serviceCode.trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

        log.info("Requesting SabbPe Gateway token for merchantOrderRef: {}", merchantOrderRef);

        try {
            ResponseEntity<GatewayTokenApiResponse> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    GatewayTokenApiResponse.class
            );

            GatewayTokenApiResponse responseBody = response.getBody();
            if (responseBody == null) {
                throw new SabbPeIntegrationException("Received empty response from SabbPe Gateway token service");
            }

            String token = responseBody.getToken();
            if (token == null || token.trim().isEmpty()) {
                String message = responseBody.getMessage() != null
                        ? responseBody.getMessage()
                        : "SabbPe Gateway token service did not return a valid token";
                throw new SabbPeIntegrationException("SabbPe Gateway token generation failed: " + message);
            }

            log.info("Successfully received SabbPe Gateway token for merchantOrderRef: {}", merchantOrderRef);
            return token.trim();

        } catch (RestClientResponseException ex) {
            String sanitizedError = maskSensitiveData(ex.getResponseBodyAsString());
            log.error("SabbPe Gateway token API error. Status: {}, Response: {}",
                    ex.getStatusCode(), sanitizedError);
            throw new SabbPeIntegrationException(
                    "SabbPe Gateway token generation failed with status " + ex.getStatusCode() + ": " + sanitizedError,
                    ex
            );
        } catch (SabbPeIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error while generating SabbPe Gateway token: {}", ex.getMessage(), ex);
            throw new SabbPeIntegrationException("Failed to generate SabbPe Gateway token: " + ex.getMessage(), ex);
        }
    }

    private void validateConfiguration() {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("sabbpe.gateway.base-url is not configured");
        }
        if (tokenPath == null || tokenPath.trim().isEmpty()) {
            throw new IllegalStateException("sabbpe.gateway.token-path is not configured");
        }
        if (merchantId == null || merchantId.trim().isEmpty()) {
            throw new IllegalStateException("sabbpe.gateway.merchant-id is not configured");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalStateException("sabbpe.gateway.user-id is not configured");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalStateException("sabbpe.gateway.password is not configured");
        }
    }

    private String normalizeBaseUrl(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizePath(String path) {
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    private String maskSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return SECRET_FIELD_PATTERN.matcher(input).replaceAll("$1\"***\"");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatewayTokenApiResponse {
        private String status;
        private String message;
        private String token;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getToken() {
            return token;
        }

                @JsonProperty("token")
        public void setToken(String token) {
            this.token = token;
        }

        @JsonProperty("sabbpe_token")
        public void setSabbpeToken(String sabbpeToken) {
            if (sabbpeToken != null && !sabbpeToken.trim().isEmpty()) {
                this.token = sabbpeToken;
            }
        }
    }
}
