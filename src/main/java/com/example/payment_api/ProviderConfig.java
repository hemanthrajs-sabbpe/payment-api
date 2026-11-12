
package com.example.payment_api;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "provider_configs",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_service_provider",
            columnNames = {"service_config_id", "provider_name"}
        )
    }
)
public class ProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_config_id", nullable = false)
    private Long serviceConfigId;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "priority", nullable = false)
    private Integer priority = 1;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "configuration", columnDefinition = "LONGTEXT")
    private String configuration;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    public ProviderConfig() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdOn = now;
        modifiedOn = now;
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getServiceConfigId() {
        return serviceConfigId;
    }

    public void setServiceConfigId(Long serviceConfigId) {
        this.serviceConfigId = serviceConfigId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public LocalDateTime getModifiedOn() {
        return modifiedOn;
    }
}
