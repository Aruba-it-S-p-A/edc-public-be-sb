package edc.service;

import edc.config.ExternalApiProperties;
import edc.dto.ExternalProvisioningRequest;
import edc.dto.ExternalCredentialRequest;
import edc.exception.ExternalApiException;
import edc.util.EdcUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class ExternalApiService {

    private final ExternalApiProperties externalApiProperties;

    private final RestClient restClient;
    private final RestClient defaultRestClient;

    public ExternalApiService(ExternalApiProperties externalApiProperties,
                              @Qualifier("externalApiRestClient") RestClient restClient,
                              RestClient defaultRestClient) {
        this.externalApiProperties = externalApiProperties;
        this.restClient = restClient;
        this.defaultRestClient = defaultRestClient;
    }

    public Map<String, String> provisionParticipant(String participantName) {
        if (StringUtils.isBlank(participantName)) {
            throw new IllegalArgumentException("Participant name cannot be null or empty");
        }
        log.debug("Original participant name: {}", participantName);
        String normalizedParticipantName = EdcUtils.normalizeForInnerDnsUse(participantName);
        log.debug("Normalized participant name: {}", normalizedParticipantName);
        
        log.info("Calling external API to provision participant: {}", normalizedParticipantName);
        
        String did = buildDid(normalizedParticipantName);
        String kubeHost = buildHost(normalizedParticipantName);
        
        ExternalProvisioningRequest request = new ExternalProvisioningRequest();
        request.setParticipantName(normalizedParticipantName);
        request.setDid(did);
        request.setKubeHost(kubeHost);
        
        String url = externalApiProperties.getProvisioner().getEndpoint();
        log.trace("Provisioning request payload: {}", request);
        log.trace("Provisioning request URL: {}", url);

        try {
            Map<String, String> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes());
                        log.error("Client error calling external API for participant {}: {} - {}",
                                normalizedParticipantName, res.getStatusCode(), responseBody);
                        throw new ExternalApiException("External API client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes());
                        log.error("Server error calling external API for participant {}: {} - {}",
                                normalizedParticipantName, res.getStatusCode(), responseBody);
                        throw new ExternalApiException("External API server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, String>>() {});

            log.info("External API response for participant {}: {}", normalizedParticipantName, response);
            return response;

        } catch (ResourceAccessException e) {
            log.error("Network error calling external API for participant {}: {}",
                    normalizedParticipantName, e.getMessage());
            throw new ExternalApiException("External API network error", e);
        }
    }

    public Map<String, String> deprovisionParticipant(String participantName) {
        if (StringUtils.isBlank(participantName)) {
            throw new IllegalArgumentException("Participant name cannot be null or empty");
        }
        log.debug("Original participant name: {}", participantName);
        String normalizedParticipantName = EdcUtils.normalizeForInnerDnsUse(participantName);
        log.debug("Normalized participant name: {}", normalizedParticipantName);

        log.info("Calling external API to deprovision participant: {}", normalizedParticipantName);
        
        String kubeHost = externalApiProperties.getKubeHost();
        
        ExternalProvisioningRequest request = new ExternalProvisioningRequest();
        request.setParticipantName(normalizedParticipantName);
        request.setKubeHost(kubeHost);
        
        String url = externalApiProperties.getProvisioner().getEndpoint();

        try {
            Map<String, String> response = restClient.method(HttpMethod.DELETE)
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes());
                        log.error("Client error calling external API for deprovisioning participant {}: {} - {}",
                                normalizedParticipantName, res.getStatusCode(), responseBody);
                        throw new ExternalApiException("External API client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes());
                        log.error("Server error calling external API for deprovisioning participant {}: {} - {}",
                                normalizedParticipantName, res.getStatusCode(), responseBody);
                        throw new ExternalApiException("External API server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, String>>() {});

            log.info("External API deprovision response for participant {}: {}", normalizedParticipantName, response);
            return response;

        } catch (ResourceAccessException e) {
            log.error("Network error calling external API for deprovisioning participant {}: {}",
                    normalizedParticipantName, e.getMessage());
            throw new ExternalApiException("External API network error", e);
        }
    }

    public Map<String, String> requestCredentials(String participantName, String did, java.util.List<ExternalCredentialRequest.CredentialSpec> credentials) {
        if (StringUtils.isBlank(participantName)) {
            throw new IllegalArgumentException("Participant name cannot be null or empty");
        }
        if (StringUtils.isBlank(did)) {
            throw new IllegalArgumentException("DID cannot be null or empty");
        }
        if (credentials == null || credentials.isEmpty()) {
            throw new IllegalArgumentException("Credentials list cannot be null or empty");
        }
        log.debug("Original participant name: {}", participantName);
        String normalizedParticipantName = EdcUtils.normalizeForInnerDnsUse(participantName);
        log.debug("Normalized participant name: {}", normalizedParticipantName);
        
        log.info("Calling external API to request credentials for participant: {}", normalizedParticipantName);
        
        // Encode DID to base64 for the URL path
        String base64Did = Base64.getEncoder().encodeToString(did.getBytes());

        //create service url
        String serviceUrl = "http://identityhub." + normalizedParticipantName + ".svc.cluster.local:7081";
        
        // Build the URL with participant name and base64 DID
//        String endpoint = externalApiProperties.getCredentials().getEndpoint()
//                .replace("{participant}", normalizedParticipantName)
//                .replace("{base64Did}", base64Did);
        String endpoint = externalApiProperties.getCredentials().getEndpoint()
                .replace("{base64Did}", base64Did);



        // Build the request
        ExternalCredentialRequest request = ExternalCredentialRequest.builder()
                .issuerDid(externalApiProperties.getIssuerDid())
                .holderPid(externalApiProperties.getHolderPid())
                .credentials(credentials)
                .build();

        log.debug("url : {}", serviceUrl+endpoint);
        log.trace("Credentials request payload: {}", request);

        try {
            Map<String, String> response = defaultRestClient.post()
                    .uri(serviceUrl+endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Api-Key", externalApiProperties.getApiKey())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes());
                        log.error("Client error calling external credentials API for participant {}: {} - {}",
                                normalizedParticipantName, res.getStatusCode(), responseBody);
                        throw new ExternalApiException("External credentials API client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes());
                        log.error("Server error calling external credentials API for participant {}: {} - {}",
                                normalizedParticipantName, res.getStatusCode(), responseBody);
                        throw new ExternalApiException("External credentials API server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, String>>() {});

            log.info("External API credentials response for participant {}: {}", normalizedParticipantName, response);
            return response;

        } catch (ResourceAccessException e) {
            log.error("Network error calling external credentials API for participant {}: {}",
                    normalizedParticipantName, e.getMessage());
            throw new ExternalApiException("External credentials API network error", e);
        }

    }

    public String buildDid(String participantName) {
        String normalizedParticipantName = EdcUtils.normalizeForInnerDnsUse(participantName);
        return externalApiProperties.getDidTemplate().replace("{participant}", normalizedParticipantName);
    }

    public String buildHost(String participantName) {
        return externalApiProperties.getKubeHost();
    }
}
