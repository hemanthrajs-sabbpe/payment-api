package com.example.payment_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PaymentReceiptTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${simulator.token.secret:3b86727903fbecf7eb1ffc04702626285c14b4ebedc1828fd12023be8312155b}")
    private String tokenSecret;

    private byte[] secretKeyBytes;

    private synchronized byte[] getSecretKeyBytes() {
        if (secretKeyBytes != null) {
            return secretKeyBytes;
        }
        String clean = (tokenSecret != null && !tokenSecret.isBlank())
                ? tokenSecret.trim()
                : "3b86727903fbecf7eb1ffc04702626285c14b4ebedc1828fd12023be8312155b";
        try {
            if (clean.matches("^[0-9a-fA-F]{64}$")) {
                secretKeyBytes = HexFormat.of().parseHex(clean);
            } else {
                secretKeyBytes = clean.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            secretKeyBytes = clean.getBytes(StandardCharsets.UTF_8);
        }
        return secretKeyBytes;
    }

    /**
     * Generates a tamper-proof receipt token bound to paymentId and merchantOrderRef/pblOrderRef.
     */
    public String generateReceiptToken(Payment payment) {
        if (payment == null || payment.getId() == null) {
            return null;
        }
        Long paymentId = payment.getId();
        String ref = payment.getMerchantOrderRef() != null ? payment.getMerchantOrderRef() : "";
        String pbl = payment.getPblOrderRef() != null ? payment.getPblOrderRef() : "";

        String payload = paymentId + ":" + ref + ":" + pbl;
        String signature = computeHmac(payload);

        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + signature;
    }

    /**
     * Validates that the provided token strictly matches the target payment.
     */
    public boolean validateReceiptToken(Long paymentId, String token, Payment payment) {
        if (paymentId == null || token == null || token.isBlank() || payment == null || !paymentId.equals(payment.getId())) {
            return false;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        String encodedPayload = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        String expectedRef = payment.getMerchantOrderRef() != null ? payment.getMerchantOrderRef() : "";
        String expectedPbl = payment.getPblOrderRef() != null ? payment.getPblOrderRef() : "";
        String expectedPayload = paymentId + ":" + expectedRef + ":" + expectedPbl;

        String expectedSig = computeHmac(expectedPayload);

        // Constant-time comparison
        byte[] expectedSigBytes = expectedSig.getBytes(StandardCharsets.UTF_8);
        byte[] providedSigBytes = signature.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedSigBytes, providedSigBytes)) {
            return false;
        }

        try {
            String decodedPayload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expectedPayload.getBytes(StandardCharsets.UTF_8), decodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(getSecretKeyBytes(), HMAC_SHA256));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
