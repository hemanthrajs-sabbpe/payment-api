
package com.example.payment_api;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    // Create a new organization
    public Organization createOrganization(Organization organization) {

        if (organization.getOrganizationId() == null
                || organization.getOrganizationId().isBlank()) {
            throw new IllegalArgumentException(
                    "Organization ID is required");
        }

        if (organization.getOrganizationCode() == null
                || organization.getOrganizationCode().isBlank()) {
            throw new IllegalArgumentException(
                    "Organization code is required");
        }

        if (organizationRepository.existsByOrganizationCode(
                organization.getOrganizationCode())) {
            throw new IllegalArgumentException(
                    "Organization code already exists");
        }

        return organizationRepository.save(organization);
    }

    // Get all organizations
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    // Get one organization by ID
    public Organization getOrganizationById(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found: " + organizationId));
    }

    // Update an existing organization
    public Organization updateOrganization(
            String organizationId,
            Organization updatedOrganization) {

        Organization existing = getOrganizationById(organizationId);

        String newCode = updatedOrganization.getOrganizationCode();

        if (newCode == null || newCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Organization code is required");
        }

        if (!newCode.equals(existing.getOrganizationCode())
                && organizationRepository.existsByOrganizationCode(newCode)) {
            throw new IllegalArgumentException(
                    "Organization code already exists");
        }

        existing.setOrganizationCode(newCode);
        existing.setLegalName(updatedOrganization.getLegalName());
        existing.setTradeName(updatedOrganization.getTradeName());
        existing.setEmail(updatedOrganization.getEmail());
        existing.setMobile(updatedOrganization.getMobile());
        existing.setCountryCode(updatedOrganization.getCountryCode());
        existing.setCurrencyCode(updatedOrganization.getCurrencyCode());
        existing.setTimezoneName(updatedOrganization.getTimezoneName());
        existing.setStatus(updatedOrganization.getStatus());
        existing.setOrganizationType(
                updatedOrganization.getOrganizationType());
        existing.setMerchantStatus(updatedOrganization.getMerchantStatus());
        existing.setMetadataJson(updatedOrganization.getMetadataJson());
        existing.setSettingsJson(updatedOrganization.getSettingsJson());

        // Keep the original ID and creation timestamp
        return organizationRepository.save(existing);
    }

    // Delete an organization
    public void deleteOrganization(String organizationId) {
        Organization organization = getOrganizationById(organizationId);
        organizationRepository.delete(organization);
    }
}
