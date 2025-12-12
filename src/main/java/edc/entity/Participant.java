package edc.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "participants", indexes = {
    @Index(name = "idx_external_id", columnList = "external_id"),
    @Index(name = "idx_name", columnList = "name"),
    @Index(name = "idx_current_operation", columnList = "current_operation"),
    @Index(name = "idx_did", columnList = "did")
})
public class Participant extends BaseEntity {

    @NotBlank(message = "External ID is required")
    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId;

    @Column(name = "tenant_id", nullable = false)
    private int tenantId;

    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Name must contain only lowercase letters and numbers (DNS-compatible)")
    @Size(min = 3, max = 63, message = "Name must be between 3 and 63 characters")
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Column(name = "company_name", length = 500)
    @Size(min = 3, max = 63, message = "Name must be between 3 and 63 characters")
    private String companyName;

    @Column(name = "description", length = 500)
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Column(name = "did")
    private String did;

    @Column(name = "host")
    private String host;

    @Column(name = "metadata", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_operation")
    private CurrentOperation currentOperation;

    public enum CurrentOperation {
        PROVISION_IN_PROGRESS("PROVISION_IN_PROGRESS"),
        ACTIVE("ACTIVE"),
        DEPROVISION_IN_PROGRESS("DEPROVISION_IN_PROGRESS"),
        DEPROVISION_COMPLETED("DEPROVISION_COMPLETED"),
        PROVISION_FAILED("PROVISION_FAILED"),
        DEPROVISION_FAILED("DEPROVISION_FAILED"),
        UPDATED("UPDATED"),
        DETAILS_UPDATED("DETAILS_UPDATED"),
        ERROR("ERROR");

        private final String value;

        CurrentOperation(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static CurrentOperation fromValue(String value) {
            for (CurrentOperation operation : CurrentOperation.values()) {
                if (operation.value.equals(value)) {
                    return operation;
                }
            }
            throw new IllegalArgumentException("Unknown current operation: " + value);
        }
    }
}
