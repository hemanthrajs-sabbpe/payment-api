
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationServiceConfigRepository
        extends JpaRepository<OrganizationServiceConfig, Long> {

    List<OrganizationServiceConfig> findByOrganizationId(
            String organizationId);

    Optional<OrganizationServiceConfig> findByOrganizationIdAndServiceType(
            String organizationId, String serviceType);

    boolean existsByOrganizationIdAndServiceType(
            String organizationId, String serviceType);
}
