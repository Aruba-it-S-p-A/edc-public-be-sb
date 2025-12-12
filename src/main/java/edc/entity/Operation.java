package edc.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "provisioning_operations", indexes = {
    @Index(name = "idx_participant_id", columnList = "participant_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class Operation extends BaseEntity {

    @NotBlank(message = "External ID is required")
    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @NotNull(message = "Event type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "event_payload", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> eventPayload;

    public enum EventType {
        PROVISION_STARTED, PROVISION_IN_PROGRESS, PROVISION_COMPLETED, PROVISION_FAILED,
        DEPROVISION_STARTED, DEPROVISION_IN_PROGRESS, DEPROVISION_COMPLETED, DEPROVISION_FAILED
    }
}
