package edc.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import edc.config.security.roles.RoleConfig;
import edc.dto.TenantRequest;
import edc.dto.TenantResponse;
import edc.dto.TenantUpdateRequest;
import edc.entity.Tenant;
import edc.exception.TenantNotFoundException;
import edc.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/v1/tenants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Management", description = "API for tenant management")
public class TenantController {

    private static final String TENANT_NAME_CLAIM = "tenantName";

    private final TenantService tenantService;

    private final RoleConfig roleConfig;


    @PostMapping
    @Operation(summary = "Create a new tenant", description = "Creates a new tenant in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tenant created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Tenant with this name already exists"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN)")
    public ResponseEntity<TenantResponse> createTenant(
            @Parameter(description = "Tenant data") @Valid @RequestBody TenantRequest request) {

        log.info("Creating new tenant with name: {}", request.getName());
        TenantResponse tenant = tenantService.createTenant(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Retrieve a tenant", description = "Retrieves the details of a specific tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<TenantResponse> getTenant(JwtAuthenticationToken authToken,
                                                    @Parameter(description = "Tenant ID") @PathVariable String tenantId) {

        TenantResponse tenant;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, proceeding to get tenant by ID", roleConfig.ROLE_ADMIN);
            log.info("Getting tenant with ID: {}", tenantId);
            tenant = tenantService.getTenant(tenantId);
        } else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            Optional<TenantResponse> checkTenantName = checkTenantIdByTenantName(authToken.getToken(), tenantId);
            if (checkTenantName.isEmpty()) {
                log.info("Access denied: User's tenant does not match requested tenant ID");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            tenant = checkTenantName.get();
            log.info("User's tenant matches requested tenant ID, proceeding to return tenant details");

        }
        else {
            log.info("Access denied: User does not have required roles");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(tenant);
    }

    @GetMapping
    @Operation(summary = "List all tenants", description = "Retrieves the list of all tenants with optional filters")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant list retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN)")
    public ResponseEntity<List<TenantResponse>> getAllTenants(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") @Min(value = 1, message = "Page must be at least 1") int page,
            @Parameter(description = "Page size (max 20000)") @RequestParam(defaultValue = "20") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 20000, message = "Limit cannot exceed 20000") int limit,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "asc") String sortDir,
            @Parameter(description = "Filter by name") @RequestParam(required = false) String name,
            @Parameter(description = "Filter by status") @RequestParam(required = false) Tenant.TenantStatus status) {

        log.info("Getting all tenants - page: {}, limit: {}, sortBy: {}, sortDir: {}, name: {}, status: {}",
                page, limit, sortBy, sortDir, name, status);

        Pageable pageable = PageRequest.of(page - 1, limit,
                sortDir.equalsIgnoreCase("desc") ?
                        org.springframework.data.domain.Sort.by(sortBy).descending() :
                        org.springframework.data.domain.Sort.by(sortBy).ascending());

        Page<TenantResponse> tenants;

        if (name != null && !name.trim().isEmpty()) {
            tenants = tenantService.searchTenants(name, pageable);
        } else if (status != null) {
            tenants = tenantService.getTenantsByStatus(status, pageable);
        } else {
            tenants = tenantService.getAllTenants(pageable);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Total", String.valueOf(tenants.getTotalElements()));
        headers.add("X-Page", String.valueOf(page));
        headers.add("X-Limit", String.valueOf(limit));

        return ResponseEntity.ok()
                .headers(headers)
                .body(tenants.getContent());
    }


    @PutMapping("/{tenantId}")
    @Operation(summary = "Update a tenant", description = "Updates the details of a tenant (name is not modifiable)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant updated successfully"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN,@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<TenantResponse> updateTenant(JwtAuthenticationToken authToken,
                                                       @Parameter(description = "Tenant ID") @PathVariable String tenantId,
                                                       @Parameter(description = "Updated tenant data") @Valid @RequestBody TenantUpdateRequest request) {

        log.info("Updating tenant with ID: {}", tenantId);
        TenantResponse tenant;
        if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN))) {
            log.debug("User has {}, proceeding to update tenant by ID", roleConfig.ROLE_ADMIN);
            tenant = tenantService.updateTenant(tenantId, request);
        }
        else if (authToken.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleConfig.ROLE_ADMIN_TENANT))) {
            log.debug("User has {}, extracting tenantName from JWT", roleConfig.ROLE_ADMIN_TENANT);
            Optional<TenantResponse> checkTenantName = checkTenantIdByTenantName(authToken.getToken(), tenantId);
            if (checkTenantName.isEmpty()) {
                log.info("Access denied: User's tenant does not match requested tenant ID");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            tenant = tenantService.updateTenant(tenantId, request);
            log.info("User's tenant matches requested tenant ID, proceeding to update tenant details");
        }
        else {
            log.info("Access denied: User does not have required roles");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }


        return ResponseEntity.ok(tenant);
    }

    @DeleteMapping("/{tenantId}")
    @Operation(summary = "Delete a tenant", description = "Logically deletes a tenant (soft delete)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN)")
    public ResponseEntity<TenantResponse> deleteTenant(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {

        log.info("Deleting tenant with ID: {}", tenantId);
        TenantResponse tenant = tenantService.deleteTenant(tenantId);

        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/me")
    @Operation(summary = "Retrieve the current tenant", description = "Retrieves the tenant information of the authenticated user based on the JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant information retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "400", description = "Invalid token or missing tenantName")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN_TENANT)")
    public ResponseEntity<TenantResponse> getCurrentTenant(JwtAuthenticationToken authToken) {

        log.info("Getting current tenant for authenticated user");

        String tenantName = authToken.getToken().getClaim(TENANT_NAME_CLAIM);
        if (tenantName == null || tenantName.trim().isEmpty()) {
            log.warn("TenantName claim is missing in JWT token");
            throw new IllegalArgumentException("TenantName not present in token");
        }

        log.debug("Extracted tenantName from JWT: {}", tenantName);

        TenantResponse tenant = tenantService.getTenantByName(tenantName);

        log.info("Found tenant for tenantName: {}", tenantName);
        return ResponseEntity.ok(tenant);
    }


    private Optional<TenantResponse> checkTenantIdByTenantName(Jwt jwt, String tenantId) {
        // Retrieve the tenantName claim from the token
        String tenantName = jwt.getClaim(TENANT_NAME_CLAIM);
        log.debug("Getting tenant with name: {}", tenantName);
        if (tenantName == null || tenantName.isEmpty()) {
            log.info("Access denied: {} claim is missing in JWT", TENANT_NAME_CLAIM);
            return Optional.empty();
        }
        try {
            TenantResponse tenant = tenantService.getTenantByName(tenantName);
            if (tenant == null || !tenant.getId().equals(tenantId)) {
                log.info("Access denied: User's tenant does not match requested tenant ID {} or tenant not found", tenantId);
                return Optional.empty();
            }
            return Optional.of(tenant);
        } catch (TenantNotFoundException e) {
            log.info("Access denied tenant not found", e);
            return Optional.empty();
        }
    }


}
