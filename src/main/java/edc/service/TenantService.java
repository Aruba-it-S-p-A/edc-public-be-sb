package edc.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edc.config.KeycloakProperties;
import edc.config.security.roles.RoleConfig;
import edc.dto.TenantRequest;
import edc.dto.TenantResponse;
import edc.dto.TenantUpdateRequest;
import edc.entity.Tenant;
import edc.exception.KeycloakAdminException;
import edc.exception.TenantConflictException;
import edc.exception.TenantNotFoundException;
import edc.repository.TenantRepository;
import edc.util.EdcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RoleConfig roleConfig;
    private final KeycloakAdminService keycloakAdminService;
    private final KeycloakProperties keycloakProperties;

    @Value("${app.security.clientId:edc-provisioning-portal-fe}")
    public String clientId;

    @Value("${app.security.realmName:edc}")
    public String realmName;

    @Transactional
    public TenantResponse createTenant(TenantRequest request) {
        // Check if tenant with same name already exists
        log.debug("Original tenant name: {}", request.getName());
        String normalizedTenantName = EdcUtils.normalizeForInnerDnsUse(request.getName());
        log.info("Normalized tenant name: {}", normalizedTenantName);

        if (tenantRepository.existsByName(normalizedTenantName)) {
            throw new TenantConflictException("Tenant with name already exists: " + normalizedTenantName);
        }

        Tenant tenant = new Tenant();
        tenant.setExternalId(UUID.randomUUID().toString());
        tenant.setName(normalizedTenantName);
        tenant.setDescription(request.getDescription());
        tenant.setMetadata(request.getMetadata());
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);

        Tenant savedTenant = tenantRepository.save(tenant);

// multitenant and multirealm solution for B2B
//        try{
//            // Create Keycloak realm and user for the tenant
//            keycloakAdminService.createRealmClientUserWithClaim(tenant.getName(),
//                    clientId,
//                    tenant.getName(),
//                    "password",
//                    keycloakProperties.getTenantKey(),
//                    tenant.getName());
//        } catch (Exception e){
//            log.error("Error creating Keycloak realm or user for tenant: {}", tenant.getName(), e);
//            throw new KeycloakAdminException("Failed to create Keycloak realm or user for tenant: " + tenant.getName());
//        }
        try{
            // Create Keycloak user for the tenant
            keycloakAdminService.createUserWithRealmRolesAndClaim(realmName,
                    savedTenant.getName()+".tenant",
                    "password",
                    keycloakProperties.getTenantKey(),
                    savedTenant.getName(),
                    List.of(roleConfig.ROLE_ADMIN_TENANT));
        } catch (Exception e){
            log.error("Error creating Keycloak realm or user for tenant: {}", savedTenant.getName(), e);
            throw new KeycloakAdminException("Failed to create Keycloak realm or user for tenant: " + savedTenant.getName());
        }

        log.info("Created new tenant with external_id: {} and name: {}", 
                savedTenant.getExternalId(), savedTenant.getName());

        return convertToResponse(savedTenant);
    }

    public TenantResponse getTenant(String externalId) {
        Tenant tenant = tenantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with external ID: " + externalId));

        // Check if tenant is not deleted
        if (tenant.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantNotFoundException("Tenant not found with external ID: " + externalId);
        }

        return convertToResponse(tenant);
    }

    public TenantResponse getTenantByName(String tenantName) {
        Tenant tenant = tenantRepository.findByName(tenantName)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with name: " + tenantName));

        // Check if tenant is not deleted
        if (tenant.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantNotFoundException("Tenant not found with name: " + tenantName);
        }

        return convertToResponse(tenant);
    }

    public Page<TenantResponse> getAllTenants(Pageable pageable) {
        Page<Tenant> tenants = tenantRepository.findAllActive(pageable);
        return tenants.map(this::convertToResponse);
    }


    public TenantResponse updateTenant(String externalId, TenantUpdateRequest request) {
        Tenant tenant = tenantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with external ID: " + externalId));

        // Check if tenant is not deleted
        if (tenant.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantNotFoundException("Tenant not found with external ID: " + externalId);
        }

        // Update only allowed fields (name is immutable)
        if (request.getDescription() != null) {
            tenant.setDescription(request.getDescription());
        }
        if (request.getMetadata() != null) {
            tenant.setMetadata(request.getMetadata());
        }

        tenant.setUpdatedAt(OffsetDateTime.now());
        Tenant updatedTenant = tenantRepository.save(tenant);

        log.info("Updated tenant with external_id: {} - description updated: {}, metadata updated: {}", 
                externalId, request.getDescription() != null, request.getMetadata() != null);

        return convertToResponse(updatedTenant);
    }

    @Transactional
    public TenantResponse deleteTenant(String externalId) {
        Tenant tenant = tenantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with external ID: " + externalId));

        // Check if tenant is not already deleted
        if (tenant.getStatus() == Tenant.TenantStatus.DELETED) {
            throw new TenantNotFoundException("Tenant not found with external ID: " + externalId);
        }

        // Soft delete
        tenant.setStatus(Tenant.TenantStatus.DELETED);
        tenant.setDeletedAt(OffsetDateTime.now());
        tenant.setUpdatedAt(OffsetDateTime.now());

        Tenant deletedTenant = tenantRepository.save(tenant);

        log.info("Soft deleted tenant with external_id: {} and name: {}", 
                deletedTenant.getExternalId(), deletedTenant.getName());

        return convertToResponse(deletedTenant);
    }

    public Page<TenantResponse> searchTenants(String name, Pageable pageable) {
        Page<Tenant> tenants = tenantRepository.findByNameContainingIgnoreCase(name, pageable);
        return tenants.map(this::convertToResponse);
    }

    public Page<TenantResponse> getTenantsByStatus(Tenant.TenantStatus status, Pageable pageable) {
        Page<Tenant> tenants = tenantRepository.findByStatus(status, pageable);
        return tenants.map(this::convertToResponse);
    }

    private TenantResponse convertToResponse(Tenant tenant) {
        TenantResponse response = new TenantResponse();
        response.setId(tenant.getExternalId());
        response.setName(tenant.getName());
        response.setDescription(tenant.getDescription());
        response.setStatus(tenant.getStatus());
        response.setCreatedAt(tenant.getCreatedAt());
        response.setUpdatedAt(tenant.getUpdatedAt());
        response.setDeletedAt(tenant.getDeletedAt());
        response.setMetadata(tenant.getMetadata());
        return response;
    }
}
