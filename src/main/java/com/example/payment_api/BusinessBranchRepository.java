
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BusinessBranchRepository
        extends JpaRepository<BusinessBranch, Long> {

    List<BusinessBranch> findByOrganizationId(String organizationId);

    List<BusinessBranch> findByOrganizationIdAndStatus(
            String organizationId,
            String status
    );

    Optional<BusinessBranch> findByOrganizationIdAndBranchCode(
            String organizationId,
            String branchCode
    );

    Optional<BusinessBranch> findByIdAndOrganizationId(
            Long id,
            String organizationId
    );

    boolean existsByOrganizationIdAndBranchCode(
            String organizationId,
            String branchCode
    );
}
