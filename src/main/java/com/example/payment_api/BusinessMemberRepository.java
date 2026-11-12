
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BusinessMemberRepository
        extends JpaRepository<BusinessMember, Long> {

    List<BusinessMember> findByOrganizationId(String organizationId);

    List<BusinessMember> findByOrganizationIdAndMembershipStatus(
            String organizationId,
            String membershipStatus
    );

    List<BusinessMember> findByUserId(Long userId);

    List<BusinessMember> findByUserIdAndMembershipStatus(
            Long userId,
            String membershipStatus
    );

    Optional<BusinessMember> findByOrganizationIdAndUserId(
            String organizationId,
            Long userId
    );

    Optional<BusinessMember> findByIdAndOrganizationId(
            Long id,
            String organizationId
    );

    boolean existsByOrganizationIdAndUserId(
            String organizationId,
            Long userId
    );
}
