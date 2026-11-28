package cn.tolim.pudans.iam.identitysource.scim;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScimConstants {

    public static final String LOGGER_NAME = "scim-identity";

    // SCIM 2.0 schema identifiers
    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
    public static final String LIST_RESPONSE_SCHEMA =
            "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    public static final String PATCH_OP_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
    public static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";
    public static final String SERVICE_PROVIDER_CONFIG_SCHEMA =
            "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";

    // SCIM media type
    public static final String MEDIA_TYPE = "application/scim+json";

    // Default endpoints
    public static final String DEFAULT_USER_ENDPOINT = "/Users";
    public static final String DEFAULT_GROUP_ENDPOINT = "/Groups";
    public static final String DEFAULT_SERVICE_PROVIDER_CONFIG_ENDPOINT = "/ServiceProviderConfig";

    // Default connection and pagination values
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 30;

    // Synthetic root department used to pull all users
    public static final String DEFAULT_ROOT_DEPT_ID = "scim-root";
    public static final String DEFAULT_ROOT_DEPT_NAME = "SCIM";

    // Event callback result
    public static final String EVENT_STATUS = "status";
    public static final String EVENT_STATUS_ACCEPTED = "accepted";
    public static final String EVENT_RESOURCE_TYPE = "resourceType";
    public static final String EVENT_RESOURCE_ID = "id";
    public static final String EVENT_USER_NAME = "userName";
    public static final String EVENT_GROUP_NAME = "displayName";
    public static final String RESOURCE_TYPE_USER = "User";
    public static final String RESOURCE_TYPE_GROUP = "Group";

    // Member type values
    public static final String MEMBER_TYPE_USER = "User";
    public static final String MEMBER_TYPE_GROUP = "Group";
}