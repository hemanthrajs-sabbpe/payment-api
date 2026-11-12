
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SplitRecipientRepository
        extends JpaRepository<SplitRecipient, Long> {

    List<SplitRecipient> findByOwnerUserIdAndRecipientStatus(
            Long ownerUserId,
            String recipientStatus
    );

    List<SplitRecipient> findByOrganizationIdAndRecipientStatus(
            String organizationId,
            String recipientStatus
    );

    List<SplitRecipient> findByBranchIdAndRecipientStatus(
            Long branchId,
            String recipientStatus
    );

    Optional<SplitRecipient> findByIdAndOwnerUserId(
            Long id,
            Long ownerUserId
    );

    Optional<SplitRecipient> findByIdAndOrganizationId(
            Long id,
            String organizationId
    );
}
