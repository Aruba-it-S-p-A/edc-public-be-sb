package edc.entity.projection;

import edc.entity.Participant;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;


    /**
     * DTO for projecting participant data,
     * company and associated user.
     */
    @Data
    @AllArgsConstructor
    public class ParticipantMeDto {
        private String participantExternalId;
        private String participantName;
        private String companyName;
        private Participant.CurrentOperation participantCurrentOperation;
        private String did;
        private String host;
        private String participantDescription;
        private Map<String, Object> participantMetadata;
        private OffsetDateTime participantCreatedAt;
        private String userExternalId;
        private String username;
        private Map<String, Object> userMetadata;
        private OffsetDateTime userCreatedAt;
    }
