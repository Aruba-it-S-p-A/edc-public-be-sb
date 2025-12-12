package edc.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenant_external_id", columnList = "external_id"),
    @Index(name = "idx_tenant_name", columnList = "name"),
    @Index(name = "idx_tenant_status", columnList = "status")
})
public class Tenant extends BaseEntity {

    @NotBlank(message = "External ID is required")
    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId;

    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Name must contain only lowercase letters, numbers and hyphens (DNS-compatible)")
    @Size(min = 3, max = 63, message = "Name must be between 3 and 63 characters")
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description")
    private String description;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "metadata", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata;

    public enum TenantStatus {
        ACTIVE, INACTIVE, DELETED
    }
}
