package cn.tolim.pudans.iam.authorization.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;


public final class AuthorizationConstants {

    // Base path of the CAM style authorization APIs
    public static final String AUTHZ_API_PATH = IamConstants.V1_API_PATH + "/authz";

    // Wildcard used by policy actions, resources and scopes
    public static final String WILDCARD = "*";

    // Policy statement effect for allow
    public static final String EFFECT_ALLOW = "Allow";

    // Policy statement effect for deny
    public static final String EFFECT_DENY = "Deny";

    // Scope value that applies to every application and resource
    public static final String SCOPE_ALL = "*";

    // Default role and policy status
    public static final String STATUS_ENABLED = "ENABLED";

    // Default policy document version
    public static final String DEFAULT_POLICY_VERSION = "1.0";

    // Maximum number of role chaining hops when resolving effective permissions
    public static final int MAX_ROLE_CHAIN_DEPTH = 10;

    // Time to live in seconds of a cached authorization decision
    public static final long DECISION_CACHE_TTL_SECONDS = 5L;

    // Maximum length of the audit reason column
    public static final int AUDIT_REASON_MAX_LENGTH = 500;

    private AuthorizationConstants() {
    }
}