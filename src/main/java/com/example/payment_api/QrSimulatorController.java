package com.example.payment_api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Controller exposing REST endpoints for the QR Payment Simulator.
 *
 * Endpoints:
 * 1. POST /api/simulator/splits/{splitId}/allocations/{allocationId}/qr  (Authenticated - Generates allocation QR)
 * 2. GET  /api/simulator/qr/details?token={rawToken}                   (Public - Validates token & displays payment details)
 * 3. POST /api/simulator/qr/process                                     (Public - Processes SUCCESS/FAILURE simulation)
 */
@RestController
@RequestMapping("/api/simulator")
public class QrSimulatorController {

    private static final Logger log = LoggerFactory.getLogger(QrSimulatorController.class);

    private final QrSimulatorService qrSimulatorService;

    public QrSimulatorController(QrSimulatorService qrSimulatorService) {
        this.qrSimulatorService = qrSimulatorService;
    }

    /**
     * Authenticated endpoint to initiate or refresh a simulated QR payment attempt for an allocation.
     * Enforces app.simulator.enabled=false check inside service.
     */
    @PostMapping("/splits/{splitId}/allocations/{allocationId}/qr")
    public ResponseEntity<QrInitiationResponse> initiateAllocationQr(
            @PathVariable("splitId") Long splitId,
            @PathVariable("allocationId") Long allocationId) {

        log.info("QR initiation requested for splitId={}, allocationId={}", splitId, allocationId);

        QrSimulatorService.QrInitiationResult result = qrSimulatorService.initiateAllocationQr(splitId, allocationId);

        QrInitiationResponse response = new QrInitiationResponse(
                result.splitId(),
                result.allocationId(),
                result.participantName(),
                result.amount(),
                result.paymentId(),
                result.qrToken(),
                result.scanUrl(),
                result.expiresAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Public endpoint invoked when the simulated customer scans the QR code.
     * Validates cryptographic token, unexpired session, and matching pending payment attempt.
     */
    @GetMapping("/qr/details")
    public ResponseEntity<QrDetailsResponse> getScannedQrDetails(
            @RequestParam("token") @NotBlank(message = "QR token is required") String token) {

        QrSimulatorService.QrPaymentDetails details = qrSimulatorService.getScannedDetails(token);

        QrDetailsResponse response = new QrDetailsResponse(
                details.splitTitle(),
                details.participantName(),
                details.amount(),
                details.currency(),
                details.paymentId(),
                details.status(),
                details.expiresAt()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Public endpoint invoked by simulated customer to submit outcome (SUCCESS or FAILURE).
     * If the session expired in the interim, the service commits the TIMEOUT state and returns
     * status="TIMEOUT", which maps to HTTP 410 Gone.
     */
    @PostMapping("/qr/process")
    public ResponseEntity<QrProcessResponse> processSimulatedOutcome(
            @Valid @RequestBody QrProcessRequest request) {

        QrSimulatorService.QrProcessResult result = qrSimulatorService.processOutcome(
                request.token(),
                request.paymentId(),
                request.action()
        );

        QrProcessResponse response = new QrProcessResponse(
                result.status(),
                result.paymentId(),
                result.allocationId(),
                result.amount(),
                result.message()
        );

        if ("TIMEOUT".equalsIgnoreCase(result.status())) {
            return ResponseEntity.status(HttpStatus.GONE).body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public record QrInitiationResponse(
            Long splitId,
            Long allocationId,
            String participantName,
            BigDecimal amount,
            Long paymentId,
            String qrToken,
            String scanUrl,
            LocalDateTime expiresAt
    ) {}

    public record QrDetailsResponse(
            String splitTitle,
            String participantName,
            BigDecimal amount,
            String currency,
            Long paymentId,
            String status,
            LocalDateTime expiresAt
    ) {}

    public record QrProcessRequest(
            @NotBlank(message = "Token is required")
            String token,

            @NotNull(message = "Payment ID is required")
            Long paymentId,

            @NotBlank(message = "Action is required")
            @Pattern(regexp = "(?i)^(SUCCESS|FAILURE)$", message = "Action must be either SUCCESS or FAILURE")
            String action
    ) {}

    public record QrProcessResponse(
            String status,
            Long paymentId,
            Long allocationId,
            BigDecimal amount,
            String message
    ) {}
}
