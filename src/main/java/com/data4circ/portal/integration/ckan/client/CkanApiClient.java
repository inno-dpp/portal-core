package com.data4circ.portal.integration.ckan.client;

import com.data4circ.portal.common.config.CkanConfig;
import com.data4circ.portal.features.platformsettings.service.CkanSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client for interacting with CKAN platform API
 */
@Component
public class CkanApiClient {

    private static final Logger logger = LoggerFactory.getLogger(CkanApiClient.class);

    private final RestTemplate ckanRestTemplate;
    private final CkanConfig.CkanProperties ckanProperties;
    private final CkanSettingsService ckanSettings;

    public CkanApiClient(@Qualifier("ckanRestTemplate") RestTemplate ckanRestTemplate,
                        CkanConfig.CkanProperties ckanProperties,
                        CkanSettingsService ckanSettings) {
        this.ckanRestTemplate = ckanRestTemplate;
        this.ckanProperties = ckanProperties;
        this.ckanSettings = ckanSettings;
    }

    /**
     * Create a new dataset (package) in CKAN
     * Makes actual HTTP POST request to CKAN platform
     *
     * @param datasetData map of dataset properties (name, title, owner_org, notes, extras, resources, etc.)
     * @param authToken JWT token for CKAN authorization
     * @return CKAN API response as JSON string
     * @throws CkanApiException if the API call fails
     */
    public String createDataset(Map<String, Object> datasetData, String authToken) {
        logger.info("=== CKAN API Call: package_create ===");
        logger.info("Creating dataset in CKAN with name: {}", datasetData.get("name"));
        logger.debug("Full dataset data: {}", datasetData);

        try {
            // Construct the CKAN API URL
            String url = ckanSettings.getBaseUrl() + ckanProperties.getEndpoints().get("package-create");
            logger.debug("CKAN API URL: {}", url);

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authToken);

            // Create request entity with dataset data
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(datasetData, headers);

            // Make POST request to CKAN
            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);

            logger.info("CKAN API response status: {}", response.getStatusCode());
            logger.debug("CKAN API response body: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

            return response.getBody();

        } catch (RestClientException e) {
            logger.error("Error calling CKAN API: {}", e.getMessage(), e);
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to create dataset in CKAN", e);
        }
    }

    /**
     * Get total dataset count from CKAN platform
     * Uses package_search with rows=0 for efficient count retrieval
     *
     * @param authToken JWT token for CKAN authorization
     * @return Total number of datasets, or null if CKAN is unavailable
     */
    public Integer getDatasetCount(String authToken) {
        logger.info("=== CKAN API Call: package_search (count only) ===");

        try {
            // Construct the CKAN API URL with rows=0 and include_private=true parameters
            String endpoint = ckanProperties.getEndpoints().get("package-search");
            String url = ckanSettings.getBaseUrl() + endpoint + "?rows=0&include_private=true";
            logger.debug("CKAN API URL: {}", url);

            // Prepare headers with JWT authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authToken);

            // Create request entity (GET request with headers only)
            HttpEntity<Void> request = new HttpEntity<>(headers);

            // Make GET request to CKAN
            ResponseEntity<CkanPackageSearchResponseDTO> response = ckanRestTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                CkanPackageSearchResponseDTO.class
            );

            logger.info("CKAN API response status: {}", response.getStatusCode());

            CkanPackageSearchResponseDTO body = response.getBody();
            if (body == null || !body.isSuccess() || body.getResult() == null) {
                logger.warn("CKAN API returned unsuccessful response or null result");
                return null;
            }

            int count = body.getResult().getCount();
            logger.info("CKAN dataset count: {}", count);
            logger.info("=== End CKAN API Call ===");

            return count;

        } catch (RestClientException e) {
            logger.error("Error calling CKAN API for dataset count: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            // Return null instead of throwing exception - allows dashboard to show "N/A"
            return null;
        }
    }

    /**
     * Get dataset count owned by a single CKAN organization.
     * Uses {@code package_search?fq=organization:<slug>&rows=0} for an efficient count-only
     * response.
     *
     * <p>Note: CKAN's solr-indexed {@code owner_org} field is the organisation <em>UUID</em>,
     * not the slug — filtering by {@code owner_org:<slug>} silently returns 0. The slug
     * lookup goes through the {@code organization} field instead.</p>
     *
     * @param authToken JWT token for CKAN authorization
     * @param ckanOrgSlug CKAN organization short name (e.g. "acmecorp", "greentechsolutions")
     * @return Dataset count, or {@code null} if CKAN is unavailable or the org does not exist
     */
    public Integer getDatasetCountByOwnerOrg(String authToken, String ckanOrgSlug) {
        if (ckanOrgSlug == null || ckanOrgSlug.isBlank()) {
            return null;
        }
        logger.info("=== CKAN API Call: package_search by organization='{}' (count only) ===", ckanOrgSlug);

        try {
            String endpoint = ckanProperties.getEndpoints().get("package-search");
            String url = ckanSettings.getBaseUrl() + endpoint
                    + "?fq=organization:" + ckanOrgSlug
                    + "&rows=0&include_private=true";
            logger.debug("CKAN API URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<CkanPackageSearchResponseDTO> response = ckanRestTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                CkanPackageSearchResponseDTO.class
            );

            CkanPackageSearchResponseDTO body = response.getBody();
            if (body == null || !body.isSuccess() || body.getResult() == null) {
                logger.warn("CKAN API returned unsuccessful response for owner_org '{}'", ckanOrgSlug);
                return null;
            }

            int count = body.getResult().getCount();
            logger.info("CKAN dataset count for owner_org '{}': {}", ckanOrgSlug, count);
            return count;

        } catch (RestClientException e) {
            logger.error("Error calling CKAN API for owner_org '{}' count: {}", ckanOrgSlug, e.getMessage());
            return null;
        }
    }

    /**
     * Get dataset count within a single CKAN group (category).
     * Uses {@code package_search?fq=groups:<group>&rows=0} for an efficient count-only response.
     *
     * @param authToken JWT token for CKAN authorization
     * @param groupName CKAN group slug (e.g. "catalytic-converters", "plastic-agriculture")
     * @return Dataset count within the group, or {@code null} if CKAN is unavailable or the group does not exist
     */
    public Integer getDatasetCountInGroup(String authToken, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        logger.info("=== CKAN API Call: package_search by group='{}' (count only) ===", groupName);

        try {
            String endpoint = ckanProperties.getEndpoints().get("package-search");
            // fq filters by group; rows=0 returns just the count, no result list; include_private to match the total-count call.
            String url = ckanSettings.getBaseUrl() + endpoint
                    + "?fq=groups:" + groupName
                    + "&rows=0&include_private=true";
            logger.debug("CKAN API URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<CkanPackageSearchResponseDTO> response = ckanRestTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                CkanPackageSearchResponseDTO.class
            );

            CkanPackageSearchResponseDTO body = response.getBody();
            if (body == null || !body.isSuccess() || body.getResult() == null) {
                logger.warn("CKAN API returned unsuccessful response for group '{}'", groupName);
                return null;
            }

            int count = body.getResult().getCount();
            logger.info("CKAN dataset count in group '{}': {}", groupName, count);
            return count;

        } catch (RestClientException e) {
            logger.error("Error calling CKAN API for group '{}' count: {}", groupName, e.getMessage());
            return null;
        }
    }

    /**
     * Permanently delete (purge) a dataset from CKAN
     * This is a hard delete that cannot be undone.
     * Primarily used for cleaning up test data.
     *
     * @param datasetId the dataset name or ID to purge
     * @param authToken JWT token for CKAN authorization
     * @return true if deletion was successful
     * @throws CkanApiException if the API call fails
     */
    public boolean purgeDataset(String datasetId, String authToken) {
        logger.info("=== CKAN API Call: dataset_purge ===");
        logger.info("Purging dataset from CKAN with id: {}", datasetId);

        try {
            String url = ckanSettings.getBaseUrl() + ckanProperties.getEndpoints().get("dataset-purge");
            logger.debug("CKAN API URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authToken);

            // CKAN dataset_purge expects {"id": "dataset_name_or_id"}
            Map<String, String> requestBody = Map.of("id", datasetId);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);

            logger.info("CKAN API response status: {}", response.getStatusCode());
            logger.debug("CKAN API response body: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

            return response.getStatusCode().is2xxSuccessful();

        } catch (RestClientException e) {
            logger.error("Error calling CKAN API for dataset purge: {}", e.getMessage(), e);
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to purge dataset from CKAN: " + datasetId, e);
        }
    }

    /**
     * Create an organization in CKAN
     *
     * @param shortName The CKAN-compatible short name (lowercase, no special chars)
     * @param fullName The display name of the organization
     * @throws CkanApiException with specific error codes for different failure scenarios
     */
    public void createOrganization(String shortName, String fullName) {
        logger.info("=== CKAN API Call: organization_create ===");
        logger.info("Creating organization in CKAN: {} ({})", fullName, shortName);

        String endpoint = ckanProperties.getEndpoints().get("organization-create");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", shortName);
        requestBody.put("title", fullName);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("Organization '{}' created successfully. Status: {}", shortName, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to create organization. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null && responseBody.contains("Group name already exists")) {
                    throw new CkanApiException("Group name already exists in CKAN: " + shortName, e);
                }
                throw new CkanApiException("Organization already exists in CKAN with name: " + shortName, e);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new CkanApiException("Invalid organization data. Please check the organization name format.", e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to create organization in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to create organization in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while creating organization: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to create organization: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to create organization in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Check if an organization exists in CKAN
     *
     * @param orgId The organization ID or short name
     * @return true if organization exists, false otherwise
     */
    public boolean organizationExists(String orgId) {
        logger.info("=== CKAN API Call: organization_show ===");
        logger.debug("Checking if organization exists in CKAN: {}", orgId);

        String endpoint = ckanProperties.getEndpoints().get("organization-show");
        String url = ckanSettings.getBaseUrl() + endpoint + "?id=" + orgId;

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = ckanRestTemplate.exchange(url, HttpMethod.GET, request, String.class);
            logger.info("=== End CKAN API Call ===");

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Organization '{}' exists in CKAN", orgId);
                return true;
            }
            return false;

        } catch (HttpClientErrorException e) {
            logger.info("=== End CKAN API Call ===");
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.debug("Organization '{}' does not exist in CKAN", orgId);
                return false;
            }
            logger.warn("Error checking organization existence in CKAN: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Error checking organization existence in CKAN: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            return false;
        }
    }

    /**
     * Create a user in CKAN
     *
     * @param username The username (CKAN-compatible)
     * @param email The user's email address
     * @param password The user's password (plain text, not base64 encoded)
     * @throws CkanApiException with specific error codes for different failure scenarios
     */
    public void createUser(String username, String email, String password) {
        logger.info("=== CKAN API Call: user_create ===");
        logger.info("Creating user in CKAN: {}", username);

        String endpoint = ckanProperties.getEndpoints().get("user-create");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", username);
        requestBody.put("email", email);
        requestBody.put("password", password);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("User '{}' created successfully. Status: {}", username, response.getStatusCode());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to create user. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null && responseBody.contains("email")) {
                    throw new CkanApiException("Email already registered in CKAN for a different user. Username: " + username, e);
                }
                throw new CkanApiException("User already exists in CKAN with username: " + username, e);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new CkanApiException("Invalid user data. Please check username, email format, or password requirements.", e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to create user in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to create user in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while creating user: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to create user: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to create user in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Add a user as member to an organization in CKAN
     *
     * @param orgShortName The organization's short name
     * @param username The username to add
     * @param role The role to assign (e.g., "editor", "admin", "member")
     * @throws CkanApiException with specific error codes for different failure scenarios
     */
    public void addUserToOrganization(String orgShortName, String username, String role) {
        logger.info("=== CKAN API Call: organization_member_create ===");
        logger.info("Adding user '{}' to organization '{}' with role '{}'", username, orgShortName, role);

        String endpoint = ckanProperties.getEndpoints().get("organization-member-create");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("id", orgShortName);
        requestBody.put("username", username);
        requestBody.put("role", role);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("User '{}' added to organization '{}' successfully. Status: {}", username, orgShortName, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to add user to organization. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new CkanApiException("User is already a member of organization: " + orgShortName, e);
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CkanApiException("Organization or user not found in CKAN. Organization: " + orgShortName + ", User: " + username, e);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new CkanApiException("Invalid request. Please check organization name and username.", e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to add user to organization in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to add user to organization in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while adding user to organization: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to add user to organization: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to add user to organization in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Add a user as member to a group in CKAN (e.g. one of the shared use-case categories).
     * Mirrors {@link #addUserToOrganization(String, String, String)} almost exactly — same auth,
     * same idempotent-on-409 shape — just pointed at CKAN's group-scoped member action
     * ({@code group_member_create}) instead of the organization-scoped one.
     *
     * @param groupSlug The group's short name/slug
     * @param username  The username to add
     * @param role      The role to assign (e.g., "editor", "admin", "member")
     * @throws CkanApiException with specific error codes for different failure scenarios
     */
    public void addUserToGroup(String groupSlug, String username, String role) {
        logger.info("=== CKAN API Call: group_member_create ===");
        logger.info("Adding user '{}' to group '{}' with role '{}'", username, groupSlug, role);

        String endpoint = ckanProperties.getEndpoints().get("group-member-create");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("id", groupSlug);
        requestBody.put("username", username);
        requestBody.put("role", role);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("User '{}' added to group '{}' successfully. Status: {}", username, groupSlug, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to add user to group. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new CkanApiException("User is already a member of group: " + groupSlug, e);
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CkanApiException("Group or user not found in CKAN. Group: " + groupSlug + ", User: " + username, e);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new CkanApiException("Invalid request. Please check group name and username.", e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to add user to group in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to add user to group in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while adding user to group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to add user to group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to add user to group in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Create an API token for a CKAN user.
     * Uses the admin token to create a token for the specified user.
     * The token is only returned once at creation and cannot be retrieved later.
     *
     * @param username the CKAN username to create a token for
     * @param tokenName a descriptive name for the token
     * @return the generated API token string
     * @throws CkanApiException if the API call fails
     */
    public String createApiToken(String username, String tokenName) {
        logger.info("=== CKAN API Call: api_token_create ===");
        logger.info("Creating API token for user '{}' with name '{}'", username, tokenName);

        String endpoint = ckanProperties.getEndpoints().get("api-token-create");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("user", username);
        requestBody.put("name", tokenName);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = ckanRestTemplate.postForEntity(url, request, Map.class);
            logger.info("API token created successfully for user '{}'. Status: {}", username, response.getStatusCode());

            // Extract token from response: {"success": true, "result": {"token": "...", ...}}
            Map<String, Object> body = response.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) body.get("result");
                if (result != null && result.get("token") != null) {
                    String token = result.get("token").toString();
                    logger.info("=== End CKAN API Call ===");
                    return token;
                }
            }

            logger.warn("API token creation returned unexpected response format");
            logger.info("=== End CKAN API Call ===");
            throw new CkanApiException("Unexpected response format from CKAN api_token_create");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to create API token. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CkanApiException("User not found in CKAN: " + username, e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to create API token.", e);
            } else {
                throw new CkanApiException("Failed to create API token in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while creating API token: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (CkanApiException e) {
            throw e; // Re-throw our own exceptions
        } catch (RestClientException e) {
            logger.error("Failed to create API token: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to create API token in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Patch an organization in CKAN (partial update).
     * Uses organization_patch to update only the specified fields, leaving others untouched.
     *
     * @param orgShortName The organization's short name (used as ID)
     * @param fields Map of fields to update (e.g., "extras" for custom fields)
     * @throws CkanApiException if the API call fails
     */
    public void patchOrganization(String orgShortName, Map<String, Object> fields) {
        logger.info("=== CKAN API Call: organization_patch ===");
        logger.info("Patching organization in CKAN: {}", orgShortName);

        String endpoint = ckanProperties.getEndpoints().get("organization-patch");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>(fields);
        requestBody.put("id", orgShortName);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("Organization '{}' patched successfully. Status: {}", orgShortName, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to patch organization. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CkanApiException("Organization not found in CKAN: " + orgShortName, e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to patch organization in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to patch organization in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while patching organization: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to patch organization: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to patch organization in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Patch a dataset in CKAN (partial update).
     * Uses package_patch to update only the specified fields, leaving others untouched.
     *
     * @param datasetId The dataset's CKAN ID or name
     * @param fields Map of fields to update (e.g., "extras" for custom fields)
     * @param authToken JWT token for CKAN authorization
     * @return CKAN API response as JSON string
     * @throws CkanApiException if the API call fails
     */
    public String patchDataset(String datasetId, Map<String, Object> fields, String authToken) {
        logger.info("=== CKAN API Call: package_patch ===");
        logger.info("Patching dataset in CKAN: {}", datasetId);

        String endpoint = ckanProperties.getEndpoints().get("package-patch");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>(fields);
        requestBody.put("id", datasetId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("Dataset '{}' patched successfully. Status: {}", datasetId, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

            return response.getBody();

        } catch (HttpClientErrorException e) {
            logger.error("Failed to patch dataset. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CkanApiException("Dataset not found in CKAN: " + datasetId, e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to patch dataset in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to patch dataset in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while patching dataset: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to patch dataset: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to patch dataset in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * List all groups (use-case categories) known to CKAN.
     * Uses the default {@code group_list} response shape, which is just an array of group
     * slugs/names (no {@code all_fields=true}) — exactly what's needed to grant a user
     * membership on each one.
     *
     * <p>Read-only and best-effort: unlike the write operations in this client, a failure here
     * does not throw — it returns an empty list so callers (currently just onboarding sync's
     * non-fatal group-membership step) can treat "CKAN unreachable" the same as "no groups
     * configured" rather than needing their own try/catch.</p>
     *
     * @return the slugs of all groups in CKAN, or an empty list if CKAN is unavailable
     */
    @SuppressWarnings("unchecked")
    public List<String> listGroups() {
        logger.info("=== CKAN API Call: group_list ===");

        String endpoint = ckanProperties.getEndpoints().get("group-list");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = ckanRestTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            logger.info("=== End CKAN API Call ===");

            Map<String, Object> body = response.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("success")) && body.get("result") instanceof List) {
                List<String> groups = (List<String>) body.get("result");
                logger.info("CKAN groups: {}", groups);
                return groups;
            }
            logger.warn("CKAN group_list returned unsuccessful or unexpected response");
            return List.of();

        } catch (RestClientException e) {
            logger.error("Error calling CKAN API for group list: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            return List.of();
        }
    }

    /**
     * Retrieve an organization's details from CKAN.
     *
     * @param orgShortName The organization's short name (used as ID)
     * @return Map containing the organization details, or null if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrganization(String orgShortName) {
        logger.info("=== CKAN API Call: organization_show ===");
        logger.debug("Fetching organization from CKAN: {}", orgShortName);

        String endpoint = ckanProperties.getEndpoints().get("organization-show");
        String url = ckanSettings.getBaseUrl() + endpoint + "?id=" + orgShortName;

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = ckanRestTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            logger.info("=== End CKAN API Call ===");

            Map<String, Object> body = response.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                return (Map<String, Object>) body.get("result");
            }
            return null;

        } catch (HttpClientErrorException e) {
            logger.info("=== End CKAN API Call (with error) ===");
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw new CkanApiException("Failed to get organization from CKAN: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to get organization from CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Create a group (use-case category) in CKAN.
     * Mirrors {@link #createOrganization(String, String)}.
     *
     * @param slug        The group's CKAN name (short name/slug)
     * @param title       The group's display title
     * @param description The group's description
     * @throws CkanApiException with specific error codes for different failure scenarios
     */
    public void createGroup(String slug, String title, String description) {
        logger.info("=== CKAN API Call: group_create ===");
        logger.info("Creating group in CKAN: {} ({})", title, slug);

        String endpoint = ckanProperties.getEndpoints().get("group-create");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", slug);
        requestBody.put("title", title);
        requestBody.put("description", description);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("Group '{}' created successfully. Status: {}", slug, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to create group. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new CkanApiException("Group already exists in CKAN with name: " + slug, e);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new CkanApiException("Invalid group data. Please check the group name format.", e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to create group in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to create group in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while creating group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to create group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to create group in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Patch a group in CKAN (partial update).
     * Uses group_patch to update only the specified fields, leaving others untouched.
     * Mirrors {@link #patchOrganization(String, Map)}.
     *
     * @param slug   The group's CKAN name (used as ID)
     * @param fields Map of fields to update (e.g. "title", "description", or "state":"active"/"deleted")
     * @throws CkanApiException if the API call fails
     */
    public void patchGroup(String slug, Map<String, Object> fields) {
        logger.info("=== CKAN API Call: group_patch ===");
        logger.info("Patching group in CKAN: {}", slug);

        String endpoint = ckanProperties.getEndpoints().get("group-patch");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, Object> requestBody = new HashMap<>(fields);
        requestBody.put("id", slug);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("Group '{}' patched successfully. Status: {}", slug, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to patch group. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CkanApiException("Group not found in CKAN: " + slug, e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to patch group in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to patch group in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while patching group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to patch group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to patch group in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve a group's details from CKAN. Mirrors {@link #getOrganization(String)}.
     *
     * @param slug The group's CKAN name (used as ID)
     * @return Map containing the group details, or null if not found
     * @throws CkanApiException if the API call fails for a reason other than "not found"
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> showGroup(String slug) {
        logger.info("=== CKAN API Call: group_show ===");
        logger.debug("Fetching group from CKAN: {}", slug);

        String endpoint = ckanProperties.getEndpoints().get("group-show");
        String url = ckanSettings.getBaseUrl() + endpoint + "?id=" + slug;

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = ckanRestTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            logger.info("=== End CKAN API Call ===");

            Map<String, Object> body = response.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                return (Map<String, Object>) body.get("result");
            }
            return null;

        } catch (HttpClientErrorException e) {
            logger.info("=== End CKAN API Call (with error) ===");
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw new CkanApiException("Failed to get group from CKAN: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to get group from CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Check whether a group already exists in CKAN. Non-throwing convenience wrapper around
     * {@link #showGroup(String)}, mirroring {@link #organizationExists(String)}'s style — used
     * to pre-empt a raw CONFLICT from {@link #createGroup} with a friendlier form validation
     * error before attempting the create.
     *
     * @param slug The group's CKAN name
     * @return true if the group exists in CKAN, false otherwise (including on error)
     */
    public boolean groupExists(String slug) {
        try {
            return showGroup(slug) != null;
        } catch (Exception e) {
            logger.warn("Error checking group existence in CKAN: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Soft-delete a group in CKAN (sets state=deleted; does not purge). Mirrors the
     * fire-and-forget POST shape of {@link #patchOrganization(String, Map)}, but calls CKAN's
     * dedicated group_delete action.
     *
     * @param slug The group's CKAN name
     * @throws CkanApiException if the API call fails
     */
    public void deleteGroup(String slug) {
        logger.info("=== CKAN API Call: group_delete ===");
        logger.info("Deleting (soft) group in CKAN: {}", slug);

        String endpoint = ckanProperties.getEndpoints().get("group-delete");
        String url = ckanSettings.getBaseUrl() + endpoint;
        logger.debug("CKAN API URL: {}", url);

        Map<String, String> requestBody = Map.of("id", slug);

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = ckanRestTemplate.postForEntity(url, request, String.class);
            logger.info("Group '{}' deleted successfully. Status: {}", slug, response.getStatusCode());
            logger.debug("Response: {}", response.getBody());
            logger.info("=== End CKAN API Call ===");

        } catch (HttpClientErrorException e) {
            logger.error("Failed to delete group. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            logger.info("=== End CKAN API Call (with error) ===");

            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CkanApiException("Group not found in CKAN: " + slug, e);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CkanApiException("Unauthorized: Invalid CKAN admin token.", e);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CkanApiException("Forbidden: Insufficient permissions to delete group in CKAN.", e);
            } else {
                throw new CkanApiException("Failed to delete group in CKAN: " + e.getMessage(), e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("CKAN server error while deleting group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("CKAN server error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            logger.error("Failed to delete group: {}", e.getMessage());
            logger.info("=== End CKAN API Call (with error) ===");
            throw new CkanApiException("Failed to delete group in CKAN: " + e.getMessage(), e);
        }
    }

    /**
     * Create HTTP headers with admin authorization token
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", ckanSettings.getJwtToken());
        return headers;
    }

    /**
     * Custom exception for CKAN API errors
     */
    public static class CkanApiException extends RuntimeException {
        public CkanApiException(String message) {
            super(message);
        }

        public CkanApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
