package edc.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import edc.entity.Tenant;
import lombok.Data;

@Data
public class TenantResponse {
    private String id;
    private String name;
    private String description;
    private Tenant.TenantStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
    private Map<String, Object> metadata;

}
