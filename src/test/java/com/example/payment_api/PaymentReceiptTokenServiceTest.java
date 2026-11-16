package com.example.payment_api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class PaymentReceiptTokenServiceTest {

    private PaymentReceiptTokenService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReceiptTokenService();
        ReflectionTestUtils.setField(
                service,
                "tokenSecret",
                "3b86727903fbecf7eb1ffc04702626285c14b4ebedc1828fd12023be8312155b"
        );
    }

    @Test
    @DisplayName("Should generate valid token bound to payment attributes and successfully validate it")
    void testValidTokenGenerationAndValidation() {
        Payment payment = new Payment();
        payment.setCustomerName("Participant 2");
        payment.setAmount(50.0);
        payment.setStatus("PENDING");
        payment.setMerchantOrderRef("SABBPE-1C6E19A1BEC340AF");
        payment.setPblOrderRef("PBLMWJ2ZYZO442");
        ReflectionTestUtils.setField(payment, "id", 507L);

        String token = service.generateReceiptToken(payment);
        assertNotNull(token);
        assertTrue(token.contains("."));

        // Validate correct payment and token
        boolean valid = service.validateReceiptToken(507L, token, payment);
        assertTrue(valid, "Token must be valid for the exact target payment");
    }

    @Test
    @DisplayName("Should reject token when accessing another customer's payment")
    void testCrossCustomerTamperRejection() {
        Payment payment1 = new Payment();
        payment1.setMerchantOrderRef("REF-CUSTOMER-1");
        payment1.setPblOrderRef("PBL-1");
        ReflectionTestUtils.setField(payment1, "id", 101L);

        Payment payment2 = new Payment();
        payment2.setMerchantOrderRef("REF-CUSTOMER-2");
        payment2.setPblOrderRef("PBL-2");
        ReflectionTestUtils.setField(payment2, "id", 102L);

        String token1 = service.generateReceiptToken(payment1);

        // Attempt to use customer 1's token on customer 2's payment
        assertFalse(service.validateReceiptToken(102L, token1, payment2),
                "Customer 1 token must be rejected when accessing Customer 2 payment");
        assertFalse(service.validateReceiptToken(101L, token1, payment2),
                "Mismatched payment object must be rejected");
    }

    @Test
    @DisplayName("Should reject forged, modified or empty tokens")
    void testForgedTokenRejection() {
        Payment payment = new Payment();
        payment.setMerchantOrderRef("REF-FORGED");
        payment.setPblOrderRef("PBL-FORGED");
        ReflectionTestUtils.setField(payment, "id", 200L);

        assertFalse(service.validateReceiptToken(200L, null, payment));
        assertFalse(service.validateReceiptToken(200L, "", payment));
        assertFalse(service.validateReceiptToken(200L, "randomStringWithoutDot", payment));
        assertFalse(service.validateReceiptToken(200L, "fakePayload.fakeSignature", payment));
    }
}
