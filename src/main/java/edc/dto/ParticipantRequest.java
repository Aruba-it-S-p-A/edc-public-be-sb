package edc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ParticipantRequest {

    private ParticipantRequest.ParticipantDto participant;
    private ParticipantRequest.UserDto user;
    // reserved for admin use
    private String tenantName;

    @Data
    public static class ParticipantDto {
        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 63, message = "Name must be between 3 and 63 characters")
        private String name;

        @Size(max = 500, message = "Description must not exceed 500 characters")
        private String description;

        private Map<String, Object> metadata;
    }

    @Data
    public static class UserDto {

        private String username;
        private String password;
        private Map<String, Object> metadata;
    }
}
