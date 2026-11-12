
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PaymentSplitAllocationRepository
        extends JpaRepository<PaymentSplitAllocation, Long> {

    List<PaymentSplitAllocation> findBySplitId(Long splitId);

    Optional<PaymentSplitAllocation> findByPaymentId(Long paymentId);

    // Retrieve an allocation only when it belongs to the specified split.
    Optional<PaymentSplitAllocation> findByIdAndSplitId(
            Long id,
            Long splitId
    );

    // Retrieve an allocation locked for update when it belongs to the specified split.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM PaymentSplitAllocation a
            WHERE a.id = :id
              AND a.splitId = :splitId
            """)
    Optional<PaymentSplitAllocation> findByIdAndSplitIdForUpdate(
            @Param("id") Long id,
            @Param("splitId") Long splitId
    );

    // Find allocations linked to a particular payment within a split.
    Optional<PaymentSplitAllocation> findBySplitIdAndPaymentId(
            Long splitId,
            Long paymentId
    );

    // Check whether a payment is already linked to an allocation.
    boolean existsBySplitIdAndPaymentId(
            Long splitId,
            Long paymentId
    );

    // Retrieve allocations by their payment status.
    List<PaymentSplitAllocation> findBySplitIdAndStatus(
            Long splitId,
            String status
    );
}
