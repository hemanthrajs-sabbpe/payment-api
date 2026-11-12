
package com.example.payment_api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class SplitParticipantService {

    private static final String ACTIVE = "ACTIVE";
    private static final String JOINED = "JOINED";
    private static final String INVITED = "INVITED";
    private static final String MEMBER = "MEMBER";

    private final SplitSessionRepository sessionRepository;
    private final SplitParticipantRepository participantRepository;
    private final PaymentSplitRepository splitRepository;
    private final CurrentUserService currentUserService;

    public SplitParticipantService(
            SplitSessionRepository sessionRepository,
            SplitParticipantRepository participantRepository,
            PaymentSplitRepository splitRepository,
            CurrentUserService currentUserService) {

        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.splitRepository = splitRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Allows an authenticated registered user to join an active
     * PUBLIC QR session.
     *
     * The session row is locked before checking participant capacity
     * and creating or updating the participant.
     *
     * Joining does not create a payment or contribution allocation.
     */
    @Transactional
    public ParticipantDetails joinRegisteredUser(String qrToken) {

        if (qrToken == null || qrToken.isBlank()) {
            throw new IllegalArgumentException("QR token is required");
        }

        // Identify the user from the authenticated JWT context.
        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user ID is unavailable");
        }

        // Hash the supplied token. The raw QR token is never stored.
        String tokenHash = hashToken(qrToken.trim());

        // Acquire a write lock to serialize joins for this session.
        SplitSession session = sessionRepository
                .findByQrTokenHashForUpdate(tokenHash)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid QR token"));

        validateSession(session);

        // Public QR joining is not allowed for invitation-only sessions.
        if (!"PUBLIC".equalsIgnoreCase(session.getJoinPolicy())) {
            throw new SecurityException(
                    "This session requires a valid invitation");
        }

        // Verify the parent split still exists and is joinable.
        PaymentSplit split = splitRepository.findById(session.getSplitId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "The parent split does not exist"));

        validateSplit(split);

        Long splitId = split.getId();
        Long userId = currentUser.getId();

        // Prevent the same registered user from joining the split twice.
        SplitParticipant existing = participantRepository
                .findBySplitIdAndUserId(splitId, userId)
                .orElse(null);

        // An already joined participant receives an idempotent response.
        if (existing != null
                && JOINED.equalsIgnoreCase(existing.getJoinStatus())) {

            return toDetails(existing);
        }

        // Only successfully joined participants count toward capacity.
        Integer maximum = session.getMaxParticipants();

        if (maximum != null) {

            long joinedCount =
                    participantRepository.countBySplitIdAndJoinStatus(
                            splitId, JOINED);

            // An existing INVITED participant is not yet counted.
            if (joinedCount >= maximum) {
                throw new IllegalStateException(
                        "This group has reached its participant limit");
            }
        }

        SplitParticipant participant;

        if (existing != null) {

            String existingStatus = existing.getJoinStatus() == null
                    ? ""
                    : existing.getJoinStatus()
                            .trim()
                            .toUpperCase(Locale.ROOT);

            // Only an invitation may be converted into a joined participant.
            if (!INVITED.equals(existingStatus)) {
                throw new IllegalStateException(
                        "Existing participant cannot rejoin from status: "
                                + existingStatus);
            }

            participant = existing;

        } else {

            participant = new SplitParticipant();
            participant.setSplitId(splitId);
            participant.setUserId(userId);
            participant.setDisplayName(currentUser.getUsername());
            participant.setParticipantRole(MEMBER);
        }

        participant.setJoinStatus(JOINED);
        participant.setJoinedAt(LocalDateTime.now());

        SplitParticipant saved =
                participantRepository.save(participant);

        return toDetails(saved);
    }

    private void validateSession(SplitSession session) {

        if (!ACTIVE.equalsIgnoreCase(session.getSessionStatus())) {
            throw new IllegalStateException(
                    "QR session is not active");
        }

        if (session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {

            throw new IllegalStateException(
                    "QR session has expired");
        }

        if (session.getSplitId() == null) {
            throw new IllegalStateException(
                    "QR session has no parent split");
        }
    }

    private void validateSplit(PaymentSplit split) {

        String status = split.getStatus() == null
                ? ""
                : split.getStatus().trim().toUpperCase(Locale.ROOT);

        if ("PAID".equals(status)
                || "CANCELLED".equals(status)
                || "CLOSED".equals(status)) {

            throw new IllegalStateException(
                    "This split is no longer accepting participants");
        }
    }

    private String hashToken(String rawToken) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is not available", exception);
        }
    }

    private ParticipantDetails toDetails(
            SplitParticipant participant) {

        return new ParticipantDetails(
                participant.getId(),
                participant.getSplitId(),
                participant.getUserId(),
                participant.getDisplayName(),
                participant.getParticipantRole(),
                participant.getJoinStatus(),
                participant.getJoinedAt()
        );
    }

    public record ParticipantDetails(
            Long participantId,
            Long splitId,
            Long userId,
            String displayName,
            String participantRole,
            String joinStatus,
            LocalDateTime joinedAt) {
    }
}
