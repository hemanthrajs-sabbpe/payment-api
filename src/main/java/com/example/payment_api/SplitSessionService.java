
package com.example.payment_api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;

@Service
public class SplitSessionService {

    private static final String ACTIVE = "ACTIVE";
    private static final String EXPIRED = "EXPIRED";
    private static final String CLOSED = "CLOSED";

    private static final String PUBLIC = "PUBLIC";
    private static final String INVITE_ONLY = "INVITE_ONLY";

    private static final String ORGANIZATION_SCOPE = "ORGANIZATION";
    private static final String BRANCH_SCOPE = "BRANCH";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SplitSessionRepository sessionRepository;
    private final PaymentSplitRepository splitRepository;
    private final PaymentSplitService paymentSplitService;
    private final CurrentUserService currentUserService;
    private final BusinessMemberRepository businessMemberRepository;
    private final BusinessBranchRepository businessBranchRepository;
    private final BusinessMemberBranchRepository memberBranchRepository;

    public SplitSessionService(
            SplitSessionRepository sessionRepository,
            PaymentSplitRepository splitRepository,
            PaymentSplitService paymentSplitService,
            CurrentUserService currentUserService,
            BusinessMemberRepository businessMemberRepository,
            BusinessBranchRepository businessBranchRepository,
            BusinessMemberBranchRepository memberBranchRepository) {

        this.sessionRepository = sessionRepository;
        this.splitRepository = splitRepository;
        this.paymentSplitService = paymentSplitService;
        this.currentUserService = currentUserService;
        this.businessMemberRepository = businessMemberRepository;
        this.businessBranchRepository = businessBranchRepository;
        this.memberBranchRepository = memberBranchRepository;
    }

    /**
     * Creates one active QR session for a split.
     *
     * The parent split is locked first so concurrent session-creation
     * requests for the same split cannot both create an active session.
     */
    @Transactional
    public CreatedSession createSession(
            Long splitId,
            Long branchId,
            String joinPolicy,
            Integer maxParticipants,
            LocalDateTime expiresAt) {

        if (splitId == null) {
            throw new IllegalArgumentException("Split ID is required");
        }

        PaymentSplit lockedSplit = splitRepository.findByIdForUpdate(splitId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Split not found: " + splitId));

        // Reuse the existing authenticated-user authorization rules.
        PaymentSplit split =
                paymentSplitService.getSplitByIdForCurrentUser(splitId);

        if (!lockedSplit.getId().equals(split.getId())) {
            throw new IllegalStateException("Split validation failed");
        }

        validateSplitCanHaveSession(split);

        Long currentUserId = currentUserService.getCurrentUserId();

        validateBranchAccess(split, branchId, currentUserId);

        String normalizedJoinPolicy = normalizeJoinPolicy(joinPolicy);

        if (maxParticipants != null && maxParticipants <= 0) {
            throw new IllegalArgumentException(
                    "Maximum participants must be greater than zero");
        }

        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Session expiry must be a future date and time");
        }

        // Expire stale sessions and reject an existing unexpired active session.
        List<SplitSession> existingSessions =
                sessionRepository.findGroupSessionsBySplitIdAndStatusForUpdate(
                        splitId, ACTIVE);

        LocalDateTime now = LocalDateTime.now();

        for (SplitSession existing : existingSessions) {

            if (existing.getExpiresAt() == null
                    || !existing.getExpiresAt().isAfter(now)) {

                existing.setSessionStatus(EXPIRED);
                sessionRepository.save(existing);

            } else {
                throw new IllegalStateException(
                        "An active QR session already exists for this split");
            }
        }

        String rawToken = generateSecureToken();

        SplitSession session = new SplitSession();
        session.setSplitId(splitId);
        session.setBranchId(branchId);
        session.setCreatedByUserId(currentUserId);
        session.setSessionCode(generateSessionCode());
        session.setQrTokenHash(hashToken(rawToken));
        session.setQrTokenVersion(1);
        session.setSessionStatus(ACTIVE);
        session.setJoinPolicy(normalizedJoinPolicy);
        session.setMaxParticipants(maxParticipants);
        session.setExpiresAt(expiresAt);

        SplitSession savedSession = sessionRepository.save(session);

        // Return the original token once. Only its hash is stored in the DB.
        return toCreatedSession(savedSession, rawToken);
    }

    /**
     * Validates a QR token for the public joining workflow.
     *
     * This method does not create a participant or initiate a payment.
     */
    @Transactional(readOnly = true)
    public ValidatedSession validateQrToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("QR token is required");
        }

        String tokenHash = hashToken(rawToken.trim());

        SplitSession session = sessionRepository.findByQrTokenHash(tokenHash)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid QR token"));

        if (!ACTIVE.equalsIgnoreCase(session.getSessionStatus())) {
            throw new IllegalStateException("QR session is not active");
        }

        if (session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("QR session has expired");
        }

        return new ValidatedSession(
                session.getId(),
                session.getSplitId(),
                session.getSessionCode(),
                session.getQrTokenVersion(),
                session.getJoinPolicy(),
                session.getExpiresAt()
        );
    }

    /**
     * Regenerates the QR token for an existing session.
     *
     * Existing participants and payment records are not deleted.
     * The old token becomes invalid because its hash is replaced.
     */
    @Transactional
    public CreatedSession regenerateQrToken(Long sessionId) {

        SplitSession session = getLockedAuthorizedSession(sessionId);

        if (!ACTIVE.equalsIgnoreCase(session.getSessionStatus())) {
            throw new IllegalStateException(
                    "Only an active session can regenerate its QR token");
        }

        if (session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {

            session.setSessionStatus(EXPIRED);
            sessionRepository.save(session);

            throw new IllegalStateException(
                    "Expired session cannot regenerate its QR token");
        }

        String rawToken = generateSecureToken();

        Integer currentVersion = session.getQrTokenVersion();

        if (currentVersion == null || currentVersion < 1) {
            throw new IllegalStateException(
                    "Invalid QR token version stored for this session");
        }

        if (currentVersion == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "QR token version limit reached");
        }

        session.setQrTokenHash(hashToken(rawToken));
        session.setQrTokenVersion(currentVersion + 1);

        SplitSession savedSession = sessionRepository.save(session);

        return toCreatedSession(savedSession, rawToken);
    }

    /**
     * Closes an active QR session.
     *
     * Participants and payment records remain unchanged.
     */
    @Transactional
    public SessionDetails closeSession(Long sessionId) {

        SplitSession session = getLockedAuthorizedSession(sessionId);

        if (CLOSED.equalsIgnoreCase(session.getSessionStatus())) {
            return toSessionDetails(session);
        }

        if (!ACTIVE.equalsIgnoreCase(session.getSessionStatus())
                && !EXPIRED.equalsIgnoreCase(session.getSessionStatus())) {
            throw new IllegalStateException(
                    "Session cannot be closed from its current status");
        }

        session.setSessionStatus(CLOSED);

        return toSessionDetails(sessionRepository.save(session));
    }

    /**
     * Returns session details only to a user authorized to access its split.
     */
    @Transactional(readOnly = true)
    public SessionDetails getSession(Long sessionId) {

        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID is required");
        }

        SplitSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Session not found: " + sessionId));

        paymentSplitService.getSplitByIdForCurrentUser(session.getSplitId());

        return toSessionDetails(session);
    }

    /**
     * Lists sessions for a split after checking split authorization.
     */
    @Transactional(readOnly = true)
    public List<SessionDetails> getSessionsForSplit(Long splitId) {

        paymentSplitService.getSplitByIdForCurrentUser(splitId);

        return sessionRepository.findBySplitId(splitId)
                .stream()
                .map(this::toSessionDetails)
                .toList();
    }

    /**
     * Retrieves and locks a session, then checks the current user's
     * permission to manage its parent split.
     */
    private SplitSession getLockedAuthorizedSession(Long sessionId) {

        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID is required");
        }

        SplitSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Session not found: " + sessionId));

        paymentSplitService.getSplitByIdForCurrentUser(session.getSplitId());

        return session;
    }

    /**
     * Validates that the parent split is eligible for a new QR session.
     */
    private void validateSplitCanHaveSession(PaymentSplit split) {

        if (split == null) {
            throw new IllegalArgumentException("Split is required");
        }

        if ("PAID".equalsIgnoreCase(split.getStatus())) {
            throw new IllegalStateException(
                    "A fully paid split cannot have a new QR session");
        }

        if ("CANCELLED".equalsIgnoreCase(split.getStatus())
                || "CLOSED".equalsIgnoreCase(split.getStatus())) {
            throw new IllegalStateException(
                    "A cancelled or closed split cannot have a QR session");
        }

        String scope = split.getSplitScope() == null
                ? "PERSONAL"
                : split.getSplitScope().trim().toUpperCase(Locale.ROOT);

        if (!"PERSONAL".equals(scope) && !"BUSINESS".equals(scope)) {
            throw new IllegalArgumentException("Unsupported split scope");
        }

        if ("BUSINESS".equals(scope)
                && (split.getOrganizationId() == null
                || split.getOrganizationId().isBlank())) {
            throw new IllegalStateException(
                    "Business split has no organization association");
        }
    }

    /**
     * Validates branch ownership and branch-specific membership access.
     */
    private void validateBranchAccess(
            PaymentSplit split,
            Long branchId,
            Long currentUserId) {

        String scope = split.getSplitScope() == null
                ? "PERSONAL"
                : split.getSplitScope().trim().toUpperCase(Locale.ROOT);

        if ("PERSONAL".equals(scope)) {
            if (branchId != null) {
                throw new IllegalArgumentException(
                        "Personal split sessions cannot use a business branch");
            }
            return;
        }

        String organizationId = split.getOrganizationId();

        BusinessMember membership = businessMemberRepository
                .findByOrganizationIdAndUserId(organizationId, currentUserId)
                .orElseThrow(() ->
                        new SecurityException(
                                "Organization membership not found"));

        if (!"ACTIVE".equalsIgnoreCase(membership.getMembershipStatus())) {
            throw new SecurityException(
                    "Your organization membership is not active");
        }

        String role = membership.getMemberRole() == null
                ? ""
                : membership.getMemberRole().trim().toUpperCase(Locale.ROOT);

        if (!List.of("OWNER", "ADMIN", "MANAGER").contains(role)) {
            throw new SecurityException(
                    "Only OWNER, ADMIN, or MANAGER can manage business QR sessions");
        }

        String accessScope = membership.getAccessScope() == null
                ? ORGANIZATION_SCOPE
                : membership.getAccessScope().trim().toUpperCase(Locale.ROOT);

        if (!ORGANIZATION_SCOPE.equals(accessScope)
                && !BRANCH_SCOPE.equals(accessScope)) {
            throw new SecurityException(
                    "Unsupported organization membership access scope");
        }

        if (branchId == null) {
            if (BRANCH_SCOPE.equals(accessScope)) {
                throw new SecurityException(
                        "Branch-scoped members must select an assigned branch");
            }
            return;
        }

        BusinessBranch branch = businessBranchRepository
                .findByIdAndOrganizationId(branchId, organizationId)
                .orElseThrow(() ->
                        new SecurityException(
                                "Branch does not belong to this organization"));

        if (!"ACTIVE".equalsIgnoreCase(branch.getStatus())) {
            throw new IllegalStateException("Selected branch is not active");
        }

        if (BRANCH_SCOPE.equals(accessScope)) {

            BusinessMemberBranch assignment = memberBranchRepository
                    .findByMemberIdAndBranchId(membership.getId(), branchId)
                    .orElseThrow(() ->
                            new SecurityException(
                                    "You are not assigned to this branch"));

            if (!organizationId.equals(assignment.getOrganizationId())) {
                throw new SecurityException(
                        "Branch assignment organization mismatch");
            }

            if (!"ACTIVE".equalsIgnoreCase(
                    assignment.getAssignmentStatus())) {
                throw new SecurityException(
                        "Your branch assignment is not active");
            }
        }
    }

    private String normalizeJoinPolicy(String joinPolicy) {

        if (joinPolicy == null || joinPolicy.isBlank()) {
            return PUBLIC;
        }

        String normalized = joinPolicy.trim().toUpperCase(Locale.ROOT);

        if (!PUBLIC.equals(normalized)
                && !INVITE_ONLY.equals(normalized)) {
            throw new IllegalArgumentException(
                    "Join policy must be PUBLIC or INVITE_ONLY");
        }

        return normalized;
    }

    /**
     * Generates a cryptographically secure 256-bit random token.
     */
    private String generateSecureToken() {

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }

    /**
     * Generates a non-secret session reference.
     */
    private String generateSessionCode() {
        return "SPL-" + java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Stores only the SHA-256 digest of the QR token.
     */
    private String hashToken(String rawToken) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available", exception);
        }
    }

    private CreatedSession toCreatedSession(
            SplitSession session,
            String rawToken) {

        return new CreatedSession(
                session.getId(),
                session.getSplitId(),
                session.getBranchId(),
                session.getSessionCode(),
                rawToken,
                session.getQrTokenVersion(),
                session.getSessionStatus(),
                session.getJoinPolicy(),
                session.getMaxParticipants(),
                session.getExpiresAt()
        );
    }

    private SessionDetails toSessionDetails(SplitSession session) {

        return new SessionDetails(
                session.getId(),
                session.getSplitId(),
                session.getBranchId(),
                session.getCreatedByUserId(),
                session.getSessionCode(),
                session.getQrTokenVersion(),
                session.getSessionStatus(),
                session.getJoinPolicy(),
                session.getMaxParticipants(),
                session.getExpiresAt(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public record CreatedSession(
            Long sessionId,
            Long splitId,
            Long branchId,
            String sessionCode,
            String qrToken,
            Integer qrTokenVersion,
            String sessionStatus,
            String joinPolicy,
            Integer maxParticipants,
            LocalDateTime expiresAt) {
    }

    public record ValidatedSession(
            Long sessionId,
            Long splitId,
            String sessionCode,
            Integer qrTokenVersion,
            String joinPolicy,
            LocalDateTime expiresAt) {
    }

    public record SessionDetails(
            Long sessionId,
            Long splitId,
            Long branchId,
            Long createdByUserId,
            String sessionCode,
            Integer qrTokenVersion,
            String sessionStatus,
            String joinPolicy,
            Integer maxParticipants,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
