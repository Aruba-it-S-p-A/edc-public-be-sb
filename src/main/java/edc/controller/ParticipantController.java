package edc.controller;

import edc.dto.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import edc.config.security.roles.RoleConfig;
import edc.entity.Participant;
import edc.entity.Credential;
import edc.service.ParticipantService;
import edc.service.CredentialService;
import edc.service.OperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import edc.util.PageUtils;
import edc.util.VisibilityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/participants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "participants", description = "Operations for managing participants")
public class ParticipantController {

    private final ParticipantService participantService;
    private final CredentialService credentialService;
    private final OperationService operationService;

    private final RoleConfig roleConfig;

    @GetMapping
    @Operation(summary = "List all participants", description = "Retrieves the list of all participants with optional filters")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of participants"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<List<ParticipantResponse>> getAllParticipants(
            JwtAuthenticationToken authToken,
            @Parameter(description = "Filter by current operation") @RequestParam(required = false) Participant.CurrentOperation currentOperation,
            @Parameter(description = "Filter by participant name") @RequestParam(required = false) String participantName,
            @Parameter(description = "Maximum number of results per page") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Field to order by", in = ParameterIn.QUERY) @Valid @RequestParam(value = "orderBy", required = false, defaultValue = "created_at") List<String> orderBy,
            @Parameter(description = "Order direction", in = ParameterIn.QUERY) @Valid @RequestParam(value = "order", required = false, defaultValue = "ASC") String order) {

        log.info("Getting participants with filters - currentOperation: {}, name: {}, limit: {}, page: {}", currentOperation, participantName, limit, page);

        Optional<String> tenantName = Optional.empty();
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        //Pageable pageable = PageRequest.of(page - 1, limit);

        Page<ParticipantResponse> participants = participantService.findAllParticipants(tenantName,
                currentOperation,
                participantName,
                limit,
                page-1,
                orderBy,
                order);
        HttpHeaders headers = PageUtils.setPaginationHeaders(participants);

//        HttpHeaders headers = new HttpHeaders();
//        headers.add("X-Total", String.valueOf(participants.getTotalElements()));
//        headers.add("X-Page", String.valueOf(page));
//        headers.add("X-Limit", String.valueOf(limit));

        return ResponseEntity.ok()
                .headers(headers)
                .body(participants.getContent());
    }

    @PostMapping
    @Operation(summary = "Create a new participant", description = "Initiates the provisioning of a new participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Provisioning started (asynchronous operation)"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Participant already exists")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<ParticipantResponse> createParticipant(JwtAuthenticationToken authToken,
                                                                 @Parameter(description = "Participant data") @Valid @RequestBody ParticipantRequest request) {

        log.info("Creating new participant for company: {}", request.getParticipant().getName());
        log.trace("Participant request details: {}", request);

        Optional<String> tenantName = Optional.ofNullable(request.getTenantName());
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has role {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has role {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            //check if username and password are defined into request user object
            if(request.getUser() == null || request.getUser().getUsername() == null || request.getUser().getUsername().isEmpty() ||
                    request.getUser().getPassword() == null || request.getUser().getPassword().isEmpty()) {
                log.warn("User information is missing in participant request for role {}, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }
        else if(authToken.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has role {}, using tenantName from request if provided", roleConfig.ROLE_ADMIN);
            if (tenantName.isEmpty()) {
                log.warn("User has role {} but no tenantName provided in request, returning bad request", roleConfig.ROLE_ADMIN);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            //for admin role user and password are optional
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Found tenantName: {} for participant creation", tenantName.get());

        ParticipantResponse participant = participantService.createParticipant(tenantName, request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(participant);
    }

    @GetMapping("/{participantId}")
    @Operation(summary = "Retrieve a specific participant", description = "Retrieves the details of a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant details"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<ParticipantResponse> getParticipant(JwtAuthenticationToken authToken,
                                                              @Parameter(description = "Participant ID") @PathVariable String participantId) {

        log.info("Getting participant with ID: {}", participantId);

        ParticipantResponse participant;
        Optional<String> tenantName;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            participant = participantService.findParticipantByExternalIdAndTenantName(participantId, tenantName.get());
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
            participant = participantService.findParticipantByExternalId(participantId);
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(participant);
    }

    @GetMapping("/me")
    @Operation(summary = "Retrieve the participant using parameters from the JWT", description = "Retrieves the details of a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant details"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_USER_PARTICIPANT)")
    public ResponseEntity<ParticipantMeResponse> getMe(JwtAuthenticationToken authToken) {

        log.info("Try Getting participant by me");

        ParticipantMeResponse participant;
        Optional<String> tenantName;
        Optional<String> userName;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_USER_PARTICIPANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_USER_PARTICIPANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            userName = VisibilityUtils.getUsernameClaim(authToken.getToken());
            if (userName.isEmpty()) {
                log.warn("User has {} but no name claim found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.info("Getting participant for user: {} and tenantName: {} from JWT", userName.get(), tenantName.get());
            participant = participantService.findParticipantMeByUserNameAndTenantName(userName.get(), tenantName.get());
        }
        else {
            log.debug("User has not {} role", roleConfig.ROLE_USER_PARTICIPANT);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        return ResponseEntity.ok(participant);
    }

    @DeleteMapping("/{participantId}")
    @Operation(summary = "Delete a participant", description = "Initiates the deprovisioning of a participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Deprovisioning started (asynchronous operation)"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<ParticipantResponse> deleteParticipant(JwtAuthenticationToken authToken,
                                                                 @Parameter(description = "Participant ID") @PathVariable String participantId) {

        log.info("Starting deprovisioning for participant with ID: {}", participantId);
        ParticipantResponse participant;
        Optional<String> tenantName = Optional.empty();
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            participant = participantService.deleteParticipantByExternalIdAndTenantName(participantId, tenantName.get());
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
            participant = participantService.deleteParticipant(participantId);
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(participant);
    }

    @GetMapping("/{participantId}/credentials")
    @Operation(summary = "List participant credentials", description = "Retrieves the list of credentials for a participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of credentials"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT,@roleConfig.ROLE_USER_PARTICIPANT)")
    public ResponseEntity<List<CredentialResponse>> getParticipantCredentials(JwtAuthenticationToken authToken,
                                                                              @Parameter(description = "Participant ID") @PathVariable String participantId,
                                                                              @Parameter(description = "Filter by credential status") @RequestParam(required = false) Credential.CredentialStatus status,
                                                                              @Parameter(description = "Maximum number of results per page") @RequestParam(defaultValue = "20") int limit,
                                                                              @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page) {

        log.info("Getting credentials for participant: {} with status: {}", participantId, status);

        Page<CredentialResponse> credentials ;
        Pageable pageable = PageRequest.of(page - 1, limit);

        Optional<String> tenantName;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            credentials = credentialService.findCredentialsByParticipantExternalIdAndTenantName(participantId, status, tenantName.get(), pageable);
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_USER_PARTICIPANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_USER_PARTICIPANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Optional<String> userName = VisibilityUtils.getUsernameClaim(authToken.getToken());
            if (userName.isEmpty()) {
                log.warn("User has {} but no name claim found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.info("Getting credentials for participant {} and user: {} and tenantName: {} from JWT", participantId, userName.get(), tenantName.get());
            credentials = credentialService.findCredentialsByParticipantExternalIdAndUserNameAndTenantName(participantId, userName.get(), status, tenantName.get(), pageable);
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
            credentials = credentialService.findCredentialsByParticipantExternalId(participantId, status, pageable);
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }


        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Total", String.valueOf(credentials.getTotalElements()));
        headers.add("X-Page", String.valueOf(page));
        headers.add("X-Limit", String.valueOf(limit));

        return ResponseEntity.ok()
                .headers(headers)
                .body(credentials.getContent());
    }

    @PostMapping("/{participantId}/credentials")
    @Operation(summary = "Request credentials from participant", description = "Requests credentials for a specific participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Credential request started"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Participant not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "502", description = "External API call error")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_USER_PARTICIPANT,@roleConfig.ROLE_ADMIN_TENANT,@roleConfig.ROLE_USER_PARTICIPANT)")
    public ResponseEntity<Map<String, Object>> requestCredentials(JwtAuthenticationToken authToken,
                                                                  @Parameter(description = "Participant ID") @PathVariable String participantId,
                                                                  @Parameter(description = "Credential request data") @Valid @RequestBody CredentialRequest request) {

        log.info("Requesting credentials for participant: {}", participantId);

        List<CredentialResponse> credentials;
        Optional<String> tenantName;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.info("Request credentials for participant {} and tenantName: {} from JWT", participantId, tenantName.get());
            credentials = credentialService.requestCredentials(participantId, request, tenantName.get());
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_USER_PARTICIPANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_USER_PARTICIPANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Optional<String> userName = VisibilityUtils.getUsernameClaim(authToken.getToken());
            if (userName.isEmpty()) {
                log.warn("User has {} but no name claim found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.info("Request credentials for participant {} and user: {} and tenantName: {} from JWT", participantId, userName.get(), tenantName.get());
            credentials = credentialService.requestCredentials(participantId, request, tenantName.get(), userName.get());
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
            log.info("Request credentials for participant {} from admin user", participantId);
            credentials = credentialService.requestCredentials(participantId, request);
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }


        Map<String, Object> response = new HashMap<>();
        response.put("requestId", credentials.get(0).getRequestId());
        response.put("participantId", participantId);
        response.put("status", "REQUESTED");
        response.put("credentials", credentials.stream().map(cred -> {
            Map<String, Object> credMap = new HashMap<>();
            credMap.put("format", cred.getFormat());
            credMap.put("type", cred.getCredentialType());
            credMap.put("id", cred.getId());
            credMap.put("status", cred.getStatus());
            return credMap;
        }).collect(Collectors.toList()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{participantId}/credentials/{credentialId}")
    @Operation(summary = "Retrieve a specific credential", description = "Retrieves the details of a specific credential")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credential details"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT,@roleConfig.ROLE_USER_PARTICIPANT)")
    public ResponseEntity<CredentialResponse> getCredential(JwtAuthenticationToken authToken,
                                                            @Parameter(description = "Participant ID") @PathVariable String participantId,
                                                            @Parameter(description = "Credential ID") @PathVariable String credentialId) {

        log.info("Getting credential {} for participant: {}", credentialId, participantId);

        CredentialResponse credential;
        Optional<String> tenantName;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            credential = credentialService.findCredentialByParticipantExternalIdAndCredentialIdAndTenantName(participantId, credentialId, tenantName.get());
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_USER_PARTICIPANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_USER_PARTICIPANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Optional<String> userName = VisibilityUtils.getUsernameClaim(authToken.getToken());
            if (userName.isEmpty()) {
                log.warn("User has {} but no name claim found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.info("Getting credential {} for participant: {} and tenantName: {} and user: {} from JWT", credentialId, participantId, tenantName.get(), userName.get());
            credential = credentialService.findCredentialByParticipantExternaIdAndCredentialIdAndTenantNameAndUserName(participantId, credentialId, tenantName.get(), userName.get());
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
            credential = credentialService.findCredentialByExternalId(participantId, credentialId);
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(credential);
    }

    @GetMapping("/{participantId}/operations")
    @Operation(summary = "Participant operation history", description = "Retrieves the operation history of a participant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operation history"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT,@roleConfig.ROLE_USER_PARTICIPANT)")
    public ResponseEntity<List<OperationResponse>> getParticipantOperations(JwtAuthenticationToken authToken,
                                                                            @Parameter(description = "Participant ID") @PathVariable String participantId,
                                                                            @Parameter(description = "Filter by event type") @RequestParam(required = false) edc.entity.Operation.EventType eventType,
                                                                            @Parameter(description = "Maximum number of results per page") @RequestParam(defaultValue = "20") int limit,
                                                                            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page) {

        log.info("Getting operations for participant: {} with eventType: {}", participantId, eventType);

        Page<OperationResponse> operations;
        Pageable pageable = PageRequest.of(page - 1, limit);

        Optional<String> tenantName;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            operations = operationService.findOperationsByParticipantExternalIdAndTenantName(participantId, tenantName.get(), eventType, pageable);
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_USER_PARTICIPANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_USER_PARTICIPANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Optional<String> userName = VisibilityUtils.getUsernameClaim(authToken.getToken());
            if (userName.isEmpty()) {
                log.warn("User has {} but no name claim found in JWT, returning bad request", roleConfig.ROLE_USER_PARTICIPANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.info("Getting operations for participant: {} and tenantName: {} and user {} from JWT", participantId, tenantName.get(), userName.get());
            operations = operationService.findOperationsByParticipantExternalIdAndTenantNameAndUserName(participantId, tenantName.get(), userName.get(), eventType, pageable);
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
            operations = operationService.findOperationsByParticipantExternalId(participantId, eventType, pageable);
        }
        else {
            log.warn("User does not have required roles, returning unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }


        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Total", String.valueOf(operations.getTotalElements()));
        headers.add("X-Page", String.valueOf(page));
        headers.add("X-Limit", String.valueOf(limit));

        return ResponseEntity.ok()
                .headers(headers)
                .body(operations.getContent());
    }

    @PatchMapping("/{participantId}")
    @Operation(summary = "Update a participant", description = "Updates the details of a participant (name is not modifiable)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant updated successfully"),
            @ApiResponse(responseCode = "404", description = "Participant not found or not modifiable"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<ParticipantResponse> updateParticipant(JwtAuthenticationToken authToken,
                                                                 @Parameter(description = "Participant ID") @PathVariable String participantId,
                                                                 @Parameter(description = "Updated participant data") @Valid @RequestBody ParticipantUpdateRequest request) {

        log.info("Updating participant with ID: {}", participantId);

        ParticipantResponse participantResponse;
        Optional<String> tenantName = Optional.empty();
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, proceeding to find tenant extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            tenantName = VisibilityUtils.getTenantName(authToken.getToken());
            if (tenantName.isEmpty()) {
                log.warn("User has {} but no tenantName found in JWT, returning bad request", roleConfig.ROLE_ADMIN_TENANT);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            participantResponse = participantService.updateParticipantByExternalIdAndTenantName(participantId, tenantName.get(), request);
        }
        else {
            log.debug("User has {}, no need to extract tenantName from JWT", roleConfig.ROLE_ADMIN);
            participantResponse = participantService.updateParticipantByExternalId(participantId, request);
        }

        return ResponseEntity.ok(participantResponse);
    }
}
