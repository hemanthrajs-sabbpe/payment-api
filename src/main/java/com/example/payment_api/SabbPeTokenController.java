
package com.example.payment_api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sabbpe")
public class SabbPeTokenController {

    private final SabbPeTokenService sabbPeTokenService;

    public SabbPeTokenController(
            SabbPeTokenService sabbPeTokenService) {
        this.sabbPeTokenService = sabbPeTokenService;
    }

    /**
     * Test endpoint for generating a SabbPe authentication token.
     *
     * POST /api/sabbpe/token
     */
    @PostMapping("/token")
    public ResponseEntity<SabbPeTokenService.SabbPeTokenResponse>
            generateToken(@RequestBody TokenRequest request) {

        SabbPeTokenService.SabbPeTokenResponse response =
                sabbPeTokenService.generateToken(
                        request.merchantOrderRef()
                );

        return ResponseEntity.ok(response);
    }

    public record TokenRequest(String merchantOrderRef) {
    }
}
