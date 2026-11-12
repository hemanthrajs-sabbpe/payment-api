
package com.example.payment_api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}/services")
public class OrganizationServiceConfigController {

    private final OrganizationServiceConfigService serviceConfigService;

    public OrganizationServiceConfigController(
            OrganizationServiceConfigService serviceConfigService) {
        this.serviceConfigService = serviceConfigService;
    }

    // Create a service configuration
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationServiceConfig createServiceConfig(
            @PathVariable String organizationId,
            @RequestBody OrganizationServiceConfig config) {

        config.setOrganizationId(organizationId);
        return serviceConfigService.createServiceConfig(config);
    }

    // Get all services for an organization
    @GetMapping
    public List<OrganizationServiceConfig> getServices(
            @PathVariable String organizationId) {
        return serviceConfigService
                .getServicesByOrganization(organizationId);
    }

    // Get one service configuration
    @GetMapping("/{serviceType}")
    public OrganizationServiceConfig getService(
            @PathVariable String organizationId,
            @PathVariable String serviceType) {
        return serviceConfigService
                .getServiceConfig(organizationId, serviceType);
    }

    // Update a service configuration
    @PutMapping("/{serviceType}")
    public OrganizationServiceConfig updateService(
            @PathVariable String organizationId,
            @PathVariable String serviceType,
            @RequestBody OrganizationServiceConfig config) {
        return serviceConfigService.updateServiceConfig(
                organizationId, serviceType, config);
    }
}
