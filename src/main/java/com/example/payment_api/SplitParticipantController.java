
package com.example.payment_api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/split-participants")
public class SplitParticipantController {

    private final SplitParticipantService splitParticipantService;

    public SplitParticipantController(
            SplitParticipantService splitParticipantService) {

        this.splitParticipantService = splitParticipantService;
    }

    /**
     * Allows an authenticated registered user to join
     * a PUBLIC group QR session.
     *
     * User identity is obtained from JWT authentication.
     * The request cannot specify another user's ID.
     */
    @PostMapping("/join/registered")
    public ResponseEntity<SplitParticipantService.ParticipantDetails>
            joinRegisteredUser(
                    @RequestBody RegisteredJoinRequest request) {

        if (request == null
                || request.qrToken() == null
                || request.qrToken().isBlank()) {

            throw new IllegalArgumentException(
                    "QR token is required");
        }

        SplitParticipantService.ParticipantDetails response =
                splitParticipantService.joinRegisteredUser(
                        request.qrToken());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    public record RegisteredJoinRequest(
            String qrToken
    ) {
    }
}
