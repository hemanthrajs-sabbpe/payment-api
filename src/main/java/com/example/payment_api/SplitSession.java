
package com.example.payment_api;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "split_sessions")
public class SplitSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "split_id", nullable = false)
    private Long splitId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "session_code", nullable = false, unique = true, length = 100)
    private String sessionCode;

    @Column(name = "qr_token_hash", nullable = false, unique = true, length = 255)
    private String qrTokenHash;

    @Column(name = "qr_token_version", nullable = false)
    private Integer qrTokenVersion = 1;

    @Column(name = "session_status", nullable = false, length = 20)
    private String sessionStatus = "ACTIVE";

    @Column(name = "join_policy", nullable = false, length = 30)
    private String joinPolicy = "PUBLIC";

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SplitSession() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSplitId() {
        return splitId;
    }

    public void setSplitId(Long splitId) {
        this.splitId = splitId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getQrTokenHash() {
        return qrTokenHash;
    }

    public void setQrTokenHash(String qrTokenHash) {
        this.qrTokenHash = qrTokenHash;
    }

    public Integer getQrTokenVersion() {
        return qrTokenVersion;
    }

    public void setQrTokenVersion(Integer qrTokenVersion) {
        this.qrTokenVersion = qrTokenVersion;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public String getJoinPolicy() {
        return joinPolicy;
    }

    public void setJoinPolicy(String joinPolicy) {
        this.joinPolicy = joinPolicy;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
