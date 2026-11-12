package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderConfigRepository
        extends JpaRepository<ProviderConfig, Long> {

    // Find all providers for a service configuration
    List<ProviderConfig> findByServiceConfigId(
            Long serviceConfigId
    );

    // Find one provider by service configuration ID and provider name
    Optional<ProviderConfig> findByServiceConfigIdAndProviderName(
            Long serviceConfigId,
            String providerName
    );

    // Check whether a provider already exists for a service
    boolean existsByServiceConfigIdAndProviderName(
            Long serviceConfigId,
            String providerName
    );

    // Find active providers ordered by priority (lowest number first)
    List<ProviderConfig> findByServiceConfigIdAndStatusOrderByPriorityAsc(
            Long serviceConfigId,
            String status
    );
}
