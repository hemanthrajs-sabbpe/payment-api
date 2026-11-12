
package com.example.payment_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class EasebuzzService {

    @Value("${easebuzz.key}")
    private String key;

    @Value("${easebuzz.salt}")
    private String salt;

    @Value("${easebuzz.base-url}")
    private String baseUrl;

    private final RestClient restClient = RestClient.create();

    // INITIATE EASEBUZZ PAYMENT
    public String initiatePayment(
            String txnid,
            BigDecimal amount,
            String productinfo,
            String firstname,
            String email,
            String phone,
            String successUrl,
            String failureUrl) {

        if (txnid == null || txnid.isBlank()
                || amount == null || amount.signum() <= 0
                || productinfo == null || productinfo.isBlank()
                || firstname == null || firstname.isBlank()
                || email == null || email.isBlank()
                || phone == null || phone.isBlank()
                || successUrl == null || successUrl.isBlank()
                || failureUrl == null || failureUrl.isBlank()) {

            throw new IllegalArgumentException(
                    "All required payment details must be provided");
        }

        BigDecimal normalizedAmount =
                amount.setScale(2, RoundingMode.UNNECESSARY);

        String formattedAmount = normalizedAmount.toPlainString();

        String hashInput = String.join("|",
                key.trim(),
                txnid.trim(),
                formattedAmount,
                productinfo.trim(),
                firstname.trim(),
                email.trim(),
                "", "", "", "", "", "", "", "", "", "",
                salt.trim());

        String hash = sha512(hashInput);

        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();

        form.add("key", key.trim());
        form.add("txnid", txnid.trim());
        form.add("amount", formattedAmount);
        form.add("productinfo", productinfo.trim());
        form.add("firstname", firstname.trim());
        form.add("email", email.trim());
        form.add("phone", phone.trim());
        form.add("surl", successUrl.trim());
        form.add("furl", failureUrl.trim());
        form.add("hash", hash);

        for (int i = 1; i <= 10; i++) {
            form.add("udf" + i, "");
        }

        try {
            String endpoint = baseUrl.replaceAll("/+$", "")
                    + "/payment/initiateLink";

            Map<?, ?> response = restClient.post()
                    .uri(endpoint)
                    .contentType(
                            MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException(
                        "Empty response from Easebuzz");
            }

            Object status = response.get("status");
            Object data = response.get("data");

            if ("1".equals(String.valueOf(status))
                    && data != null
                    && !String.valueOf(data).isBlank()) {

                String accessKey = String.valueOf(data);

                return baseUrl.replaceAll("/+$", "")
                        + "/pay/" + accessKey;
            }

            throw new IllegalStateException(
                    "Easebuzz initiation was rejected");

        } catch (org.springframework.web.client.RestClientException ex) {
            throw new IllegalStateException(
                    "Could not communicate with Easebuzz sandbox", ex);
        }
    }

    // VERIFY EASEBUZZ CALLBACK HASH
    public boolean verifyCallback(EasebuzzCallbackRequest callback) {

        if (callback == null
                || isBlank(callback.getKey())
                || isBlank(callback.getTxnid())
                || isBlank(callback.getAmount())
                || isBlank(callback.getProductinfo())
                || isBlank(callback.getFirstname())
                || isBlank(callback.getEmail())
                || isBlank(callback.getStatus())
                || isBlank(callback.getHash())) {
            return false;
        }

        // Reject responses for a different merchant key.
        if (!key.trim().equals(callback.getKey().trim())) {
            return false;
        }

        // Easebuzz response hash order:
        // salt|status|udf10|udf9|...|udf1|email|firstname|
        // productinfo|amount|txnid|key

        String hashInput = String.join("|",
                salt.trim(),
                callback.getStatus(),
                value(callback.getUdf10()),
                value(callback.getUdf9()),
                value(callback.getUdf8()),
                value(callback.getUdf7()),
                value(callback.getUdf6()),
                value(callback.getUdf5()),
                value(callback.getUdf4()),
                value(callback.getUdf3()),
                value(callback.getUdf2()),
                value(callback.getUdf1()),
                callback.getEmail(),
                callback.getFirstname(),
                callback.getProductinfo(),
                callback.getAmount(),
                callback.getTxnid(),
                callback.getKey()
        );

        String expectedHash = sha512(hashInput);

        // Constant-time comparison of the supplied and expected hashes.
        byte[] expected = expectedHash
                .toLowerCase(java.util.Locale.ROOT)
                .getBytes(StandardCharsets.US_ASCII);

        byte[] received = callback.getHash()
                .toLowerCase(java.util.Locale.ROOT)
                .getBytes(StandardCharsets.US_ASCII);

        return MessageDigest.isEqual(expected, received);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String sha512(String input) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-512");

            byte[] bytes = digest.digest(
                    input.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();

            for (byte b : bytes) {
                result.append(String.format("%02x", b & 0xff));
            }

            return result.toString();

        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-512 is not available", ex);
        }
    }
}
