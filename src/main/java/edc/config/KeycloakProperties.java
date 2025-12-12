package edc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.security.keycloak")
public class KeycloakProperties {
    private String baseUrl;
    private Admin admin = new Admin();
    private Map<String, String> customAttributeKeys;
    private Client client = new Client();

    @Data
    public static class Admin {
        private String username;
        private String password;
    }

    @Data
    public static class Client {
        private String rootUrl;
        private List<String> redirectUris;
        private List<String> postLogoutRedirectUris;
        private List<String> webOrigins;
    }


    public String getTenantKey() {
        return customAttributeKeys != null ? customAttributeKeys.get("tenant") : null;
    }
}
