package cn.tolim.pudans.iam.identitysource.scim.client;

import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.scim.ScimAuthType;
import cn.tolim.pudans.iam.identitysource.scim.ScimConfig;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimError;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroup;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroupListResponse;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimPatchOp;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimServiceProviderConfig;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUser;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUserListResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.LOGGER_NAME;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.MEDIA_TYPE;


@Slf4j(topic = LOGGER_NAME)
public class ScimHttpClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String ACCEPT = "Accept";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String PATCH = "PATCH";
    private static final String DELETE = "DELETE";

    private final ScimConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ScimHttpClient(ScimConfig config) {
        this(config, buildHttpClient(config), buildObjectMapper());
    }

    public ScimHttpClient(ScimConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    // Read the service provider configuration
    public ScimServiceProviderConfig getServiceProviderConfig() {
        return exchange(GET, url(config.getServiceProviderConfigPath(), null), null,
                ScimServiceProviderConfig.class);
    }

    // List users with SCIM pagination
    public ScimUserListResponse listUsers(int startIndex, int count) {
        return exchange(GET, url(config.getUsersPath(), listQuery(startIndex, count, null)), null,
                ScimUserListResponse.class);
    }

    // Search users with a SCIM filter
    public ScimUserListResponse searchUsers(String filter, int startIndex, int count) {
        return exchange(GET, url(config.getUsersPath(), listQuery(startIndex, count, filter)), null,
                ScimUserListResponse.class);
    }

    // Read a user by identifier
    public ScimUser getUser(String id) {
        return exchange(GET, resourceUrl(config.getUsersPath(), id), null, ScimUser.class);
    }

    // Create a user
    public ScimUser createUser(ScimUser user) {
        return exchange(POST, url(config.getUsersPath(), null), user, ScimUser.class);
    }

    // Replace a user
    public ScimUser replaceUser(String id, ScimUser user) {
        return exchange(PUT, resourceUrl(config.getUsersPath(), id), user, ScimUser.class);
    }

    // Patch a user
    public ScimUser patchUser(String id, ScimPatchOp patchOp) {
        return exchange(PATCH, resourceUrl(config.getUsersPath(), id), patchOp, ScimUser.class);
    }

    // Delete a user
    public void deleteUser(String id) {
        exchange(DELETE, resourceUrl(config.getUsersPath(), id), null, null);
    }

    // List groups with SCIM pagination
    public ScimGroupListResponse listGroups(int startIndex, int count) {
        return exchange(GET, url(config.getGroupsPath(), listQuery(startIndex, count, null)), null,
                ScimGroupListResponse.class);
    }

    // Search groups with a SCIM filter
    public ScimGroupListResponse searchGroups(String filter, int startIndex, int count) {
        return exchange(GET, url(config.getGroupsPath(), listQuery(startIndex, count, filter)),
                null, ScimGroupListResponse.class);
    }

    // Read a group by identifier
    public ScimGroup getGroup(String id) {
        return exchange(GET, resourceUrl(config.getGroupsPath(), id), null, ScimGroup.class);
    }

    // Create a group
    public ScimGroup createGroup(ScimGroup group) {
        return exchange(POST, url(config.getGroupsPath(), null), group, ScimGroup.class);
    }

    // Replace a group
    public ScimGroup replaceGroup(String id, ScimGroup group) {
        return exchange(PUT, resourceUrl(config.getGroupsPath(), id), group, ScimGroup.class);
    }

    // Patch a group
    public ScimGroup patchGroup(String id, ScimPatchOp patchOp) {
        return exchange(PATCH, resourceUrl(config.getGroupsPath(), id), patchOp, ScimGroup.class);
    }

    // Delete a group
    public void deleteGroup(String id) {
        exchange(DELETE, resourceUrl(config.getGroupsPath(), id), null, null);
    }

    // Build a list query string
    private String listQuery(int startIndex, int count, String filter) {
        StringBuilder query = new StringBuilder();
        query.append("startIndex=").append(startIndex).append("&count=").append(count);
        if (StringUtils.isNotBlank(filter)) {
            query.append("&filter=").append(encode(filter));
        }
        return query.toString();
    }

    // Build a resource URL
    private String resourceUrl(String endpoint, String id) {
        return url(endpoint + "/" + encode(id), null);
    }

    // Build an absolute URL
    private String url(String path, String query) {
        String baseUrl = StringUtils.removeEnd(config.getBaseUrl(), "/");
        String fullPath = path.startsWith("/") ? path : "/" + path;
        if (StringUtils.isBlank(query)) {
            return baseUrl + fullPath;
        }
        return baseUrl + fullPath + "?" + query;
    }

    // Execute a SCIM request and parse the response
    private <T> T exchange(String method, String url, Object payload, Class<T> responseType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(config.getEffectiveRequestTimeoutSeconds()))
                    .header(ACCEPT, MEDIA_TYPE);
            String authorization = authorizationHeader();
            if (StringUtils.isNotBlank(authorization)) {
                builder.header(AUTHORIZATION, authorization);
            }
            if (payload == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header(CONTENT_TYPE, MEDIA_TYPE)
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw toApiCallException(status, response.body());
            }
            if (responseType == null || StringUtils.isBlank(response.body())) {
                return null;
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (ApiCallException exception) {
            throw exception;
        } catch (IOException exception) {
            log.error("SCIM request [{}] [{}] failed: {}", method, url, exception.getMessage());
            throw new ApiCallException("SCIM request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("SCIM request [{}] [{}] interrupted", method, url);
            throw new ApiCallException("SCIM request interrupted", exception);
        }
    }

    // Convert a non-success response to an API call exception
    private ApiCallException toApiCallException(int status, String body) {
        String detail = body;
        String scimType = null;
        try {
            if (StringUtils.isNotBlank(body)) {
                ScimError error = objectMapper.readValue(body, ScimError.class);
                detail = StringUtils.defaultIfBlank(error.getDetail(), body);
                scimType = error.getScimType();
            }
        } catch (Exception ignored) {
            // The body is not a SCIM error document, keep the raw body
        }
        String message = "SCIM request failed with HTTP " + status;
        if (StringUtils.isNotBlank(scimType)) {
            message += " (" + scimType + ")";
        }
        if (StringUtils.isNotBlank(detail)) {
            message += ": " + detail;
        }
        log.error(message);
        return new ApiCallException(message);
    }

    // Resolve the authorization header from the configuration
    private String authorizationHeader() {
        ScimAuthType authType = config.getAuthType() == null ? ScimAuthType.BEARER
                : config.getAuthType();
        return switch (authType) {
            case NONE -> null;
            case BASIC -> {
                if (StringUtils.isBlank(config.getUsername())) {
                    throw new ApiCallException(
                            "SCIM basic authorization requires a user name");
                }
                String credentials = config.getUsername() + ":"
                        + StringUtils.defaultString(config.getPassword());
                yield "Basic " + Base64.getEncoder().encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8));
            }
            case BEARER -> {
                if (StringUtils.isBlank(config.getBearerToken())) {
                    throw new ApiCallException(
                            "SCIM bearer authorization requires a token");
                }
                yield "Bearer " + config.getBearerToken();
            }
        };
    }

    // Encode a URI path segment
    private String encode(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    // Build the default HTTP client
    private static HttpClient buildHttpClient(ScimConfig config) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getEffectiveConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    // Build the default JSON mapper
    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}