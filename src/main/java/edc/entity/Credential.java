package edc.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "credentials", indexes = {
    @Index(name = "idx_participant_id", columnList = "participant_id"),
    @Index(name = "idx_request_id", columnList = "request_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_credential_type", columnList = "credential_type")
})
public class Credential extends BaseEntity {

    @NotBlank(message = "External ID is required")
    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId;

    @NotBlank(message = "Request ID is required")
    @Column(name = "request_id", nullable = false)
    private String requestId;

    @NotBlank(message = "Issuer DID is required")
    @Column(name = "issuer_did", nullable = false)
    private String issuerDid;

    @NotBlank(message = "Holder PID is required")
    @Column(name = "holder_pid", nullable = false)
    private String holderPid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @NotBlank(message = "Credential type is required")
    @Pattern(regexp = "^(MembershipCredential|DataProcessorCredential)$", message = "Credential type must be 'MembershipCredential' or 'DataProcessorCredential'")
    @Column(name = "credential_type", nullable = false)
    private String credentialType;

    @NotBlank(message = "Format is required")
    @Pattern(regexp = "VC1_0_JWT", message = "Format must be 'VC1_0_JWT'")
    @Column(name = "format", nullable = false)
    private String format;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CredentialStatus status = CredentialStatus.REQUESTED;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "credential_hash", nullable = false)
    private String credentialHash;

    public enum CredentialStatus {
        REQUESTED, ISSUED, EXPIRED, REVOKED, SUSPENDED, ERROR
    }

    public String generateCredentialHash() {
        try {
            // Create a unique string combining externalId, requestId, issuerDid, holderPid, credentialType, and format
            String dataToHash = externalId + "|" + requestId + "|" + issuerDid + "|" + holderPid + "|" + credentialType + "|" + format;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
