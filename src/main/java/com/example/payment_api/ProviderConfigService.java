package com.example.payment_api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProviderConfigService {

    private final ProviderConfigRepository providerConfigRepository;
    private final OrganizationServiceConfigRepository serviceConfigRepository;

    public ProviderConfigService(
            ProviderConfigRepository providerConfigRepository,
            OrganizationServiceConfigRepository serviceConfigRepository) {
        this.providerConfigRepository = providerConfigRepository;
        this.serviceConfigRepository = serviceConfigRepository;
    }

    @Transactional
    public ProviderConfig createProviderConfig(
            Long serviceConfigId, ProviderConfig providerConfig) {

        if (serviceConfigId == null) {
            throw new IllegalArgumentException(
                    "Service configuration ID is required");
        }

        OrganizationServiceConfig serviceConfig =
                serviceConfigRepository.findById(serviceConfigId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Service configuration not found: "
                                        + serviceConfigId));

        if (!"PAYMENT_GATEWAY".equalsIgnoreCase(
                serviceConfig.getServiceType())) {
            throw new IllegalArgumentException(
                    "Providers can only be added to PAYMENT_GATEWAY services");
        }

        if (providerConfig.getProviderName() == null
                || providerConfig.getProviderName().isBlank()) {
            throw new IllegalArgumentException("Provider name is required");
        }

        providerConfig.setProviderName(
                providerConfig.getProviderName().trim().toUpperCase());

        if (providerConfig.getPriority() == null
                || providerConfig.getPriority() < 1) {
            throw new IllegalArgumentException(
                    "Priority must be at least 1");
        }

        if (providerConfig.getStatus() == null
                || !List.of("ACTIVE", "INACTIVE").contains(
                providerConfig.getStatus().trim().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Status must be ACTIVE or INACTIVE");
        }

        providerConfig.setStatus(
                providerConfig.getStatus().trim().toUpperCase());

        if (providerConfigRepository
                .existsByServiceConfigIdAndProviderName(
                        serviceConfigId,
                        providerConfig.getProviderName())) {
            throw new IllegalArgumentException(
                    "This provider is already configured for this service");
        }

        providerConfig.setServiceConfigId(serviceConfigId);
        return providerConfigRepository.save(providerConfig);
    }

    @Transactional(readOnly = true)
    public List<ProviderConfig> getProvidersByServiceConfigId(
            Long serviceConfigId) {

        if (!serviceConfigRepository.existsById(serviceConfigId)) {
            throw new IllegalArgumentException(
                    "Service configuration not found: " + serviceConfigId);
        }

        return providerConfigRepository.findByServiceConfigId(serviceConfigId);
    }

    @Transactional(readOnly = true)
    public ProviderConfig getProviderByName(
            Long serviceConfigId, String providerName) {

        if (!serviceConfigRepository.existsById(serviceConfigId)) {
            throw new IllegalArgumentException(
                    "Service configuration not found: " + serviceConfigId);
        }

        return providerConfigRepository
                .findByServiceConfigIdAndProviderName(
                        serviceConfigId,
                        providerName.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerName));
    }

    @Transactional
    public ProviderConfig updateProviderConfig(
            Long serviceConfigId,
            String providerName,
            ProviderConfig request) {

        ProviderConfig existing =
                getProviderByName(serviceConfigId, providerName);

        if (request.getPriority() == null || request.getPriority() < 1) {
            throw new IllegalArgumentException(
                    "Priority must be at least 1");
        }

        if (request.getStatus() == null
                || !List.of("ACTIVE", "INACTIVE").contains(
                request.getStatus().trim().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Status must be ACTIVE or INACTIVE");
        }

        existing.setPriority(request.getPriority());
        existing.setStatus(request.getStatus().trim().toUpperCase());
        existing.setConfiguration(request.getConfiguration());

        return providerConfigRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public ProviderConfig selectProvider(Long serviceConfigId) {

        if (!serviceConfigRepository.existsById(serviceConfigId)) {
            throw new IllegalArgumentException(
                    "Service configuration not found: " + serviceConfigId);
        }

        List<ProviderConfig> activeProviders =
                providerConfigRepository
                        .findByServiceConfigIdAndStatusOrderByPriorityAsc(
                                serviceConfigId, "ACTIVE");

        if (activeProviders.isEmpty()) {
            throw new IllegalArgumentException(
                    "No active providers available for service: "
                            + serviceConfigId);
        }

        return activeProviders.get(0);
    }
}
