
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BusinessMemberBranchRepository
        extends JpaRepository<BusinessMemberBranch, Long> {

    List<BusinessMemberBranch> findByMemberId(Long memberId);

    List<BusinessMemberBranch> findByMemberIdAndAssignmentStatus(
            Long memberId,
            String assignmentStatus
    );

    List<BusinessMemberBranch> findByBranchId(Long branchId);

    List<BusinessMemberBranch> findByOrganizationId(String organizationId);

    Optional<BusinessMemberBranch> findByMemberIdAndBranchId(
            Long memberId,
            Long branchId
    );

    boolean existsByMemberIdAndBranchId(Long memberId, Long branchId);
}
