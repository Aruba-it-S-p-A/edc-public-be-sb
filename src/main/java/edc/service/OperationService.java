package edc.service;

import edc.dto.OperationResponse;
import edc.entity.Operation;
import edc.entity.Participant;
import edc.entity.ParticipantUser;
import edc.exception.ParticipantNotFoundException;
import edc.repository.OperationRepository;
import edc.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OperationService {

    private final OperationRepository operationRepository;
    private final ParticipantRepository participantRepository;

    public Page<OperationResponse> findOperationsByParticipantExternalId(String participantExternalId,
                                                             Operation.EventType eventType, 
                                                             Pageable pageable) {
        Participant participant = participantRepository.findByExternalId(participantExternalId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantExternalId));

        Page<Operation> operations = operationRepository.findByParticipantAndEventType(participant, eventType, pageable);
        return operations.map(this::convertToResponse);
    }

    public Page<OperationResponse> findOperationsByParticipantExternalIdAndTenantName(String participantExternalId,
                                                                         String tenantName,
                                                                         Operation.EventType eventType,
                                                                         Pageable pageable) {
        Participant participant = participantRepository.findByExternalIdAndTenantName(participantExternalId, tenantName)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantExternalId));

        Page<Operation> operations = operationRepository.findByParticipantAndEventType(participant, eventType, pageable);
        return operations.map(this::convertToResponse);
    }

    public Page<OperationResponse> findOperationsByParticipantExternalIdAndTenantNameAndUserName(String participantName,
                                                                                                String tenantName,
                                                                                                 String userName,
                                                                                                 Operation.EventType eventType,
                                                                                                 Pageable pageable) {
        Participant participant = participantRepository.findByExternalIdAndTenantNameAndUserName(participantName, tenantName, userName, List.of(ParticipantUser.Status.ACTIVE,
                                                                                                                                                ParticipantUser.Status.DELETE_IN_PROGRESS))
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with name: " + participantName +
                        ", tenant: " + tenantName + ", user: " + userName));

        Page<Operation> operations = operationRepository.findByParticipantAndEventType(participant, eventType, pageable);
        return operations.map(this::convertToResponse);
    }

    public List<OperationResponse> findLatestOperationsByParticipant(String participantId, int limit) {
        Participant participant = participantRepository.findByExternalId(participantId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId));

        Pageable pageable = Pageable.ofSize(limit);
        List<Operation> operations = operationRepository.findLatestByParticipant(participant, pageable);
        
        return operations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public OperationResponse createOperation(String participantId, Operation.EventType eventType, Map<String, Object> eventPayload) {
        Participant participant = participantRepository.findByExternalId(participantId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId));

        Operation operation = new Operation();
        operation.setExternalId(UUID.randomUUID().toString());
        operation.setParticipant(participant);
        operation.setEventType(eventType);
        operation.setEventPayload(eventPayload);

        Operation savedOperation = operationRepository.save(operation);

        log.info("Created operation {} for participant: {}", eventType, participantId);
        return convertToResponse(savedOperation);
    }

    public OperationResponse createProvisionStartedOperation(String participantId) {
        return createOperation(participantId, Operation.EventType.PROVISION_STARTED, Map.of("message", "Provisioning started"));
    }

    public OperationResponse createProvisionCompletedOperation(String participantId, String did, String host) {
        Map<String, Object> payload = Map.of(
            "message", "Provisioning completed",
            "did", did,
            "host", host
        );
        return createOperation(participantId, Operation.EventType.PROVISION_COMPLETED, payload);
    }

    public OperationResponse createProvisionFailedOperation(String participantId, String errorMessage) {
        Map<String, Object> payload = Map.of(
            "message", "Provisioning failed",
            "error", errorMessage
        );
        return createOperation(participantId, Operation.EventType.PROVISION_FAILED, payload);
    }

    public OperationResponse createDeprovisionStartedOperation(String participantId) {
        return createOperation(participantId, Operation.EventType.DEPROVISION_STARTED, Map.of("message", "Deprovisioning started"));
    }

    public OperationResponse createDeprovisionCompletedOperation(String participantId) {
        return createOperation(participantId, Operation.EventType.DEPROVISION_COMPLETED, Map.of("message", "Deprovisioning completed"));
    }

    public OperationResponse createDeprovisionFailedOperation(String participantId, String errorMessage) {
        Map<String, Object> payload = Map.of(
            "message", "Deprovisioning failed",
            "error", errorMessage
        );
        return createOperation(participantId, Operation.EventType.DEPROVISION_FAILED, payload);
    }

    private OperationResponse convertToResponse(Operation operation) {
        OperationResponse response = new OperationResponse();
        response.setId(operation.getExternalId());
        response.setEventType(operation.getEventType());
        response.setEventPayload(operation.getEventPayload());
        response.setCreatedAt(operation.getCreatedAt());
        return response;
    }
}
