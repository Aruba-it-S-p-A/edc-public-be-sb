package edc.dto;

import java.util.Map;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ParticipantUpdateRequest {

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Map<String, Object> metadata;

}
