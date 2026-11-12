
package com.example.payment_api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class OrganizationServiceConfigService {

    private final OrganizationServiceConfigRepository serviceConfigRepository;
    private final OrganizationRepository organizationRepository;

    private static final Set<String> ALLOWED_SERVICES = Set.of(
            "PAYMENT_GATEWAY",
            "KYC",
            "PAYOUT",
            "NACH_MANDATE",
            "STATIC_QR"
    );

    public OrganizationServiceConfigService(
            OrganizationServiceConfigRepository serviceConfigRepository,
            OrganizationRepository organizationRepository) {
        this.serviceConfigRepository = serviceConfigRepository;
        this.organizationRepository = organizationRepository;
    }

    // Create a service configuration for an organization
    public OrganizationServiceConfig createServiceConfig(
            OrganizationServiceConfig config) {

        validateConfig(config);

        if (serviceConfigRepository
                .existsByOrganizationIdAndServiceType(
                        config.getOrganizationId(),
                        config.getServiceType())) {
            throw new IllegalArgumentException(
                    "This service is already configured for the organization");
        }

        config.setServiceType(config.getServiceType().toUpperCase());
        return serviceConfigRepository.save(config);
    }

    // Get all services configured for one organization
    public List<OrganizationServiceConfig> getServicesByOrganization(
            String organizationId) {

        ensureOrganizationExists(organizationId);

        return serviceConfigRepository.findByOrganizationId(organizationId);
    }

    // Get one service configuration
    public OrganizationServiceConfig getServiceConfig(
            String organizationId, String serviceType) {

        ensureOrganizationExists(organizationId);

        return serviceConfigRepository
                .findByOrganizationIdAndServiceType(
                        organizationId, serviceType.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Service configuration not found"));
    }

    // Update an existing service configuration
    @Transactional
    public OrganizationServiceConfig updateServiceConfig(
            String organizationId,
            String serviceType,
            OrganizationServiceConfig updatedConfig) {

        OrganizationServiceConfig existing =
                getServiceConfig(organizationId, serviceType);

        if (updatedConfig.getStatus() != null) {
            existing.setStatus(updatedConfig.getStatus().toUpperCase());
        }

        existing.setConfiguration(updatedConfig.getConfiguration());

        return serviceConfigRepository.save(existing);
    }

    private void validateConfig(OrganizationServiceConfig config) {
        if (config.getOrganizationId() == null
                || config.getOrganizationId().isBlank()) {
            throw new IllegalArgumentException(
                    "Organization ID is required");
        }

        ensureOrganizationExists(config.getOrganizationId());

        if (config.getServiceType() == null
                || !ALLOWED_SERVICES.contains(
                        config.getServiceType().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Unsupported service type. Allowed services: "
                            + ALLOWED_SERVICES);
        }

        if (config.getStatus() == null
                || !Set.of("ACTIVE", "INACTIVE").contains(
                        config.getStatus().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Status must be ACTIVE or INACTIVE");
        }
    }

    private void ensureOrganizationExists(String organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new IllegalArgumentException(
                    "Organization not found: " + organizationId);
        }
    }
}
