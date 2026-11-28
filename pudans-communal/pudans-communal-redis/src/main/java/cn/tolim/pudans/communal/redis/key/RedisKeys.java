package cn.tolim.pudans.communal.redis.key;

// Central Redis key naming; every key is namespaced by the configured prefix.
public final class RedisKeys {

    public static final String SEPARATOR = ":";

    private final String prefix;

    public RedisKeys(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        if (prefix.contains(SEPARATOR)) {
            throw new IllegalArgumentException("prefix must not contain ':'");
        }
        this.prefix = prefix.trim();
    }

    public String prefix() {
        return prefix;
    }

    public String key(String... parts) {
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("at least one key part is required");
        }
        StringBuilder builder = new StringBuilder(prefix);
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                throw new IllegalArgumentException("key part must not be blank");
            }
            if (part.contains(SEPARATOR)) {
                throw new IllegalArgumentException("key part must not contain ':'");
            }
            builder.append(SEPARATOR).append(part);
        }
        return builder.toString();
    }

    // Global login session bound to a sid.
    public String loginSession(String sid) {
        return key("iam", "session", sid);
    }

    // One-time SSO ticket issued by the login page.
    public String ssoTicket(String appCode, String ticket) {
        return key("iam", "ticket", appCode, ticket);
    }

    // Registry of applications a user is logged into for back-channel logout.
    public String sessionRegistry(String userId) {
        return key("iam", "registry", userId);
    }

    // Application access decision cache.
    public String appAccess(String appCode, String subjectId) {
        return key("iam", "access", appCode, subjectId);
    }

    // Authorization decision cache.
    public String authorizationDecision(String subjectId, String action, String resource) {
        return key("iam", "authz", subjectId, action, resource);
    }

    // Generic cache namespace.
    public String cache(String namespace, String name) {
        return key("cache", namespace, name);
    }

    // Distributed lock key.
    public String lock(String name) {
        return key("lock", name);
    }

    // Rate limit counter.
    public String rateLimit(String scope, String subject) {
        return key("rate", scope, subject);
    }
}