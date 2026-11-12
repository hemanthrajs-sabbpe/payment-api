
package com.example.payment_api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/split-sessions")
public class SplitSessionController {

    private final SplitSessionService splitSessionService;

    public SplitSessionController(
            SplitSessionService splitSessionService) {
        this.splitSessionService = splitSessionService;
    }

    /**
     * Creates a QR session for a split.
     * Authentication is required.
     */
    @PostMapping
    public ResponseEntity<SplitSessionService.CreatedSession> createSession(
            @RequestBody CreateSessionRequest request) {

        if (request == null || request.splitId() == null) {
            throw new IllegalArgumentException("Split ID is required");
        }

        SplitSessionService.CreatedSession response =
                splitSessionService.createSession(
                        request.splitId(),
                        request.branchId(),
                        request.joinPolicy(),
                        request.maxParticipants(),
                        request.expiresAt()
                );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves one session.
     * Authentication and split authorization are required.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SplitSessionService.SessionDetails> getSession(
            @PathVariable Long sessionId) {

        return ResponseEntity.ok(
                splitSessionService.getSession(sessionId)
        );
    }

    /**
     * Lists sessions belonging to a split.
     * Authentication and split authorization are required.
     */
    @GetMapping("/split/{splitId}")
    public ResponseEntity<List<SplitSessionService.SessionDetails>>
            getSessionsForSplit(@PathVariable Long splitId) {

        return ResponseEntity.ok(
                splitSessionService.getSessionsForSplit(splitId)
        );
    }

    /**
     * Regenerates the QR token.
     * Authentication and split authorization are required.
     */
    @PostMapping("/{sessionId}/regenerate")
    public ResponseEntity<SplitSessionService.CreatedSession>
            regenerateQrToken(@PathVariable Long sessionId) {

        return ResponseEntity.ok(
                splitSessionService.regenerateQrToken(sessionId)
        );
    }

    /**
     * Closes an active session.
     * Authentication and split authorization are required.
     */
    @PostMapping("/{sessionId}/close")
    public ResponseEntity<SplitSessionService.SessionDetails>
            closeSession(@PathVariable Long sessionId) {

        return ResponseEntity.ok(
                splitSessionService.closeSession(sessionId)
        );
    }

    /**
     * Validates a scanned QR token.
     *
     * This endpoint is intended for public QR scanning.
     * SecurityConfig must explicitly permit POST
     * /api/split-sessions/validate.
     */
    @PostMapping("/validate")
    public ResponseEntity<SplitSessionService.ValidatedSession>
            validateQrToken(@RequestBody ValidateQrRequest request) {

        if (request == null
                || request.qrToken() == null
                || request.qrToken().isBlank()) {

            throw new IllegalArgumentException("QR token is required");
        }

        return ResponseEntity.ok(
                splitSessionService.validateQrToken(request.qrToken())
        );
    }

    public record CreateSessionRequest(
            Long splitId,
            Long branchId,
            String joinPolicy,
            Integer maxParticipants,
            LocalDateTime expiresAt
    ) {
    }

    public record ValidateQrRequest(
            String qrToken
    ) {
    }
}
