
package com.example.payment_api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${sabbpe.frontend-url:http://192.168.1.13:5173}")
    private String frontendUrl;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse resp = new PaymentResponse(
                payment.getId(),
                payment.getCustomerName(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getMerchantOrderRef(),
                payment.getPblOrderRef()
        );
        resp.setPaymentUrl(payment.getPaymentUrl());
        resp.setPaymentMethod(payment.getPaymentMethod());
        resp.setGatewayProvider(payment.getGatewayProvider());
        resp.setMasterTransactionId(payment.getMasterTransactionId());
        return resp;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request) {

        Payment payment = paymentService.createPayment(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(payment));
    }

    @GetMapping
    public List<PaymentResponse> getAllPayments() {
        return paymentService.getAllPayments()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse getPaymentById(@PathVariable Long id) {
        return toResponse(paymentService.getPaymentById(id));
    }

    @PutMapping("/{id}")
    public PaymentResponse updatePayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {

        return toResponse(paymentService.updatePayment(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
    }

    @PostMapping("/easebuzz/initiate")
    public ResponseEntity<Map<String, Object>> initiateEasebuzzPayment(
            @Valid @RequestBody EasebuzzPaymentRequest request) {

        return ResponseEntity.ok(
                paymentService.initiateEasebuzzPayment(request));
    }

    @PostMapping("/callback")
    public ResponseEntity<PaymentResponse> paymentCallback(
            @Valid @RequestBody PaymentCallbackRequest request) {

        return ResponseEntity.ok(
                toResponse(paymentService.updatePaymentStatus(request)));
    }

    @PostMapping(
            value = "/easebuzz/response",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public ResponseEntity<Void> easebuzzCallback(
            @RequestParam Map<String, String> formData) {

        EasebuzzCallbackRequest callback =
                new EasebuzzCallbackRequest();

        callback.setStatus(formData.get("status"));
        callback.setTxnid(formData.get("txnid"));
        callback.setAmount(formData.get("amount"));
        callback.setHash(formData.get("hash"));
        callback.setKey(formData.get("key"));
        callback.setEmail(formData.get("email"));
        callback.setFirstname(formData.get("firstname"));
        callback.setProductinfo(formData.get("productinfo"));

        callback.setUdf1(formData.get("udf1"));
        callback.setUdf2(formData.get("udf2"));
        callback.setUdf3(formData.get("udf3"));
        callback.setUdf4(formData.get("udf4"));
        callback.setUdf5(formData.get("udf5"));
        callback.setUdf6(formData.get("udf6"));
        callback.setUdf7(formData.get("udf7"));
        callback.setUdf8(formData.get("udf8"));
        callback.setUdf9(formData.get("udf9"));
        callback.setUdf10(formData.get("udf10"));

        Payment payment =
                paymentService.processEasebuzzCallback(callback);

        String baseRedirect = (frontendUrl != null && !frontendUrl.isBlank())
                ? frontendUrl.replaceAll("/+$", "")
                : "http://" + formData.getOrDefault("originHost", "192.168.1.13:5173");

        URI resultPage = URI.create(
                baseRedirect + "/payment-result/" + payment.getId()
        );

        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(resultPage)
                .build();
    }
}
