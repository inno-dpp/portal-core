package com.data4circ.portal.integration.keycloak.client;

import com.data4circ.portal.integration.keycloak.KeycloakInstance;
import com.data4circ.portal.integration.keycloak.dto.KeycloakCredentialRepresentation;
import com.data4circ.portal.integration.keycloak.dto.KeycloakGroupRepresentation;
import com.data4circ.portal.integration.keycloak.dto.KeycloakTokenResponse;
import com.data4circ.portal.integration.keycloak.dto.KeycloakUserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Low-level HTTP client for the Keycloak Admin REST API. Every call is parameterized
 * by a {@link KeycloakInstance} so different Keycloak servers can be targeted per
 * organization (the default instance comes from configuration/platform settings).
 */
@Component
public class KeycloakApiClient {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakApiClient.class);

    private final RestTemplate restTemplate;

    public KeycloakApiClient(@Qualifier("keycloakRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Obtain an Admin API access token via the client_credentials grant using the
     * instance's service-account client.
     *
     * @throws KeycloakApiException with UNAUTHORIZED semantics when the client
     *                              credentials are rejected
     */
    public String obtainAdminToken(KeycloakInstance instance) {
        logger.info("Obtaining Keycloak admin token from {}", instance);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", instance.getClientId());
            form.add("client_secret", instance.getClientSecret());

            ResponseEntity<KeycloakTokenResponse> response = restTemplate.exchange(
                    URI.create(instance.tokenUrl()), HttpMethod.POST, new HttpEntity<>(form, headers),
                    KeycloakTokenResponse.class);

            KeycloakTokenResponse token = response.getBody();
            if (token == null || token.getAccessToken() == null) {
                throw new KeycloakApiException("Keycloak token endpoint returned no access token");
            }
            return token.getAccessToken();
        } catch (HttpStatusCodeException e) {
            throw wrap("Keycloak admin authentication failed", e);
        } catch (KeycloakApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak admin authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a top-level group, returning its id. A 409 CONFLICT (group already
     * exists) is resolved by looking the group up — the call is idempotent.
     */
    public String createGroup(KeycloakInstance instance, String token, String groupName) {
        logger.info("Creating Keycloak group '{}' on {}", groupName, instance);
        KeycloakGroupRepresentation group = new KeycloakGroupRepresentation();
        group.setName(groupName);
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    URI.create(instance.adminUrl("/groups")), HttpMethod.POST,
                    new HttpEntity<>(group, authHeaders(token)), Void.class);
            String id = idFromLocation(response.getHeaders().getLocation());
            if (id != null) {
                return id;
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw wrap("Keycloak group creation failed for '" + groupName + "'", e);
            }
            logger.info("Keycloak group '{}' already exists, resolving its id", groupName);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak group creation failed: " + e.getMessage(), e);
        }
        return findGroupByName(instance, token, groupName)
                .map(KeycloakGroupRepresentation::getId)
                .orElseThrow(() -> new KeycloakApiException(
                        "Keycloak group '" + groupName + "' was created or reported existing but cannot be found"));
    }

    /** Find a top-level group by exact name. */
    public Optional<KeycloakGroupRepresentation> findGroupByName(KeycloakInstance instance, String token, String name) {
        try {
            // Template expansion after encode() percent-encodes reserved characters
            // (e.g. '+', which Keycloak would otherwise decode as a space).
            URI url = UriComponentsBuilder.fromUriString(instance.adminUrl("/groups"))
                    .queryParam("search", "{search}")
                    .queryParam("exact", "true")
                    .encode()
                    .buildAndExpand(name)
                    .toUri();
            ResponseEntity<KeycloakGroupRepresentation[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                    KeycloakGroupRepresentation[].class);
            KeycloakGroupRepresentation[] groups = response.getBody();
            if (groups == null) {
                return Optional.empty();
            }
            for (KeycloakGroupRepresentation group : groups) {
                if (name.equals(group.getName())) {
                    return Optional.of(group);
                }
            }
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            throw wrap("Keycloak group lookup failed for '" + name + "'", e);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak group lookup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a user, returning their id. The user is enabled and carries the
     * UPDATE_PASSWORD required action. A 409 CONFLICT (username or email taken) is
     * resolved by username lookup — the call is idempotent for re-runs of the same
     * onboarding request.
     */
    public String createUser(KeycloakInstance instance, String token, String username,
                             String email, String firstName, String lastName) {
        logger.info("Creating Keycloak user '{}' on {}", username, instance);
        KeycloakUserRepresentation user = new KeycloakUserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setRequiredActions(List.of("UPDATE_PASSWORD"));
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    URI.create(instance.adminUrl("/users")), HttpMethod.POST,
                    new HttpEntity<>(user, authHeaders(token)), Void.class);
            String id = idFromLocation(response.getHeaders().getLocation());
            if (id != null) {
                return id;
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw wrap("Keycloak user creation failed for '" + username + "'", e);
            }
            logger.info("Keycloak user '{}' already exists, resolving their id", username);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak user creation failed: " + e.getMessage(), e);
        }
        return findUserByUsername(instance, token, username)
                .map(KeycloakUserRepresentation::getId)
                .orElseThrow(() -> new KeycloakApiException(
                        "Keycloak user '" + username + "' was created or reported existing but cannot be found"));
    }

    /** Find a user by exact username. */
    public Optional<KeycloakUserRepresentation> findUserByUsername(KeycloakInstance instance, String token,
                                                                   String username) {
        try {
            // Template expansion after encode() percent-encodes reserved characters
            // (e.g. '+' in plus-addressed emails, which Keycloak would decode as a space).
            URI url = UriComponentsBuilder.fromUriString(instance.adminUrl("/users"))
                    .queryParam("username", "{username}")
                    .queryParam("exact", "true")
                    .encode()
                    .buildAndExpand(username)
                    .toUri();
            ResponseEntity<KeycloakUserRepresentation[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                    KeycloakUserRepresentation[].class);
            KeycloakUserRepresentation[] users = response.getBody();
            if (users == null) {
                return Optional.empty();
            }
            for (KeycloakUserRepresentation user : users) {
                if (username.equalsIgnoreCase(user.getUsername())) {
                    return Optional.of(user);
                }
            }
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            throw wrap("Keycloak user lookup failed for '" + username + "'", e);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak user lookup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Set the user's password. With {@code temporary=true} Keycloak forces a change
     * at first login.
     */
    public void setPassword(KeycloakInstance instance, String token, String userId,
                            String password, boolean temporary) {
        logger.info("Setting {} password for Keycloak user id {}", temporary ? "temporary" : "permanent", userId);
        try {
            restTemplate.exchange(
                    URI.create(instance.adminUrl("/users/" + userId + "/reset-password")), HttpMethod.PUT,
                    new HttpEntity<>(KeycloakCredentialRepresentation.password(password, temporary),
                            authHeaders(token)),
                    Void.class);
        } catch (HttpStatusCodeException e) {
            throw wrap("Keycloak password reset failed for user id " + userId, e);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak password reset failed: " + e.getMessage(), e);
        }
    }

    /** Add a user to a group (idempotent by Keycloak semantics). */
    public void addUserToGroup(KeycloakInstance instance, String token, String userId, String groupId) {
        logger.info("Adding Keycloak user id {} to group id {}", userId, groupId);
        try {
            restTemplate.exchange(
                    URI.create(instance.adminUrl("/users/" + userId + "/groups/" + groupId)), HttpMethod.PUT,
                    new HttpEntity<>(authHeaders(token)), Void.class);
        } catch (HttpStatusCodeException e) {
            throw wrap("Keycloak group membership assignment failed for user id " + userId, e);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak group membership assignment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Remove one pending required action from the user (e.g. UPDATE_PASSWORD after
     * the portal has mirrored a final password), leaving any other realm-mandated
     * actions (VERIFY_EMAIL, CONFIGURE_TOTP, ...) untouched. Only the
     * requiredActions field is sent, so all other user attributes stay as they are.
     */
    public void removeRequiredAction(KeycloakInstance instance, String token, String userId, String action) {
        logger.info("Removing required action {} from Keycloak user id {}", action, userId);
        try {
            ResponseEntity<KeycloakUserRepresentation> current = restTemplate.exchange(
                    URI.create(instance.adminUrl("/users/" + userId)), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), KeycloakUserRepresentation.class);
            KeycloakUserRepresentation user = current.getBody();
            if (user == null || user.getRequiredActions() == null
                    || !user.getRequiredActions().contains(action)) {
                return;
            }
            KeycloakUserRepresentation update = new KeycloakUserRepresentation();
            update.setRequiredActions(user.getRequiredActions().stream()
                    .filter(existing -> !existing.equals(action))
                    .toList());
            restTemplate.exchange(
                    URI.create(instance.adminUrl("/users/" + userId)), HttpMethod.PUT,
                    new HttpEntity<>(update, authHeaders(token)), Void.class);
        } catch (HttpStatusCodeException e) {
            throw wrap("Keycloak required-actions update failed for user id " + userId, e);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak required-actions update failed: " + e.getMessage(), e);
        }
    }

    /** Delete a user by id (no-op if already gone). */
    public void deleteUser(KeycloakInstance instance, String token, String userId) {
        logger.info("Deleting Keycloak user id {}", userId);
        try {
            restTemplate.exchange(
                    URI.create(instance.adminUrl("/users/" + userId)), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)), Void.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return;
            }
            throw wrap("Keycloak user deletion failed for id " + userId, e);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak user deletion failed: " + e.getMessage(), e);
        }
    }

    /** Delete a group by id (no-op if already gone). */
    public void deleteGroup(KeycloakInstance instance, String token, String groupId) {
        logger.info("Deleting Keycloak group id {}", groupId);
        try {
            restTemplate.exchange(
                    URI.create(instance.adminUrl("/groups/" + groupId)), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)), Void.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return;
            }
            throw wrap("Keycloak group deletion failed for id " + groupId, e);
        } catch (Exception e) {
            throw new KeycloakApiException("Keycloak group deletion failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token);
        return headers;
    }

    /** Extracts the created resource id from a Location header, e.g. .../users/{id}. */
    private String idFromLocation(URI location) {
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < path.length() - 1 ? path.substring(lastSlash + 1) : null;
    }

    private KeycloakApiException wrap(String context, HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        String detail;
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            detail = "Unauthorized: check the admin client id/secret";
        } else if (status == HttpStatus.FORBIDDEN.value()) {
            detail = "Forbidden: the service account lacks the required realm-management roles";
        } else if (status == HttpStatus.NOT_FOUND.value()) {
            detail = "Not found: check the base URL and realm name";
        } else {
            detail = e.getResponseBodyAsString();
        }
        String message = context + ": " + e.getStatusCode() + " - " + detail;
        logger.error(message);
        return new KeycloakApiException(message, e, status);
    }

    /**
     * Exception for Keycloak Admin API errors. Carries the HTTP status when the
     * failure came from a status-coded response (0 otherwise).
     */
    public static class KeycloakApiException extends RuntimeException {

        private final int status;

        public KeycloakApiException(String message) {
            this(message, null, 0);
        }

        public KeycloakApiException(String message, Throwable cause) {
            this(message, cause, 0);
        }

        public KeycloakApiException(String message, Throwable cause, int status) {
            super(message, cause);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
