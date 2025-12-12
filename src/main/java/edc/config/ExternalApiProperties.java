package edc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "external.api")
public class ExternalApiProperties {

    private String baseUrl;
    private Provisioner provisioner = new Provisioner();
    private Credentials credentials = new Credentials();
    private String kubeHost;
    private String didTemplate;
    private String issuerDid;
    private String holderPid;
    private String apiKey;

    @Data
    public static class Provisioner {
        private String endpoint;
    }

    @Data
    public static class Credentials {
        private String endpoint;
    }
}
