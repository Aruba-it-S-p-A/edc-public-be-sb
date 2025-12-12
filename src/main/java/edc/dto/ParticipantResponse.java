package edc.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class ParticipantResponse {
    private String id;
    private String name;
    private String companyName;
    private String did;
    private String host;
    private Map<String, Object> metadata;
    private String currentOperation;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
