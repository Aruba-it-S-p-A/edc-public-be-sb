package edc.dto;

import edc.entity.Credential;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CredentialResponse {
    private String id;
    private String requestId;
    private String issuerDid;
    private String holderPid;
    private String credentialType;
    private String format;
    private Credential.CredentialStatus status;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private String credentialHash;
    private OffsetDateTime createdAt;
}
