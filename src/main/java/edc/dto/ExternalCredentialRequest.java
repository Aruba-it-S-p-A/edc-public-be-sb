package edc.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExternalCredentialRequest {
    private String issuerDid;
    private String holderPid;
    private List<CredentialSpec> credentials;

    @Data
    @Builder
    public static class CredentialSpec {
        @Pattern(regexp = "VC1_0_JWT", message = "Format must be 'VC1_0_JWT'")
        private String format;
        
        @Pattern(regexp = "^(MembershipCredential|DataProcessorCredential)$", message = "Type must be 'MembershipCredential' or 'DataProcessorCredential'")
        private String type;
        
        private String id;
    }
}
