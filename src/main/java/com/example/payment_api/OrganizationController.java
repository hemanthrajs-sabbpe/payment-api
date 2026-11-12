
package com.example.payment_api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    // Create a new organization
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Organization createOrganization(
            @RequestBody Organization organization) {
        return organizationService.createOrganization(organization);
    }

    // Get all organizations
    @GetMapping
    public List<Organization> getAllOrganizations() {
        return organizationService.getAllOrganizations();
    }

    // Get one organization by ID
    @GetMapping("/{organizationId}")
    public Organization getOrganizationById(
            @PathVariable String organizationId) {
        return organizationService.getOrganizationById(organizationId);
    }

    // Update an existing organization
    @PutMapping("/{organizationId}")
    public Organization updateOrganization(
            @PathVariable String organizationId,
            @RequestBody Organization organization) {
        return organizationService.updateOrganization(
                organizationId, organization);
    }
}
