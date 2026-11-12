
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PaymentSplitRepository
        extends JpaRepository<PaymentSplit, Long> {

    Optional<PaymentSplit> findBySplitReference(String splitReference);

    // Retrieve splits created by a specific authenticated user.
    List<PaymentSplit> findByCreatedByUserId(Long createdByUserId);

    // Retrieve a split only when it belongs to the specified creator.
    Optional<PaymentSplit> findByIdAndCreatedByUserId(
            Long id,
            Long createdByUserId
    );

    // Check whether a split belongs to a specific creator.
    boolean existsByIdAndCreatedByUserId(
            Long id,
            Long createdByUserId
    );

    // Retrieve organization-owned splits.
    List<PaymentSplit> findByOrganizationId(String organizationId);

    /**
     * Locks the parent split while creating or managing its QR sessions.
     * Must be called from a transactional service method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentSplit p WHERE p.id = :splitId")
    Optional<PaymentSplit> findByIdForUpdate(
            @Param("splitId") Long splitId
    );
}
