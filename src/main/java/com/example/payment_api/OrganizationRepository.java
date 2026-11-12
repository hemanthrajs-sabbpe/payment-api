
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrganizationRepository
        extends JpaRepository<Organization, String> {

    Optional<Organization> findByOrganizationCode(String organizationCode);

    boolean existsByOrganizationCode(String organizationCode);
}
