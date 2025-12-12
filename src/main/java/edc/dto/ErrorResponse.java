package edc.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Data;

@Data
public class ErrorResponse {
    private String error;
    private String message;
    private Map<String, Object> details;
    private OffsetDateTime timestamp = OffsetDateTime.now();
    private int status;
}
