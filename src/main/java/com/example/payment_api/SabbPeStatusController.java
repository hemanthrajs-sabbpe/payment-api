package com.example.payment_api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sabbpe")
public class SabbPeStatusController {

    private final PaymentRepository paymentRepository;
    private final SabbPeStatusService sabbPeStatusService;

    public SabbPeStatusController(
            PaymentRepository paymentRepository,
            SabbPeStatusService sabbPeStatusService) {
        this.paymentRepository = paymentRepository;
        this.sabbPeStatusService = sabbPeStatusService;
    }

    @GetMapping("/payments/{paymentId}/status")
    public ResponseEntity<?> getPaymentStatus(
            @PathVariable Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElse(null);

        if (payment == null) {
            return ResponseEntity.notFound().build();
        }

        if (payment.getPblOrderRef() == null
                || payment.getPblOrderRef().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Payment has no SabbPe Pay By Link reference");
        }

        try {
            SabbPeStatusService.SabbPePaymentStatusResponse status =
                    sabbPeStatusService.getVerifiedPaymentStatus(payment);

            return ResponseEntity.ok(status);

        } catch (RuntimeException ex) {
            return ResponseEntity.status(502)
                    .body("Unable to retrieve verified payment status from SabbPe");
        }
    }
}