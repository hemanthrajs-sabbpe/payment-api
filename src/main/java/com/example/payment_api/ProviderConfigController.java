package com.example.payment_api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-configs/{serviceConfigId}/providers")
public class ProviderConfigController {

    private final ProviderConfigService providerConfigService;

    public ProviderConfigController(
            ProviderConfigService providerConfigService) {
        this.providerConfigService = providerConfigService;
    }

    // Create a provider configuration
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderConfig createProvider(
            @PathVariable Long serviceConfigId,
            @RequestBody ProviderConfig providerConfig) {

        return providerConfigService.createProviderConfig(
                serviceConfigId, providerConfig);
    }

    // Get all providers for a service configuration
    @GetMapping
    public List<ProviderConfig> getProviders(
            @PathVariable Long serviceConfigId) {

        return providerConfigService
                .getProvidersByServiceConfigId(serviceConfigId);
    }

    // Get one provider by name
    @GetMapping("/{providerName}")
    public ProviderConfig getProvider(
            @PathVariable Long serviceConfigId,
            @PathVariable String providerName) {

        return providerConfigService.getProviderByName(
                serviceConfigId, providerName);
    }

    // Update an existing provider
    @PutMapping("/{providerName}")
    public ProviderConfig updateProvider(
            @PathVariable Long serviceConfigId,
            @PathVariable String providerName,
            @RequestBody ProviderConfig providerConfig) {

        return providerConfigService.updateProviderConfig(
                serviceConfigId, providerName, providerConfig);
    }

    // Select the highest-priority ACTIVE provider
    @GetMapping("/select")
    public ProviderConfig selectProvider(
            @PathVariable Long serviceConfigId) {

        return providerConfigService.selectProvider(serviceConfigId);
    }
}


