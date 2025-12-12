package edc.util;

import org.springframework.security.oauth2.jwt.Jwt;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class VisibilityUtils {

    private VisibilityUtils() {
        // Utility class
    }

    private static final String TENANT_NAME_CLAIM = "tenantName";
    private static final String NAME_CLAIM = "given_name";


    public static Optional<String> getTenantName(Jwt jwt) {
        return getClaim(jwt, TENANT_NAME_CLAIM);
    }

    public static Optional<String> getUsernameClaim(Jwt jwt) {
        return getClaim(jwt, NAME_CLAIM);
    }

    public static Optional<String> getClaim(Jwt jwt, String claimName) {
        // Retrieve the claim from the token
        String claimValue = jwt.getClaim(claimName);
        log.debug("Getting claim : {}", claimName);
        if (claimValue == null || claimValue.isEmpty()) {
            log.info("Access denied: {} claim is missing in JWT", claimName);
            return Optional.empty();
        }
        return Optional.of(claimValue);
    }





}