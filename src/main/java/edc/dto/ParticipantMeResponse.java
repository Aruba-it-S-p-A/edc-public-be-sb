package edc.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class ParticipantMeResponse {


    private ParticipantMeResponse.ParticipantDto participant;
    private ParticipantMeResponse.UserDto user;

    @Data
    @Builder
    public static class ParticipantDto {
        private String id;
        private String participantName;
        private String name;
        private String did;
        private String host;
        private String description;
        private String currentOperation;
        private Map<String, Object> metadata;
        private OffsetDateTime createdAt;
    }

    @Data
    @Builder
    public static class UserDto {
        private String id;
        private String username;
        private Map<String, Object> metadata;
        private OffsetDateTime createdAt;
    }
}
