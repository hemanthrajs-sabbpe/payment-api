package com.example.payment_api;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating the QR Payment Simulator.
 * Provides allocation-bound cryptographic token generation, HMAC verification,
 * and canonical pessimistic locking across splits, allocations, sessions, and payments.
 */
@Service
public class QrSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(QrSimulatorService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";
    public static final String SIMULATOR_POLICY = "ALLOCATION_SIMULATOR";
    public static final String SIMULATOR_ORDER_PREFIX = "SIM-ORD-";
    public static final String SIMULATOR_TXN_PREFIX = "SIM-TXN-";

    private final SecureRandom secureRandom = new SecureRandom();

    private final PaymentSplitRepository splitRepository;
    private final PaymentSplitAllocationRepository allocationRepository;
    private final SplitSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitService paymentSplitService;

    @Value("${app.simulator.enabled:false}")
    private boolean simulatorEnabled;

    @Value("${simulator.token.secret}")
    private String tokenSecret;

    @Value("${sabbpe.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private byte[] decodedSecretKeyBytes;

    public QrSimulatorService(
            PaymentSplitRepository splitRepository,
            PaymentSplitAllocationRepository allocationRepository,
            SplitSessionRepository sessionRepository,
            PaymentRepository paymentRepository,
            PaymentSplitService paymentSplitService) {
        this.splitRepository = splitRepository;
        this.allocationRepository = allocationRepository;
        this.sessionRepository = sessionRepository;
        this.paymentRepository = paymentRepository;
        this.paymentSplitService = paymentSplitService;
    }

    /**
     * Validates that the configured simulator secret is a valid 64-character hex string (32 bytes)
     * and initializes the decoded key bytes at startup.
     */
    @PostConstruct
    public void initSecretKey() {
        if (tokenSecret == null || tokenSecret.isBlank()) {
            throw new IllegalStateException("simulator.token.secret is required");
        }
        String cleanSecret = tokenSecret.trim();
        if (cleanSecret.length() != 64 || !cleanSecret.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalStateException("simulator.token.secret must be a 64-character hexadecimal string representing 32 bytes");
        }
        try {
            this.decodedSecretKeyBytes = HexFormat.of().parseHex(cleanSecret);
            if (this.decodedSecretKeyBytes.length != 32) {
                throw new IllegalStateException("Decoded simulator.token.secret length must be exactly 32 bytes");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to parse hexadecimal simulator.token.secret", ex);
        }
    }

    /**
     * Checks if simulator is enabled.
     */
    public void ensureSimulatorEnabled() {
        if (!simulatorEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "QR Payment Simulator is disabled");
        }
    }

    // =========================================================================
    // 1. INITIATE QR & COMMITTED PENDING PAYMENT ATTEMPT
    // =========================================================================

    /**
     * Creates an allocation-bound QR session and persisted PENDING payment attempt.
     * Follows top-down pessimistic locking order:
     * PaymentSplit -> PaymentSplitAllocation -> (SplitSession -> Payment if finalizing prior attempt).
     */
    @Transactional
    public QrInitiationResult initiateAllocationQr(Long splitId, Long allocationId) {
        ensureSimulatorEnabled();

        if (splitId == null || allocationId == null) {
            throw new IllegalArgumentException("Split ID and Allocation ID are required");
        }

        try {
            // 1. Verify authorization using existing split access rules
            paymentSplitService.authorizeSplitAccess(splitId);

            // 2. Lock 1: PaymentSplit first (Hierarchical top lock)
            PaymentSplit split = splitRepository.findByIdForUpdate(splitId)
                    .orElseThrow(() -> new IllegalArgumentException("Split not found: " + splitId));

            if ("PAID".equalsIgnoreCase(split.getStatus())) {
                throw new IllegalStateException("Split is already fully paid");
            }

            // 3. Lock 2: PaymentSplitAllocation second
            PaymentSplitAllocation allocation = allocationRepository.findByIdAndSplitIdForUpdate(allocationId, splitId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Allocation " + allocationId + " not found for split " + splitId));

            if ("SUCCESS".equalsIgnoreCase(allocation.getStatus())) {
                throw new IllegalStateException("Allocation is already paid");
            }

            BigDecimal allocationAmount = allocation.getAllocationAmount();
            if (allocationAmount == null || allocationAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Invalid allocation amount");
            }
            allocationAmount = allocationAmount.setScale(2, RoundingMode.HALF_UP);

            // 4. Lock 3 & Lock 4 (if retrying): Expire only THIS allocation's prior simulator session
            // and finalize only verified simulator payment (requires BOTH checks to pass: AND logic)
            if (allocation.getPaymentId() != null) {
                finalizePreviousPendingAttempt(splitId, allocation);
            }

            // 5. Generate secure allocation-bound cryptographic token
            String rawEntropy = generateEntropy(32); // 256 bits of entropy
            String signature = computeHmac(allocationId, splitId, rawEntropy);
            String rawToken = String.format("%s.%s.%s",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(allocationId).getBytes(StandardCharsets.UTF_8)),
                    rawEntropy,
                    signature);

            String tokenHash = hashToken(rawToken);

            // 6. Create allocation SplitSession (joinPolicy = ALLOCATION_SIMULATOR)
            // Generate full 128-bit random attempt identifier (32-character hex UUID without hyphens)
            String attemptIdentifier = UUID.randomUUID().toString().replace("-", "").toUpperCase();
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
            SplitSession session = new SplitSession();
            session.setSplitId(splitId);
            session.setCreatedByUserId(split.getCreatedByUserId());
            session.setSessionCode("SIM-ALC-" + allocationId + "-" + attemptIdentifier);
            session.setQrTokenHash(tokenHash);
            session.setQrTokenVersion(1);
            session.setSessionStatus("ACTIVE");
            session.setJoinPolicy(SIMULATOR_POLICY);
            session.setMaxParticipants(1);
            session.setExpiresAt(expiresAt);
            session = sessionRepository.save(session);

            // 7. Create persisted Payment attempt in PENDING status
            Payment payment = new Payment(
                    allocation.getParticipantName(),
                    allocationAmount.doubleValue(),
                    "PENDING"
            );
            String merchantOrderRef = SIMULATOR_ORDER_PREFIX + attemptIdentifier;
            String transactionId = SIMULATOR_TXN_PREFIX + UUID.randomUUID();
            payment.setMerchantOrderRef(merchantOrderRef);
            payment.setTransactionId(transactionId);
            payment = paymentRepository.save(payment);

            // 8. Link Payment to Allocation
            allocation.setPaymentId(payment.getId());
            allocation.setStatus("PENDING");
            allocationRepository.save(allocation);

            String scanUrl = frontendUrl.replaceAll("/+$", "") + "/qr-pay?token=" + rawToken;

            log.info("Initiated QR simulation attempt for splitId={}, allocationId={}, paymentId={}, expiresAt={}",
                    splitId, allocationId, payment.getId(), expiresAt);

            return new QrInitiationResult(
                    splitId,
                    allocationId,
                    allocation.getParticipantName(),
                    allocationAmount,
                    payment.getId(),
                    rawToken,
                    scanUrl,
                    expiresAt
            );
        } catch (PessimisticLockingFailureException ex) {
            log.warn("Database lock acquisition timed out or deadlocked during initiateAllocationQr for splitId={}, allocationId={}: {}",
                    splitId, allocationId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment allocation is currently busy or being updated by another request. Please try again.", ex);
        }
    }

    /**
     * Finalizes prior uncompleted pending simulator attempt for THIS specific allocation as TIMEOUT.
     * Enforces strict lock hierarchy: SplitSession -> Payment.
     * Requires BOTH checks to pass (AND logic):
     * 1. An active ALLOCATION_SIMULATOR session exists specifically for this allocation and is locked/revalidated.
     * 2. The payment has a valid simulator reference (SIM-ORD- or SIM-TXN-) and no gateway reference.
     * If ANY check fails, leaves BOTH session and payment completely unchanged.
     */
    private void finalizePreviousPendingAttempt(Long splitId, PaymentSplitAllocation allocation) {
        Long previousPaymentId = allocation.getPaymentId();
        if (previousPaymentId == null) return;

        // Step A: Check candidate session for this specific allocation
        String allocationSessionCodePrefix = "SIM-ALC-" + allocation.getId() + "-";
        List<SplitSession> sessions = sessionRepository.findBySplitId(splitId);
        SplitSession targetSimulatorSession = null;

        for (SplitSession s : sessions) {
            if (SIMULATOR_POLICY.equalsIgnoreCase(s.getJoinPolicy())
                    && s.getSessionCode() != null
                    && s.getSessionCode().startsWith(allocationSessionCodePrefix)
                    && "ACTIVE".equalsIgnoreCase(s.getSessionStatus())) {
                targetSimulatorSession = s;
                break;
            }
        }

        // If no candidate active simulator session exists, skip safely without modifying payment
        if (targetSimulatorSession == null) {
            log.info("No active simulator session found for allocation {}; skipping payment finalization to protect existing records",
                    allocation.getId());
            return;
        }

        // Step B: Lock 3 - SplitSession. Lock and revalidate ALL session criteria
        SplitSession lockedSession = sessionRepository.findByIdForUpdate(targetSimulatorSession.getId())
                .orElse(null);

        if (lockedSession == null
                || !SIMULATOR_POLICY.equalsIgnoreCase(lockedSession.getJoinPolicy())
                || !splitId.equals(lockedSession.getSplitId())
                || lockedSession.getSessionCode() == null
                || !lockedSession.getSessionCode().startsWith(allocationSessionCodePrefix)
                || !"ACTIVE".equalsIgnoreCase(lockedSession.getSessionStatus())) {
            log.warn("Candidate simulator session {} for allocation {} failed post-lock revalidation; leaving both session and payment unchanged",
                    targetSimulatorSession.getId(), allocation.getId());
            return;
        }

        // Step C: Lock 4 - Payment. Lock and revalidate ALL payment criteria
        Payment lockedPayment = paymentRepository.findByIdForUpdate(previousPaymentId).orElse(null);

        if (lockedPayment == null) {
            log.warn("Previous payment {} referenced by allocation {} was not found; leaving session unchanged",
                    previousPaymentId, allocation.getId());
            return;
        }

        boolean hasSimulatorReference =
                (lockedPayment.getMerchantOrderRef() != null && lockedPayment.getMerchantOrderRef().startsWith(SIMULATOR_ORDER_PREFIX))
                || (lockedPayment.getTransactionId() != null && lockedPayment.getTransactionId().startsWith(SIMULATOR_TXN_PREFIX));

        // Safeguard: Verify it is NOT a SabbPe PBL (no pblOrderRef and no SABBPE- prefix) and NOT Easebuzz (no TXN- without SIM-)
        boolean isGatewayPayment =
                (lockedPayment.getPblOrderRef() != null && !lockedPayment.getPblOrderRef().isBlank())
                || (lockedPayment.getMerchantOrderRef() != null && lockedPayment.getMerchantOrderRef().startsWith("SABBPE-"))
                || (lockedPayment.getTransactionId() != null && lockedPayment.getTransactionId().startsWith("TXN-")
                    && !lockedPayment.getTransactionId().startsWith(SIMULATOR_TXN_PREFIX));

        // Step D: EXACT ATTEMPT BINDING VERIFICATION:
        // Extract the complete 128-bit attempt identifier from the sessionCode and compare exactly with merchantOrderRef.
        // Format of sessionCode: SIM-ALC-{allocationId}-{attemptIdentifier}
        // Format of merchantOrderRef: SIM-ORD-{attemptIdentifier}
        String sessionAttemptIdentifier = lockedSession.getSessionCode().substring(allocationSessionCodePrefix.length());
        String expectedMerchantOrderRef = SIMULATOR_ORDER_PREFIX + sessionAttemptIdentifier;

        boolean isExactAttemptMatched = lockedPayment.getMerchantOrderRef() != null
                && lockedPayment.getMerchantOrderRef().equals(expectedMerchantOrderRef);

        // ALL checks must pass (AND logic):
        // 1. Valid simulator reference on payment
        // 2. Not a gateway payment
        // 3. Exact attempt binding between complete identifier in sessionCode and merchantOrderRef matches
        if (hasSimulatorReference && !isGatewayPayment && isExactAttemptMatched) {
            // Expire session
            lockedSession.setSessionStatus("EXPIRED");
            sessionRepository.save(lockedSession);
            log.info("Expired previous active simulator session {} for allocation {}", lockedSession.getId(), allocation.getId());

            // Finalize payment as TIMEOUT if PENDING
            if ("PENDING".equalsIgnoreCase(lockedPayment.getStatus())) {
                lockedPayment.setStatus("TIMEOUT");
                paymentRepository.save(lockedPayment);
                log.info("Finalized previous abandoned simulator payment attempt {} as TIMEOUT for allocation {}",
                        previousPaymentId, allocation.getId());
            }
        } else {
            log.warn("Previous payment {} for allocation {} failed exact simulator attempt binding check (hasSimRef={}, isGateway={}, exactMatch={}); left both unchanged",
                    previousPaymentId, allocation.getId(), hasSimulatorReference, isGatewayPayment, isExactAttemptMatched);
        }
    }

    // =========================================================================
    // 2. RETRIEVE SCANNED QR DETAILS (CUSTOMER FACING)
    // =========================================================================

    /**
     * Public inspection endpoint. Validates token cryptographically without locking.
     */
    @Transactional(readOnly = true)
    public QrPaymentDetails getScannedDetails(String rawToken) {
        ensureSimulatorEnabled();

        ParsedToken parsed = parseAndVerifyToken(rawToken);
        String tokenHash = hashToken(rawToken);

        SplitSession session = sessionRepository.findByQrTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR session not found or invalid"));

        if (!SIMULATOR_POLICY.equalsIgnoreCase(session.getJoinPolicy())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid QR session type");
        }

        if (!"ACTIVE".equalsIgnoreCase(session.getSessionStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "QR session is no longer active");
        }

        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "QR code has expired");
        }

        PaymentSplit split = splitRepository.findById(session.getSplitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Associated split not found"));

        PaymentSplitAllocation allocation = allocationRepository.findByIdAndSplitId(parsed.allocationId(), split.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Associated allocation not found"));

        // Verify that the session belongs specifically to this allocation
        String expectedSessionPrefix = "SIM-ALC-" + allocation.getId() + "-";
        if (session.getSessionCode() == null || !session.getSessionCode().startsWith(expectedSessionPrefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR session does not belong to this allocation");
        }

        // Validate attempt identifier is exactly 32 hexadecimal characters
        String attemptIdentifier = session.getSessionCode().substring(expectedSessionPrefix.length());
        if (attemptIdentifier.length() != 32 || !attemptIdentifier.matches("^[0-9A-F]{32}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attempt identifier in QR session code");
        }

        Long paymentId = allocation.getPaymentId();
        if (paymentId == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "No payment attempt linked to this allocation");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Associated payment attempt not found"));

        // Verify that the current payment attempt matches this session's exact attempt identifier
        String expectedMerchantOrderRef = SIMULATOR_ORDER_PREFIX + attemptIdentifier;
        if (payment.getMerchantOrderRef() == null || !payment.getMerchantOrderRef().equals(expectedMerchantOrderRef)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This QR code corresponds to a replaced or expired payment attempt");
        }

        String paymentStatus = payment.getStatus() != null ? payment.getStatus() : allocation.getStatus();

        return new QrPaymentDetails(
                split.getSplitReference() != null ? split.getSplitReference() : ("Split #" + split.getId()),
                allocation.getParticipantName(),
                allocation.getAllocationAmount(),
                split.getCurrencyCode() != null ? split.getCurrencyCode() : "INR",
                paymentId,
                paymentStatus,
                session.getExpiresAt()
        );
    }

    // =========================================================================
    // 3. OUTCOME PROCESSING: SUCCESS, FAILED, TIMEOUT
    // =========================================================================

    /**
     * Executes outcome simulation with strict canonical locking order:
     * PaymentSplit -> PaymentSplitAllocation -> SplitSession -> Payment.
     * Returns QrProcessResult cleanly with status TIMEOUT, SUCCESS, or FAILED.
     * When TIMEOUT occurs, the transaction commits successfully without throwing an exception.
     */
    @Transactional
    public QrProcessResult processOutcome(String rawToken, Long paymentId, String action) {
        ensureSimulatorEnabled();

        if (action == null || (!"SUCCESS".equalsIgnoreCase(action) && !"FAILURE".equalsIgnoreCase(action))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action. Supported: SUCCESS, FAILURE");
        }

        // 1. Non-locking token decode & validation to discover identifiers
        ParsedToken parsed = parseAndVerifyToken(rawToken);
        String tokenHash = hashToken(rawToken);

        SplitSession readSession = sessionRepository.findByQrTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR session not found"));

        Long splitId = readSession.getSplitId();
        Long allocationId = parsed.allocationId();

        try {
            // 2. Acquire locks in strict hierarchical canonical order:
            // Lock 1: PaymentSplit
            PaymentSplit split = splitRepository.findByIdForUpdate(splitId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Split not found: " + splitId));

            // Lock 2: PaymentSplitAllocation
            PaymentSplitAllocation allocation = allocationRepository.findByIdAndSplitIdForUpdate(allocationId, splitId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allocation not found"));

            // Lock 3: SplitSession
            SplitSession session = sessionRepository.findByQrTokenHashForUpdate(tokenHash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session lock failed"));

            // Lock 4: Payment
            Long targetPaymentId = paymentId != null ? paymentId : allocation.getPaymentId();
            if (targetPaymentId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active payment attempt linked to this allocation");
            }

            Payment payment = paymentRepository.findByIdForUpdate(targetPaymentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment attempt not found: " + targetPaymentId));

            // 3. Revalidate all relationships and states under lock
            if (!SIMULATOR_POLICY.equalsIgnoreCase(session.getJoinPolicy())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid QR session policy");
            }
            if (!allocation.getId().equals(parsed.allocationId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocation mismatch with token");
            }
            if (!split.getId().equals(allocation.getSplitId()) || !session.getSplitId().equals(split.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session or allocation does not belong to split");
            }
            if (!payment.getId().equals(allocation.getPaymentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment is not linked to this allocation");
            }

            // Verify session belongs specifically to this allocation
            String expectedSessionPrefix = "SIM-ALC-" + allocation.getId() + "-";
            if (session.getSessionCode() == null || !session.getSessionCode().startsWith(expectedSessionPrefix)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR session does not belong to this allocation");
            }

            // Validate attempt identifier is exactly 32 hexadecimal characters
            String attemptIdentifier = session.getSessionCode().substring(expectedSessionPrefix.length());
            if (attemptIdentifier.length() != 32 || !attemptIdentifier.matches("^[0-9A-F]{32}$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attempt identifier in QR session code");
            }

            // Verify exact attempt identifier binding with payment's merchantOrderRef
            String expectedMerchantOrderRef = SIMULATOR_ORDER_PREFIX + attemptIdentifier;
            if (payment.getMerchantOrderRef() == null || !payment.getMerchantOrderRef().equals(expectedMerchantOrderRef)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The scanned QR session does not match this payment attempt. It may have been replaced by a newer attempt.");
            }

            // Check terminal state
            String currentPaymentStatus = payment.getStatus() != null ? payment.getStatus().toUpperCase() : "";
            if ("SUCCESS".equals(currentPaymentStatus) || "FAILED".equals(currentPaymentStatus) || "TIMEOUT".equals(currentPaymentStatus)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Payment attempt " + targetPaymentId + " has already terminated with status: " + currentPaymentStatus);
            }
            if (!"PENDING".equals(currentPaymentStatus)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment attempt is not in PENDING state: " + currentPaymentStatus);
            }

            // Expiry check (expiresAt <= now)
            LocalDateTime now = LocalDateTime.now();
            if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
                // Finalize as TIMEOUT, expire session, and commit cleanly without throwing
                session.setSessionStatus("EXPIRED");
                sessionRepository.save(session);

                payment.setStatus("TIMEOUT");
                paymentRepository.save(payment);

                paymentSplitService.recordFailedPayment(splitId, allocationId, payment.getId(), "TIMEOUT");
                log.info("Payment attempt {} and session {} timed out at {}", targetPaymentId, session.getId(), now);

                BigDecimal timeoutAmount = allocation.getAllocationAmount() != null
                        ? allocation.getAllocationAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                return new QrProcessResult(
                        "TIMEOUT",
                        payment.getId(),
                        allocation.getId(),
                        timeoutAmount,
                        "QR code has expired. Attempt recorded as TIMEOUT."
                );
            }

            if (!"ACTIVE".equalsIgnoreCase(session.getSessionStatus())) {
                throw new ResponseStatusException(HttpStatus.GONE, "QR session is no longer active");
            }

            // 4. Process Outcome
            BigDecimal finalAmount = allocation.getAllocationAmount().setScale(2, RoundingMode.HALF_UP);

            if ("SUCCESS".equalsIgnoreCase(action)) {
                payment.setStatus("SUCCESS");
                paymentRepository.save(payment);

                // Marks allocation SUCCESS, updates paidAmount, and recalculates master split
                paymentSplitService.recordSuccessfulPayment(splitId, allocationId, payment.getId());

                // Close session as completed
                session.setSessionStatus("CLOSED");
                sessionRepository.save(session);

                log.info("Simulated payment SUCCESS for splitId={}, allocationId={}, paymentId={}, amount={}",
                        splitId, allocationId, payment.getId(), finalAmount);

                return new QrProcessResult(
                        "SUCCESS",
                        payment.getId(),
                        allocation.getId(),
                        finalAmount,
                        "Payment completed successfully"
                );
            } else {
                payment.setStatus("FAILED");
                paymentRepository.save(payment);

                // Record failure; paidAmount remains ZERO; allocation eligible for retry
                paymentSplitService.recordFailedPayment(splitId, allocationId, payment.getId(), "FAILED");

                session.setSessionStatus("EXPIRED");
                sessionRepository.save(session);

                log.info("Simulated payment FAILED for splitId={}, allocationId={}, paymentId={}, amount={}",
                        splitId, allocationId, payment.getId(), finalAmount);

                return new QrProcessResult(
                        "FAILED",
                        payment.getId(),
                        allocation.getId(),
                        finalAmount,
                        "Payment attempt simulated as failed"
                );
            }
        } catch (PessimisticLockingFailureException ex) {
            log.warn("Database lock acquisition timed out or deadlocked during processOutcome for splitId={}, allocationId={}: {}",
                    splitId, allocationId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment allocation is currently busy or being updated by another request. Please try again.", ex);
        }
    }

    // =========================================================================
    // 4. CRYPTOGRAPHIC TOKEN GENERATION & CONSTANT-TIME VERIFICATION
    // =========================================================================

    private String generateEntropy(int numBytes) {
        byte[] bytes = new byte[numBytes];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String computeHmac(Long allocationId, Long splitId, String entropy) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(this.decodedSecretKeyBytes, HMAC_SHA256);
            mac.init(keySpec);
            String payload = allocationId + ":" + splitId + ":" + entropy;
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate token HMAC", e);
        }
    }

    /**
     * Parses the allocation-bound raw token and validates signature using constant-time comparison.
     */
    public ParsedToken parseAndVerifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token is required");
        }

        String[] parts = rawToken.trim().split("\\.");
        if (parts.length != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed QR token structure");
        }

        Long allocationId;
        try {
            byte[] allocBytes = Base64.getUrlDecoder().decode(parts[0]);
            allocationId = Long.parseLong(new String(allocBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid allocation ID encoding in token");
        }

        String entropy = parts[1];
        String providedSignature = parts[2];

        // Discover splitId from allocation record to recompute signature
        PaymentSplitAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocation not found for token"));

        Long splitId = allocation.getSplitId();
        String expectedSignature = computeHmac(allocationId, splitId, entropy);

        // Constant-time signature verification using MessageDigest.isEqual
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedSignature.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            log.warn("HMAC verification failed for allocationId={}, token tampering detected", allocationId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or tampered QR token");
        }

        return new ParsedToken(allocationId, splitId, entropy);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // =========================================================================
    // DTO RECORDS
    // =========================================================================

    public record ParsedToken(Long allocationId, Long splitId, String entropy) {}

    public record QrInitiationResult(
            Long splitId,
            Long allocationId,
            String participantName,
            BigDecimal amount,
            Long paymentId,
            String qrToken,
            String scanUrl,
            LocalDateTime expiresAt
    ) {}

    public record QrPaymentDetails(
            String splitTitle,
            String participantName,
            BigDecimal amount,
            String currency,
            Long paymentId,
            String status,
            LocalDateTime expiresAt
    ) {}

    public record QrProcessResult(
            String status,
            Long paymentId,
            Long allocationId,
            BigDecimal amount,
            String message
    ) {}
}
