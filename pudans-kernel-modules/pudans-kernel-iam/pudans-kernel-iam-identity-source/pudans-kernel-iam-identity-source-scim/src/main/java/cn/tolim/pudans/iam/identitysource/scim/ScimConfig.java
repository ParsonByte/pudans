package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.communal.jackjson.encrypt.JsonPropertyEncrypt;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;

import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_GROUP_ENDPOINT;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_PAGE_SIZE;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_REQUEST_TIMEOUT_SECONDS;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_ROOT_DEPT_ID;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_ROOT_DEPT_NAME;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_SERVICE_PROVIDER_CONFIG_ENDPOINT;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.DEFAULT_USER_ENDPOINT;


@Getter
@Setter
public class ScimConfig extends IdentitySourceConfig {

    @Serial
    private static final long serialVersionUID = 3465520930934517660L;

    // SCIM base URL, for example https://scim.example.com/scim/v2
    @NotEmpty(message = "SCIM base URL cannot be empty")
    private String baseUrl;

    // Authorization type used for SCIM requests
    private ScimAuthType authType = ScimAuthType.BEARER;

    // Bearer token
    @JsonPropertyEncrypt
    private String bearerToken;

    // Basic authorization user name
    private String username;

    // Basic authorization password
    @JsonPropertyEncrypt
    private String password;

    // User resource endpoint
    private String userEndpoint = DEFAULT_USER_ENDPOINT;

    // Group resource endpoint
    private String groupEndpoint = DEFAULT_GROUP_ENDPOINT;

    // Service provider configuration endpoint
    private String serviceProviderConfigEndpoint = DEFAULT_SERVICE_PROVIDER_CONFIG_ENDPOINT;

    // Page size used for list requests
    @Min(value = 1, message = "SCIM page size must be greater than zero")
    @Max(value = 1000, message = "SCIM page size cannot exceed 1000")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    // Connection timeout in seconds
    @Min(value = 1, message = "SCIM connect timeout must be greater than zero")
    private Integer connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;

    // Request timeout in seconds
    @Min(value = 1, message = "SCIM request timeout must be greater than zero")
    private Integer requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;

    // Whether SCIM groups are synchronized as departments
    private Boolean syncGroupsAsDepts = Boolean.TRUE;

    // Synthetic root department identifier
    private String rootDeptId = DEFAULT_ROOT_DEPT_ID;

    // Synthetic root department name
    private String rootDeptName = DEFAULT_ROOT_DEPT_NAME;

    // Optional fixed area code applied to mapped phone numbers
    private String phoneAreaCode;

    // Whether every listed user is fetched as a full resource to keep representations stable
    private Boolean detailOnList = Boolean.TRUE;

    public ScimConfig() {
    }

    // Effective user endpoint with a leading slash
    public String getUsersPath() {
        return normalizePath(userEndpoint, DEFAULT_USER_ENDPOINT);
    }

    // Effective group endpoint with a leading slash
    public String getGroupsPath() {
        return normalizePath(groupEndpoint, DEFAULT_GROUP_ENDPOINT);
    }

    // Effective service provider configuration endpoint with a leading slash
    public String getServiceProviderConfigPath() {
        return normalizePath(serviceProviderConfigEndpoint,
                DEFAULT_SERVICE_PROVIDER_CONFIG_ENDPOINT);
    }

    // Effective page size
    public int getEffectivePageSize() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }

    // Effective connection timeout in seconds
    public int getEffectiveConnectTimeoutSeconds() {
        return connectTimeoutSeconds == null ? DEFAULT_CONNECT_TIMEOUT_SECONDS
                : connectTimeoutSeconds;
    }

    // Effective request timeout in seconds
    public int getEffectiveRequestTimeoutSeconds() {
        return requestTimeoutSeconds == null ? DEFAULT_REQUEST_TIMEOUT_SECONDS
                : requestTimeoutSeconds;
    }

    // Effective root department identifier
    public String getEffectiveRootDeptId() {
        return StringUtils.defaultIfBlank(rootDeptId, DEFAULT_ROOT_DEPT_ID);
    }

    // Effective root department name
    public String getEffectiveRootDeptName() {
        return StringUtils.defaultIfBlank(rootDeptName, DEFAULT_ROOT_DEPT_NAME);
    }

    // Whether groups are mapped to departments
    public boolean isGroupSyncEnabled() {
        return !Boolean.FALSE.equals(syncGroupsAsDepts);
    }

    // Whether listed users are fetched individually
    public boolean isDetailOnListEnabled() {
        return !Boolean.FALSE.equals(detailOnList);
    }

    // Normalize an endpoint path
    private String normalizePath(String path, String fallback) {
        String value = StringUtils.defaultIfBlank(path, fallback);
        return value.startsWith("/") ? value : "/" + value;
    }
}