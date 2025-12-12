package edc.service;

import edc.config.KeycloakProperties;
import edc.config.security.roles.RoleConfig;
import edc.dto.ParticipantMeResponse;
import edc.dto.ParticipantRequest;
import edc.dto.ParticipantResponse;
import edc.dto.ParticipantUpdateRequest;
import edc.entity.Operation;
import edc.entity.Participant;
import edc.entity.ParticipantUser;
import edc.entity.Tenant;
import edc.repository.ParticipantRepository;
import edc.repository.ParticipantUserRepository;
import edc.repository.TenantRepository;
import edc.entity.projection.ParticipantMeDto;
import edc.exception.ParticipantConflictException;
import edc.exception.ParticipantNotFoundException;
import edc.util.EdcUtils;
import edc.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final TenantRepository tenantRepository;
    private final ExternalApiService externalApiService;
    private final OperationService operationService;
    private final KeycloakAdminService keycloakAdminService;
    private final KeycloakProperties keycloakProperties;
    private final RoleConfig roleConfig;
    private final ParticipantUserRepository participantUserRepository;


    @Value("${app.security.realmName:edc}")
    public String realmName;

    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    public Page<ParticipantResponse> findAllParticipants(Optional<String> tenantName,
                                                         Participant.CurrentOperation currentOperation,
                                                         String participantName,
                                                         Integer limit,
                                                         Integer page,
                                                         List<String> orderBy,
                                                         String order) {

        final Pageable pageable = PageUtils.getPageable(limit, page, orderBy, Sort.Direction.valueOf(order));

        String currentOperationValue = currentOperation != null ? currentOperation.getValue() : null;
        Page<Participant> participants;
        if (tenantName.isEmpty() || tenantName.get().isBlank()) {
            participants = participantRepository.findByFilters(currentOperationValue, participantName, pageable);
        } else {
            participants = participantRepository.findByFiltersAndTenantName(tenantName.get(), currentOperationValue, participantName, pageable);
        }
        return participants.map(this::convertToResponse);
    }

    public ParticipantResponse findParticipantByExternalId(String externalId) {
        Participant participant = participantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + externalId));
        return convertToResponse(participant);
    }

    public ParticipantResponse findParticipantByExternalIdAndTenantName(String externalId, String tenantName) {
        Participant participant = participantRepository.findByExternalIdAndTenantName(externalId, tenantName)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + externalId + " and tenant: " + tenantName));
        return convertToResponse(participant);
    }

    public ParticipantMeResponse findParticipantMeByUserNameAndTenantName(String userName, String tenantName) {
        ParticipantMeDto participantMeDto = participantRepository.findByUserNameAndTenantName(userName, tenantName, List.of(ParticipantUser.Status.ACTIVE,
                                                                                                                            ParticipantUser.Status.DELETE_IN_PROGRESS))
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found for username: " + userName + " and tenant: " + tenantName));
        return convertToMeResponse(participantMeDto);
    }



    @Transactional
    public ParticipantResponse createParticipant(Optional<String> tenantName,
                                                 ParticipantRequest request) {
        log.debug("Original participant name: {}", request.getParticipant().getName());
        String normalizedParticipantName = EdcUtils.normalizeForInnerDnsUse(request.getParticipant().getName());
        log.debug("Normalized participant name: {}", normalizedParticipantName);

        if (participantRepository.existsByName(normalizedParticipantName)) {
            throw new ParticipantConflictException("Participant with name already exists: " + normalizedParticipantName);
        }

        if (tenantName.isEmpty() || tenantName.get().isBlank()) {
            throw new RuntimeException("Tenant not found with name " + tenantName);
        }
        Tenant foundTenant = tenantRepository.findByName(tenantName.get()).orElseThrow(() ->
            new RuntimeException("Tenant not found with name " + tenantName.get())
        );

        if(!(request.getUser().getUsername() == null || request.getUser().getUsername().isBlank())
                && participantUserRepository.existsByUsername(request.getUser().getUsername())){
                throw new ParticipantConflictException("Participant user with username already exists: " + request.getUser().getUsername());
            }


        try {
            log.info("Try Creating participant with name: {} - tenant: {}", normalizedParticipantName, tenantName.get());
            Map<String, String> externalResponse = externalApiService.provisionParticipant(normalizedParticipantName);
            log.info("External API provisioning successful for participant: {}, response: {}", normalizedParticipantName, externalResponse);

            // Create and save Participant entity
            String did = externalApiService.buildDid(normalizedParticipantName);
            String host = externalApiService.buildHost(normalizedParticipantName);
            Participant participant = new Participant();
            participant.setName(normalizedParticipantName);
            participant.setCompanyName(request.getParticipant().getName());
            participant.setTenantId(foundTenant.getId());
            participant.setExternalId(UUID.randomUUID().toString());
            participant.setDid(did);
            participant.setHost(host);
            participant.setCurrentOperation(Participant.CurrentOperation.PROVISION_IN_PROGRESS);
            participant.setMetadata(request.getParticipant().getMetadata());
            participant.setDescription(request.getParticipant().getDescription());

            Participant savedParticipant = participantRepository.save(participant);
            log.trace("Saved participant entity: {}", savedParticipant);

            // Create corresponding operation
            operationService.createProvisionStartedOperation(savedParticipant.getExternalId());

            //create participant user on kk
            keycloakAdminService.createUserWithRealmRolesAndClaim(realmName,
                        request.getUser().getUsername(),
                        request.getUser().getPassword(),
                        keycloakProperties.getTenantKey(),
                        tenantName.get(),
                        List.of(roleConfig.ROLE_USER_PARTICIPANT));

            log.info("Created Keycloak user {} for participant {}", request.getUser().getUsername(), normalizedParticipantName);

            //create and save ParticipantUser entity
            ParticipantUser participantUser = new ParticipantUser();
            participantUser.setExternalId(UUID.randomUUID().toString());
            participantUser.setParticipantId(savedParticipant.getId());
            participantUser.setUsername(request.getUser().getUsername());
            participantUser.setPassword(passwordEncoder.encode(request.getUser().getPassword()));
            participantUser.setMetadata(request.getUser().getMetadata());
            participantUser.setStatus(ParticipantUser.Status.ACTIVE);

            participantUserRepository.save(participantUser);
            log.trace("Saved participant user entity: {}", participantUser);

            log.info("Created new participant with external_id: {} and name: {} after successful external provisioning",
                    savedParticipant.getExternalId(), savedParticipant.getName());

            return convertToResponse(savedParticipant);

        } catch (Exception e) {
            // Rollback participant creation in case of Keycloak user creation failure
            //try rollback external provisioning
            try {
                externalApiService.deprovisionParticipant(normalizedParticipantName);
            } catch (Exception ex) {
                log.error("Failed to rollback external provisioning for participant {}: {}", normalizedParticipantName, ex.getMessage(), ex);
            }

            log.error("Failed to provision participant via external API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to provision participant via external API: " + e.getMessage(), e);
        }

    }



    public ParticipantResponse deleteParticipant(String externalId) {
        Participant participant = participantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + externalId));

        return deprovisioningParticipant(participant);
    }

    public ParticipantResponse deleteParticipantByExternalIdAndTenantName(String participantId, String tenantName) {
        Participant participant = participantRepository.findByExternalIdAndTenantName(participantId, tenantName)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + participantId ));

        return deprovisioningParticipant(participant);
    }


    private ParticipantResponse deprovisioningParticipant(Participant participant) {
        try {
            // Check if participant is in a state that allows deletion
            if (participant.getCurrentOperation() != Participant.CurrentOperation.ACTIVE) {
                log.info("Participant with external ID: {} is not in ACTIVE state, cannot deprovision. Current state: {}",
                        participant.getExternalId(), participant.getCurrentOperation());
                return convertToResponse(participant);
            }

            Map<String, String> externalResponse = externalApiService.deprovisionParticipant(participant.getName());
            participant.setCurrentOperation(Participant.CurrentOperation.DEPROVISION_COMPLETED);
            Participant updatedParticipant = participantRepository.save(participant);
            log.info("External API deprovisioning successful for participant: {}, response: {}", participant.getName(), externalResponse);

            // Create corresponding operation
            operationService.createDeprovisionStartedOperation(participant.getExternalId());

            //set user as DELETED (soft delete)
            deleteParticipatUserAndKeycloakUser(participant, ParticipantUser.Status.DELETED);

            log.info("Deprovisioned participant with external_id: {} after successful external deprovisioning", participant.getExternalId());
            return convertToResponse(updatedParticipant);

        } catch (Exception e) {
            log.error("Failed to deprovision participant via external API: {}", e.getMessage(), e);
            participant.setCurrentOperation(Participant.CurrentOperation.DEPROVISION_FAILED);
            Participant updatedParticipant = participantRepository.save(participant);

            // Create corresponding failed operation
            operationService.createDeprovisionFailedOperation(participant.getExternalId(), e.getMessage());

            //set user as DELETE_IN_ERROR (soft delete)
            deleteParticipatUserAndKeycloakUser(participant, ParticipantUser.Status.DELETE_WITH_ERROR);

            return convertToResponse(updatedParticipant);
        }

    }

    private void deleteParticipatUserAndKeycloakUser(Participant participant, ParticipantUser.Status status){
        List<ParticipantUser> participantUsers = participantUserRepository.findByParticipantId(participant.getId()) ;
        for(ParticipantUser user : participantUsers){
            log.debug("Setting participant user {} status to {} for participant {}", user.getUsername(), status, participant.getName());
            user.setStatus(status);
            user.setDeletedAt(OffsetDateTime.now());
            participantUserRepository.save(user);

            //try delete user from kk
            try {
                keycloakAdminService.deleteUserByUsername(realmName, user.getUsername());

            } catch (Exception e) {
                log.warn("Failed to delete Keycloak user {} for participant {}: {}", user.getUsername(), participant.getName(), e.getMessage(), e);
            }
            log.info("Deleted user {} for participant {}", user.getUsername(), participant.getName());
        }

    }

    public ParticipantResponse updateParticipantByExternalId(String externalId, ParticipantUpdateRequest request) {
        Participant participant = participantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + externalId));

        return updateParticipant(participant, request);
    }

    public ParticipantResponse updateParticipantByExternalIdAndTenantName(String externalId, String tenantName, ParticipantUpdateRequest request) {
        Participant participant = participantRepository.findByExternalIdAndTenantName(externalId, tenantName)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found with external ID: " + externalId + " and tenant: " + tenantName));

        return updateParticipant(participant, request);
    }

    private ParticipantResponse updateParticipant(Participant participant, ParticipantUpdateRequest request) {

        // Check if participant is in a state that allows updates
        if (!isParticipantUpdatable(participant.getCurrentOperation().getValue())) {
            // Return generic 404 to avoid exposing internal state information
            throw new ParticipantNotFoundException("Participant not found with external ID: " + participant.getExternalId());
        }

        // Update only allowed fields (name is immutable)
        if (request.getDescription() != null) {
            participant.setDescription(request.getDescription());
        }

        if (request.getMetadata() != null) {
            participant.setMetadata(request.getMetadata());
        }

        participant.setCurrentOperation(Participant.CurrentOperation.UPDATED);
        Participant updatedParticipant = participantRepository.save(participant);

        log.info("Updated participant with external_id: {} - description: {}, metadata updated: {}",
                participant.getExternalId(), request.getDescription() != null, request.getMetadata() != null);

        return convertToResponse(updatedParticipant);
    }

    private ParticipantResponse convertToResponse(Participant participant) {
        ParticipantResponse response = new ParticipantResponse();
        response.setId(participant.getExternalId());
        response.setName(participant.getName());
        response.setCompanyName(participant.getCompanyName());
        response.setDid(participant.getDid());
        response.setHost(participant.getHost());
        response.setMetadata(participant.getMetadata());
        response.setCurrentOperation(String.valueOf(participant.getCurrentOperation()));
        response.setCreatedAt(participant.getCreatedAt());
        response.setUpdatedAt(participant.getUpdatedAt());
        return response;
    }

    private ParticipantMeResponse convertToMeResponse(ParticipantMeDto participantMeDto) {
        ParticipantMeResponse.ParticipantDto participantDto = ParticipantMeResponse.ParticipantDto.builder()
                .id(participantMeDto.getParticipantExternalId())
                .participantName(participantMeDto.getParticipantName())
                .name(participantMeDto.getCompanyName())
                .description(participantMeDto.getParticipantDescription())
                .did(participantMeDto.getDid())
                .host(participantMeDto.getHost())
                .currentOperation(participantMeDto.getParticipantCurrentOperation().getValue())
                .metadata(participantMeDto.getParticipantMetadata())
                .createdAt(participantMeDto.getParticipantCreatedAt())
                .build();

        ParticipantMeResponse.UserDto user = ParticipantMeResponse.UserDto.builder()
                .id(participantMeDto.getUserExternalId())
                .username(participantMeDto.getUsername())
                .metadata(participantMeDto.getUserMetadata())
                .createdAt(participantMeDto.getUserCreatedAt())
                .build();

        return ParticipantMeResponse.builder()
                .participant(participantDto)
                .user(user)
                .build();


    }



    /**
     * Creates the corresponding operation for a participant current operation change
     * @param externalId the participant external ID
     * @param currentOperation the new participant current operation
     */
    private void createOperationForStatus(String externalId, String currentOperation) {
        try {
            Participant.CurrentOperation operation = Participant.CurrentOperation.fromValue(currentOperation);
            switch (operation) {
                case PROVISION_IN_PROGRESS:
                    operationService.createProvisionStartedOperation(externalId);
                    break;
                case ACTIVE:
                    operationService.createOperation(externalId, Operation.EventType.PROVISION_COMPLETED,
                        Map.of("message", "Participant activated"));
                    break;
                case DEPROVISION_IN_PROGRESS:
                    operationService.createDeprovisionStartedOperation(externalId);
                    break;
                case DEPROVISION_COMPLETED:
                    operationService.createDeprovisionCompletedOperation(externalId);
                    break;
                case PROVISION_FAILED:
                    operationService.createProvisionFailedOperation(externalId, "Provisioning failed");
                    break;
                case DEPROVISION_FAILED:
                    operationService.createDeprovisionFailedOperation(externalId, "Deprovisioning failed");
                    break;
                case UPDATED:
                case DETAILS_UPDATED:
                    break;
            }
        } catch (IllegalArgumentException e) {
            log.warn("No operation mapping found for current operation: {}", currentOperation);
        }
    }
    
    /**
     * Checks if a participant can be updated based on its current operation
     * @param currentOperation the current participant operation
     * @return true if the participant can be updated, false otherwise
     */
    private boolean isParticipantUpdatable(String currentOperation) {
        try {
            Participant.CurrentOperation operation = Participant.CurrentOperation.fromValue(currentOperation);
            return operation == Participant.CurrentOperation.PROVISION_IN_PROGRESS || 
                   operation == Participant.CurrentOperation.ACTIVE;
        } catch (IllegalArgumentException e) {
            log.warn("Unknown current operation: {}, considering as not updatable", currentOperation);
            return false;
        }
    }



}
