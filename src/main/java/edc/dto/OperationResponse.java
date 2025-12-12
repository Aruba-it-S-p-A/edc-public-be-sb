package edc.dto;

import edc.entity.Operation;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class OperationResponse {
    private String id;
    private Operation.EventType eventType;
    private Map<String, Object> eventPayload;
    private OffsetDateTime createdAt;
}
