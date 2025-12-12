package edc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class TenantRequest {

    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Name must contain only lowercase letters, numbers and hyphens (DNS-compatible)")
    @Size(min = 3, max = 63, message = "Name must be between 3 and 63 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Map<String, Object> metadata;

}
