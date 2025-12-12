package edc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import edc.config.security.roles.RoleConfig;
import edc.dto.TenantRequest;
import edc.dto.TenantResponse;
import edc.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/signup")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Management", description = "API for tenant creation from signup page")
public class SignupController {

    private final TenantService tenantService;

    private final RoleConfig roleConfig;

    @PostMapping
    @Operation(summary = "Signup a new tenant", description = "Creates a new tenant in the system via signup page")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Signup completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Name already exists"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyAuthority(@roleConfig.ROLE_ADMIN)")
    public ResponseEntity<TenantResponse> createTenant(
            @Parameter(description = "Tenant data") @Valid @RequestBody TenantRequest request) {

        log.info("Signup new tenant with name: {}", request.getName());
        TenantResponse tenant = tenantService.createTenant(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

}
