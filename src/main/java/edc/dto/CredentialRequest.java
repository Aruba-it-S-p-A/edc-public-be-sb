package edc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class CredentialRequest {

    @NotEmpty(message = "Credentials list cannot be empty")
    @Valid
    private List<CredentialItem> credentials;

    @Data
    public static class CredentialItem {
        @NotNull(message = "Format is required")
        @Pattern(regexp = "VC1_0_JWT", message = "Format must be 'VC1_0_JWT'")
        private String format;

        @NotNull(message = "Type is required")
        @Pattern(regexp = "^(MembershipCredential|DataProcessorCredential)$", message = "Type must be 'MembershipCredential' or 'DataProcessorCredential'")
        private String type;

        @NotNull(message = "Id is required")
        private String id;
    }
}
