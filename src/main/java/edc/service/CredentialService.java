package edc.service;

import edc.config.ExternalApiProperties;
import edc.dto.CredentialRequest;
import edc.dto.CredentialResponse;
import edc.dto.ExternalCredentialRequest;
import edc.entity.Credential;
import edc.entity.Participant;
import edc.entity.ParticipantUser;
import edc.exception.ParticipantNotActiveException;
import edc.exception.ParticipantNotFoundException;
import edc.repository.CredentialRepository;
import edc.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final ParticipantRepository participantRepository;
    private final ExternalApiService externalApiService;
    private final ExternalApiProperties externalApiProperties;

    @Value("${app.mockCredentials:false}")
    private boolean mockCredentials;

    public Page<CredentialResponse> findCredentialsByParticipantExternalId(String participantExternalId,
                                                                 Credential.CredentialStatus status,
                                                                 Pageable pageable) {

        Page<Credential> credentials = credentialRepository.findByParticipantExternalIdAndStatus(participantExternalId, status, pageable);
        return credentials.map(this::convertToResponse);
    }

    public Page<CredentialResponse> findCredentialsByParticipantExternalIdAndTenantName(String participantExternalId,
                                                                   Credential.CredentialStatus status,
                                                                   String tenantName,
                                                                   Pageable pageable) {

        Page<Credential> credentials = credentialRepository.findByParticipantExternalIdAndStatusAndTenantName(participantExternalId, status, tenantName, pageable);
        return credentials.map(this::convertToResponse);
    }

    public Page<CredentialResponse> findCredentialsByParticipantExternalIdAndUserNameAndTenantName(String participantExternalId,
                                                                                        String username,
                                                                                        Credential.CredentialStatus status,
                                                                                        String tenantName,
                                                                                        Pageable pageable) {

        Page<Credential> credentials = credentialRepository.findByParticipantExternalIdAndUserNameAndStatusAndTenantName(participantExternalId,
                username, List.of(ParticipantUser.Status.ACTIVE, ParticipantUser.Status.DELETE_IN_PROGRESS), status, tenantName, pageable);
        return credentials.map(this::convertToResponse);
    }


    public CredentialResponse findCredentialByParticipantExternalIdAndCredentialIdAndTenantName(String participantId, String credentialId, String tenantName) {
        Participant participant = participantRepository.findByExternalIdAndTenantName(participantId, tenantName)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId));

        Credential credential = findAndcheckCredential(participant, credentialId);

        return convertToResponse(credential);
    }

    public CredentialResponse findCredentialByParticipantExternaIdAndCredentialIdAndTenantNameAndUserName(String participantId, String credentialId, String tenantName, String username) {
        Participant participant = participantRepository.findByExternalIdAndTenantNameAndUserName(participantId, tenantName, username, List.of(ParticipantUser.Status.ACTIVE,
                                                                                                                                                ParticipantUser.Status.DELETE_IN_PROGRESS))
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with id: " + participantId + " tenantName: " + tenantName + " username: " + username));

        Credential credential = findAndcheckCredential(participant, credentialId);

        return convertToResponse(credential);
    }

    public CredentialResponse findCredentialByExternalId(String participantId, String credentialId) {
        Participant participant = participantRepository.findByExternalId(participantId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId));

        Credential credential = findAndcheckCredential(participant, credentialId);

        return convertToResponse(credential);
    }

    private Credential findAndcheckCredential(Participant partecipant, String credentialId) {
        Credential credential = credentialRepository.findByExternalId(credentialId)
                .orElseThrow(() -> new ParticipantNotFoundException("Credential not found with external ID: " + credentialId));

        if (!credential.getParticipant().equals(partecipant)) {
            throw new ParticipantNotFoundException("Credential does not belong to the specified participant");
        }
        return credential;
    }

    public List<CredentialResponse> requestCredentials(String participantId, CredentialRequest request, String tenantName, String username) {
        Participant participant = participantRepository.findByExternalIdAndTenantNameAndUserName(participantId, tenantName, username, List.of(ParticipantUser.Status.ACTIVE))
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId));
        if(participant.getCurrentOperation() == null || !participant.getCurrentOperation().equals(Participant.CurrentOperation.ACTIVE)) {
            throw new ParticipantNotActiveException("Participant with external ID: " + participantId + " is not "
                    +Participant.CurrentOperation.ACTIVE+" but "+participant.getCurrentOperation());
        }
        return saveCredentials(participant, request);
    }

    public List<CredentialResponse> requestCredentials(String participantId, CredentialRequest request, String tenantName) {
        Participant participant = participantRepository.findByExternalIdAndTenantName(participantId, tenantName)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId));
        if(participant.getCurrentOperation() == null || !participant.getCurrentOperation().equals(Participant.CurrentOperation.ACTIVE)) {
            throw new ParticipantNotActiveException("Participant with external ID: " + participantId + " is not "
                    +Participant.CurrentOperation.ACTIVE+" but "+participant.getCurrentOperation());
        }
        return saveCredentials(participant, request);
    }

    public List<CredentialResponse> requestCredentials(String participantId, CredentialRequest request) {
        Participant participant = participantRepository.findByExternalId(participantId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId));
        if(participant.getCurrentOperation() == null || !participant.getCurrentOperation().equals(Participant.CurrentOperation.ACTIVE)) {
            throw new ParticipantNotActiveException("Participant with external ID: " + participantId + " is not "
                    +Participant.CurrentOperation.ACTIVE+" but "+participant.getCurrentOperation());
        }
        return saveCredentials(participant, request);
    }

    private List<CredentialResponse> saveCredentials(Participant participant, CredentialRequest request) {
        String requestId = UUID.randomUUID().toString();

        // Convert to external API format
        List<ExternalCredentialRequest.CredentialSpec> externalCredentials = request.getCredentials().stream()
                .map(credentialItem -> ExternalCredentialRequest.CredentialSpec.builder()
                        .format(credentialItem.getFormat())
                        .type(credentialItem.getType())
                        .id(credentialItem.getId())
                        .build())
                .collect(Collectors.toList());

        try {

            if(!mockCredentials) {
                // Call external API first
                Map<String, String> externalResponse = externalApiService.requestCredentials(
                        participant.getName(),
                        participant.getDid(),
                        externalCredentials
                );
                log.info("External credentials API call successful for participant: {}, response: {}",
                        participant.getExternalId(), externalResponse);
            }
            else{
                log.debug("Mocking external API call for participant: {}", participant.getExternalId());
                log.info("External credentials API call successful for participant: {}",
                        participant.getExternalId());
            }



            // Create local credentials only after successful external call
            List<Credential> credentials = request.getCredentials().stream()
                    .map(credentialItem -> {
                        Credential credential = new Credential();
                        credential.setExternalId(UUID.randomUUID().toString());
                        credential.setRequestId(requestId);
                        credential.setIssuerDid(externalApiProperties.getIssuerDid());
                        credential.setHolderPid(externalApiProperties.getHolderPid());
                        credential.setParticipant(participant);
                        credential.setCredentialType(credentialItem.getType());
                        credential.setFormat(credentialItem.getFormat());
                        credential.setStatus(mockCredentials ? Credential.CredentialStatus.ISSUED : Credential.CredentialStatus.REQUESTED);

                        // Generate credential hash automatically
                        credential.setCredentialHash(credential.generateCredentialHash());

                        return credential;
                    })
                    .collect(Collectors.toList());

            List<Credential> savedCredentials = credentialRepository.saveAll(credentials);

            log.info("Created {} credential requests for participant: {} with request ID: {} after successful external API call",
                    savedCredentials.size(), participant.getExternalId(), requestId);

            return savedCredentials.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to request credentials via external API for participant {}: {}", participant.getExternalId(), e.getMessage(), e);
            throw new RuntimeException("Failed to request credentials via external API: " + e.getMessage(), e);
        }
    }

    public CredentialResponse updateCredentialStatus(String credentialId, Credential.CredentialStatus status) {
        Credential credential = credentialRepository.findByExternalId(credentialId)
                .orElseThrow(() -> new ParticipantNotFoundException("Credential not found with external ID: " + credentialId));

        credential.setStatus(status);
        
        if (status == Credential.CredentialStatus.ISSUED) {
            credential.setIssuedAt(OffsetDateTime.now());
        }

        Credential updatedCredential = credentialRepository.save(credential);

        log.info("Updated credential status to {} for credential ID: {}", status, credentialId);
        return convertToResponse(updatedCredential);
    }

    public CredentialResponse updateCredentialDetails(String credentialId, OffsetDateTime expiresAt) {
        Credential credential = credentialRepository.findByExternalId(credentialId)
                .orElseThrow(() -> new ParticipantNotFoundException("Credential not found with external ID: " + credentialId));

        credential.setCredentialHash(credential.generateCredentialHash());
        credential.setExpiresAt(expiresAt);
        credential.setStatus(Credential.CredentialStatus.ISSUED);
        credential.setIssuedAt(OffsetDateTime.now());

        Credential updatedCredential = credentialRepository.save(credential);

        log.info("Updated credential details for credential ID: {} with regenerated hash", credentialId);
        return convertToResponse(updatedCredential);
    }

    private CredentialResponse convertToResponse(Credential credential) {
        CredentialResponse response = new CredentialResponse();
        response.setId(credential.getExternalId());
        response.setRequestId(credential.getRequestId());
        response.setIssuerDid(credential.getIssuerDid());
        response.setHolderPid(credential.getHolderPid());
        response.setCredentialType(credential.getCredentialType());
        response.setFormat(credential.getFormat());
        response.setStatus(credential.getStatus());
        response.setIssuedAt(credential.getIssuedAt());
        response.setExpiresAt(credential.getExpiresAt());
        response.setCredentialHash(credential.getCredentialHash());
        response.setCreatedAt(credential.getCreatedAt());
        return response;
    }
}
