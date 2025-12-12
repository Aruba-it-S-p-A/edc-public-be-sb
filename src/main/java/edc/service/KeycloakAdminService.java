package edc.service;

import com.fasterxml.jackson.databind.JsonNode;
import edc.config.KeycloakProperties;
import edc.exception.KeycloakAdminException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Keycloak administration service:
 * - Creates Realm
 * - Creates Client
 * - Creates Client Roles
 * - Creates User
 * - Sets user claims and protocol mapper for JWT
 */
@Slf4j
@Service
public class KeycloakAdminService {

    private final RestClient restClient;
    private final KeycloakProperties keycloakProperties;

    private String adminToken;
    private LocalDateTime tokenExpiry;

    // === API Keycloak ===
    private static final String TOKEN_PATH = "/realms/master/protocol/openid-connect/token";
    private static final String REALMS_PATH = "/admin/realms";
    private static final String CLIENTS_PATH = "/admin/realms/{realm}/clients";
    private static final String CLIENT_QUERY_PATH = "/admin/realms/{realm}/clients?clientId={clientId}";
    private static final String CLIENT_ROLE_PATH = "/admin/realms/{realm}/clients/{clientId}/roles";
    private static final String CLIENT_ROLE_BY_NAME_PATH = "/admin/realms/{realm}/clients/{clientId}/roles/{role}";
    private static final String CLIENT_PROTOCOL_MAPPER_PATH = "/admin/realms/{realm}/clients/{clientId}/protocol-mappers/models";
    private static final String USERS_PATH = "/admin/realms/{realm}/users";
    private static final String USER_PROFILE_PATH = "/admin/realms/{realm}/users/profile";
    private static final String USER_SEARCH_PATH = "/admin/realms/{realm}/users?username={username}";
    private static final String USER_ROLE_MAPPING_PATH =  "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientId}";
    private static final String REALM_ROLE_BY_NAME_PATH = "/admin/realms/{realm}/roles/{role}";
    private static final String USER_REALM_ROLE_MAPPING_PATH = "/admin/realms/{realm}/users/{userId}/role-mappings/realm";
    private static final String USER_DELETE_PATH = "/admin/realms/{realm}/users/{userId}";


    private static final List<Map<String, Object>> BASE_ATTRIBUTES = buildBaseAttributes();
    private static final List<Map<String, Object>> BASE_GROUPS = buildBaseGroups();

    public KeycloakAdminService(@Qualifier("keycloakRestClient") RestClient restClient,
                                KeycloakProperties keycloakProperties) {
        this.restClient = restClient;
        this.keycloakProperties = keycloakProperties;
    }

    /**
     * Returns an admin token for the master realm, with temporal caching.
     */
    private String getAdminToken() {
//        if (adminToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
//            return adminToken;
//        }
        try {

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("client_id", "admin-cli");
            formData.add("username", keycloakProperties.getAdmin().getUsername());
            formData.add("password", keycloakProperties.getAdmin().getPassword());


            JsonNode node = restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(JsonNode.class);

            this.adminToken = node.get("access_token").asText();
            int expiresIn = node.get("expires_in").asInt();
            this.tokenExpiry = LocalDateTime.now().plusSeconds(expiresIn - 30);
            return adminToken;

        } catch (RestClientResponseException e) {
            log.error("Error obtaining admin token from Keycloak: {}", e.getResponseBodyAsString(), e);
            throw new KeycloakAdminException("Unable to obtain admin token", e);
        }
    }

    /**
     * Creates realm, client, client roles, user, user claims and protocol mapper.
     */
    public void createRealmClientUserWithClaim(String realmName,
                                               String clientId,
                                               String username,
                                               String password,
                                               String claimKey,
                                               String claimValue) {
        try {
            // 1. Create the realm
            createRealm(realmName);

            // 1b. Create the tenantName attribute (or any claimKey) in the user profile
            addUserProfileAttribute(realmName, claimKey);

            // 2. Create the client
            String clientUuid = createClient(realmName,
                                            clientId,
                                            keycloakProperties.getClient().getRootUrl(),
                                            keycloakProperties.getClient().getRedirectUris(),
                                            keycloakProperties.getClient().getPostLogoutRedirectUris(),
                                            keycloakProperties.getClient().getWebOrigins());

            // 3. Add the protocol mapper for the claim
            createUserAttributeProtocolMapper(realmName, clientUuid, claimKey);

            // 4. Create client roles
            List<String> roles = List.of("EDC_ADMIN", "EDC_USER_TENANT", "EDC_USER_PARTICIPANT", "EDC_USER_SUBMIT");
            createClientRoles(realmName, clientUuid, roles);

            // 5. Create the user with claim
            String userId = createUserWithClaim(realmName, username, password, claimKey, claimValue);

            // 6. Assign client roles to the user
            assignClientRolesToUser(realmName, clientUuid, userId, roles);

            log.info("Realm [{}], client [{}], user [{}] created with claim [{}]",
                    realmName, clientId, username, claimKey);

        } catch (RestClientResponseException e) {
            log.error("Keycloak error [{}]: {}", e.getRawStatusCode(), e.getResponseBodyAsString(), e);
            throw new KeycloakAdminException("Error creating realm/client/user", e);
        }
    }

    /**
     * Creates realm, client, client roles, user, user claims and protocol mapper.
     */
    public void createUserWithRealmRolesAndClaim(String realmName,
                                               String username,
                                               String password,
                                               String claimKey,
                                               String claimValue,
                                                List<String> realmRoles
                                           ) {
        try {

            // 1. Create the user with claim
            String userId = createUserWithClaim(realmName, username, password, claimKey, claimValue);

            // 2. Assign realm roles to the user
            assignRealmRolesToUser(realmName, userId, realmRoles);

            log.info("Realm [{}] - user [{}], roles [{}] created with claim [{}:{}]",
                    realmName, username, realmRoles, claimKey, claimValue);

        } catch (RestClientResponseException e) {
            log.error("Keycloak error [{}]: {}", e.getRawStatusCode(), e.getResponseBodyAsString(), e);
            throw new KeycloakAdminException("Error creating realm/client/user", e);
        }
    }


    /**
     * Deletes a Keycloak user given the realm and username.
     */
    public void deleteUserByUsername(String realmName, String username) {
        try {
            // 1. Retrieve user
            JsonNode list = restClient.get()
                    .uri(USER_SEARCH_PATH, realmName, username)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .retrieve()
                    .body(JsonNode.class);

            if (list == null || !list.isArray() || list.size() == 0) {
                log.info("No user found with username [{}] in realm [{}]", username, realmName);
                return; // or throw exception if you want it to be blocking
            }

            String userId = list.get(0).get("id").asText();

            // 2. Delete user
            restClient.delete()
                    .uri(USER_DELETE_PATH, realmName, userId)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .retrieve()
                    .toBodilessEntity();

            log.info("User [{}] deleted from realm [{}]", username, realmName);

        } catch (RestClientResponseException e) {
            log.error("Error deleting user [{}] from realm [{}]: {}",
                    username, realmName, e.getResponseBodyAsString(), e);
            throw new KeycloakAdminException("Unable to delete user " + username, e);
        }
    }


    private void createRealm(String realmName) {
        Map<String, Object> realm = Map.of("realm", realmName, "enabled", true);
        restClient.post()
                .uri(REALMS_PATH)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(realm)
                .retrieve()
                .toBodilessEntity();
            log.info("Realm {} created", realmName);
    }

    private String createClient(String realmName,
                                String clientId,
                                String rootUrl,
                                List<String> redirectUris,
                                List<String> postLogoutRedirectUris,
                                List<String> webOrigins) {
        Map<String, Object> client = new HashMap<>();
        client.put("clientId", clientId);
        client.put("protocol", "openid-connect");
        client.put("rootUrl", rootUrl);
        client.put("redirectUris", redirectUris);
        //client.put("postLogoutRedirectUris", postLogoutRedirectUris);
        client.put("webOrigins", webOrigins);
        client.put("publicClient", true);                 // disables client authentication
        client.put("standardFlowEnabled", true);          // enables Authorization Code Flow
        client.put("directAccessGrantsEnabled", false);   // optional
        client.put("serviceAccountsEnabled", false);      // optional
        client.put("authorizationServicesEnabled", false);// disables Authorization

        restClient.post()
                .uri(CLIENTS_PATH, realmName)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(client)
                .retrieve()
                .toBodilessEntity();

        JsonNode list = restClient.get()
                .uri(CLIENT_QUERY_PATH, realmName, clientId)
                .header("Authorization", "Bearer " + getAdminToken())
                .retrieve()
                .body(JsonNode.class);

        String clientUuid = list.get(0).get("id").asText();
        log.info("Client {} created (UUID: {})", clientId, clientUuid);
        return clientUuid;
    }

    /**
     * Adds an attribute to the user profile with view and edit permissions for admin and user.
     */
    private void addUserProfileAttribute(String realmName, String attributeName) {
        // Clone the base list to avoid modifying it
        List<Map<String, Object>> allAttributes = new ArrayList<>(BASE_ATTRIBUTES);

        // Attributo dinamico
        Map<String, Object> customAttr = new HashMap<>();
        customAttr.put("name", attributeName);
        customAttr.put("displayName", "");
        customAttr.put("permissions", Map.of(
                "edit", List.of("admin", "user"),
                "view", List.of("user", "admin")
        ));
        customAttr.put("multivalued", false);
        customAttr.put("annotations", Collections.emptyMap());
        customAttr.put("validations", Collections.emptyMap());

        allAttributes.add(customAttr);

        Map<String, Object> profile = new HashMap<>();
        profile.put("attributes", allAttributes);
        profile.put("groups", BASE_GROUPS);

        restClient.put()
                .uri(USER_PROFILE_PATH, realmName)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(profile)
                .retrieve()
                .toBodilessEntity();

        log.info("Created User Profile attribute [{}] with admin+user permissions in realm {}", attributeName, realmName);
    }

    private static List<Map<String, Object>> buildBaseAttributes() {
        List<Map<String, Object>> attrs = new ArrayList<>();

        // username
        attrs.add(Map.of(
                "name", "username",
                "displayName", "${username}",
                "validations", Map.of(
                        "length", Map.of("min", 3, "max", 255),
                        "username-prohibited-characters", Collections.emptyMap(),
                        "up-username-not-idn-homograph", Collections.emptyMap()
                ),
                "permissions", Map.of(
                        "view", List.of("admin", "user"),
                        "edit", List.of("admin", "user")
                ),
                "multivalued", false
        ));

        // email
        attrs.add(Map.of(
                "name", "email",
                "displayName", "${email}",
                "validations", Map.of(
                        "email", Collections.emptyMap(),
                        "length", Map.of("max", 255)
                ),
                "required", Map.of("roles", List.of("user")),
                "permissions", Map.of(
                        "view", List.of("admin", "user"),
                        "edit", List.of("admin", "user")
                ),
                "multivalued", false
        ));

        // firstName
        attrs.add(Map.of(
                "name", "firstName",
                "displayName", "${firstName}",
                "validations", Map.of(
                        "length", Map.of("max", 255),
                        "person-name-prohibited-characters", Collections.emptyMap()
                ),
                "required", Map.of("roles", List.of("user")),
                "permissions", Map.of(
                        "view", List.of("admin", "user"),
                        "edit", List.of("admin", "user")
                ),
                "multivalued", false
        ));

        // lastName
        attrs.add(Map.of(
                "name", "lastName",
                "displayName", "${lastName}",
                "validations", Map.of(
                        "length", Map.of("max", 255),
                        "person-name-prohibited-characters", Collections.emptyMap()
                ),
                "required", Map.of("roles", List.of("user")),
                "permissions", Map.of(
                        "view", List.of("admin", "user"),
                        "edit", List.of("admin", "user")
                ),
                "multivalued", false
        ));

        return attrs;
    }

    private static List<Map<String, Object>> buildBaseGroups() {
        return List.of(
                Map.of(
                        "name", "user-metadata",
                        "displayHeader", "User metadata",
                        "displayDescription", "Attributes, which refer to user metadata"
                )
        );
    }



    /**
     * Creates a protocol mapper that extracts a user attribute (claimKey) and inserts it into the token.
     */
    private void createUserAttributeProtocolMapper(String realmName, String clientUuid, String claimKey) {
        Map<String, Object> mapper = new HashMap<>();
        mapper.put("name", claimKey);
        mapper.put("protocol", "openid-connect");
        mapper.put("protocolMapper", "oidc-usermodel-attribute-mapper");

        Map<String, String> config = new HashMap<>();
        config.put("user.attribute", claimKey);
        config.put("claim.name", claimKey);
        config.put("jsonType.label", "String");
        config.put("id.token.claim", "true");
        config.put("access.token.claim", "true");

        mapper.put("config", config);

        restClient.post()
                .uri(CLIENT_PROTOCOL_MAPPER_PATH, realmName, clientUuid)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(mapper)
                .retrieve()
                .toBodilessEntity();

        log.info("Protocol mapper for claim [{}] created on client {}", claimKey, clientUuid);
    }

    private void createClientRoles(String realmName, String clientUuid, List<String> roles) {
        for (String role : roles) {
            restClient.post()
                    .uri(CLIENT_ROLE_PATH, realmName, clientUuid)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .body(Map.of("name", role))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Client role {} created", role);
        }
    }

    private String createUserWithClaim(String realmName,
                                       String username,
                                       String password,
                                       String claimKey,
                                       String claimValue) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("enabled", true);
        user.put("firstName", username);
        user.put("lastName", username);
        user.put("email", username+"@mail.com");
        user.put("emailVerified", true);
        user.put("attributes", Map.of(claimKey, List.of(claimValue)));
        user.put("credentials", List.of(Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        )));

        restClient.post()
                .uri(USERS_PATH, realmName)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(user)
                .retrieve()
                .toBodilessEntity();

        JsonNode node = restClient.get()
                .uri(USER_SEARCH_PATH, realmName, username)
                .header("Authorization", "Bearer " + getAdminToken())
                .retrieve()
                .body(JsonNode.class);

        String userId = node.get(0).get("id").asText();
        log.info("User {} created (ID: {}) with claim {}={}", username, userId, claimKey, claimValue);
        return userId;
    }

    private void assignClientRolesToUser(String realmName,
                                         String clientUuid,
                                         String userId,
                                         List<String> roles) {

        List<Map<String, String>> roleReps = new ArrayList<>();
        for (String role : roles) {
            JsonNode r = restClient.get()
                    .uri(CLIENT_ROLE_BY_NAME_PATH, realmName, clientUuid, role)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .retrieve()
                    .body(JsonNode.class);
            roleReps.add(Map.of("id", r.get("id").asText(), "name", r.get("name").asText()));
        }

        restClient.post()
                .uri(USER_ROLE_MAPPING_PATH, realmName, userId, clientUuid)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(roleReps)
                .retrieve()
                .toBodilessEntity();

        log.info("Assigned roles {} to user {}", roles, userId);
    }

    private void assignRealmRolesToUser(String realmName,
                                        String userId,
                                        List<String> realmRoles) {

        List<Map<String, String>> roleReps = new ArrayList<>();
        for (String role : realmRoles) {
            JsonNode r = restClient.get()
                    .uri(REALM_ROLE_BY_NAME_PATH, realmName, role)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .retrieve()
                    .body(JsonNode.class);
            roleReps.add(Map.of("id", r.get("id").asText(), "name", r.get("name").asText()));
        }

        restClient.post()
                .uri(USER_REALM_ROLE_MAPPING_PATH, realmName, userId)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(roleReps)
                .retrieve()
                .toBodilessEntity();

        log.info("Assigned roles {} to user {}", realmRoles, userId);
    }
}
