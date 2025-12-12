package edc.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "participant_users", indexes = {
    @Index(name = "idx_external_id", columnList = "external_id"),
    @Index(name = "idx_username", columnList = "username")
})
public class ParticipantUser extends BaseEntity {

    @NotBlank(message = "External ID is required")
    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId;

    @Column(name = "participant_id", nullable = false)
    private int participantId;

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 63, message = "Name must be between 3 and 63 characters")
    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @NotBlank(message = "Password is required")
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "description", length = 500)
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;


    @Column(name = "metadata", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ParticipantUser.Status status;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public enum Status {
        ACTIVE("ACTIVE"),
        INACTIVE("INACTIVE"),
        DELETE_IN_PROGRESS("DELETE_IN_PROGRESS"),
        DELETE_WITH_ERROR("DELETE_WITH_ERROR"),
        DELETED("DELETED"),
        ERROR("ERROR");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static ParticipantUser.Status fromValue(String value) {
            for (ParticipantUser.Status status : ParticipantUser.Status.values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown current participant user status: " + value);
        }
    }


}
