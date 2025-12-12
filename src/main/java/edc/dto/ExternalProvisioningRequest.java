package edc.dto;

import lombok.Data;

@Data
public class ExternalProvisioningRequest {
    private String participantName;
    private String did;
    private String kubeHost;
}
