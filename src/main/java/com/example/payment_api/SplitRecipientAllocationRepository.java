
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SplitRecipientAllocationRepository
        extends JpaRepository<SplitRecipientAllocation, Long> {

    List<SplitRecipientAllocation> findBySplitId(Long splitId);

    List<SplitRecipientAllocation> findByRecipientId(Long recipientId);

    List<SplitRecipientAllocation> findBySplitIdAndAllocationStatus(
            Long splitId,
            String allocationStatus
    );

    Optional<SplitRecipientAllocation> findBySplitIdAndRecipientId(
            Long splitId,
            Long recipientId
    );
}
