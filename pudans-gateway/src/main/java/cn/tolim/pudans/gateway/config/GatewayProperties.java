package cn.tolim.pudans.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "pudans.gateway")
public class GatewayProperties {

    // Default route order for internal service routes, lower values win over the IAM catch-all
    public static final int DEFAULT_SERVICE_ROUTE_ORDER = -40;

    // Shared token the gateway injects when routing to internal services
    private String internalToken;

    private Frontend frontend = new Frontend();
    private Auth auth = new Auth();
    private Map<String, App> apps = new LinkedHashMap<>();
    // Explicit route overrides; discovered routes come from registry metadata
    private Map<String, Service> services = new LinkedHashMap<>();

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public Frontend getFrontend() {
        return frontend;
    }

    public void setFrontend(Frontend frontend) {
        this.frontend = frontend;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Map<String, App> getApps() {
        return apps;
    }

    public void setApps(Map<String, App> apps) {
        this.apps = apps;
    }

    public Map<String, Service> getServices() {
        return services;
    }

    public void setServices(Map<String, Service> services) {
        this.services = services;
    }

    public static class Frontend {

        // Backend URI that serves the homepage
        private String homeUri = "http://localhost:3000";

        // Backend URI that serves the dedicated login page
        private String loginUri = "http://localhost:3000";

        public String getHomeUri() {
            return homeUri;
        }

        public void setHomeUri(String homeUri) {
            this.homeUri = homeUri;
        }

        public String getLoginUri() {
            return loginUri;
        }

        public void setLoginUri(String loginUri) {
            this.loginUri = loginUri;
        }
    }

    public static class Auth {

        // External login page URL used for redirects, absolute or same-origin path
        private String loginPageUrl = "/login";

        // Cookie carrying the global login session id, matches the IAM session cookie
        private String sessionCookieName = "PUDANS_SID";

        // Whether the gateway validates the global login session for protected routes
        private boolean sessionEnabled = true;

        // IAM endpoint that validates a global login session
        private String sessionValidateUrl = "http://localhost:8081/apis/pudans.tolim.cn/v1alpha1/iam/session/validate";

        // Short cache for session validation results in seconds
        private long sessionCacheTtlSeconds = 10;

        // Session validation HTTP timeout in milliseconds
        private long sessionTimeoutMillis = 3000;

        // Secure flag for session cookie responses
        private boolean cookieSecure = false;

        // SameSite flag for session cookie responses
        private String cookieSameSite = "Lax";

        // Page requests redirect to the login page, API requests return 401
        private boolean redirectPageRequests = true;

        // Whether X-Forwarded-* headers are trusted when building external URLs
        private boolean trustForwardedHeaders = true;

        public String getLoginPageUrl() {
            return loginPageUrl;
        }

        public void setLoginPageUrl(String loginPageUrl) {
            this.loginPageUrl = loginPageUrl;
        }

        public String getSessionCookieName() {
            return sessionCookieName;
        }

        public void setSessionCookieName(String sessionCookieName) {
            this.sessionCookieName = sessionCookieName;
        }

        public boolean isSessionEnabled() {
            return sessionEnabled;
        }

        public void setSessionEnabled(boolean sessionEnabled) {
            this.sessionEnabled = sessionEnabled;
        }

        public String getSessionValidateUrl() {
            return sessionValidateUrl;
        }

        public void setSessionValidateUrl(String sessionValidateUrl) {
            this.sessionValidateUrl = sessionValidateUrl;
        }

        public long getSessionCacheTtlSeconds() {
            return sessionCacheTtlSeconds;
        }

        public void setSessionCacheTtlSeconds(long sessionCacheTtlSeconds) {
            this.sessionCacheTtlSeconds = sessionCacheTtlSeconds;
        }

        public long getSessionTimeoutMillis() {
            return sessionTimeoutMillis;
        }

        public void setSessionTimeoutMillis(long sessionTimeoutMillis) {
            this.sessionTimeoutMillis = sessionTimeoutMillis;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookieSameSite() {
            return cookieSameSite;
        }

        public void setCookieSameSite(String cookieSameSite) {
            this.cookieSameSite = cookieSameSite;
        }

        public boolean isRedirectPageRequests() {
            return redirectPageRequests;
        }

        public void setRedirectPageRequests(boolean redirectPageRequests) {
            this.redirectPageRequests = redirectPageRequests;
        }

        public boolean isTrustForwardedHeaders() {
            return trustForwardedHeaders;
        }

        public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
            this.trustForwardedHeaders = trustForwardedHeaders;
        }
    }

    public static class App {

        // Backend URI of the microservice
        private String uri;

        // External path prefix owned by the microservice, defaults to /apps/{appCode}
        private String pathPrefix;

        // API path pattern routed to the microservice, defaults to the namespaced app API
        private String apiPath;

        // Whether the external path prefix is stripped before forwarding
        private boolean stripPrefix = true;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        public String getApiPath() {
            return apiPath;
        }

        public void setApiPath(String apiPath) {
            this.apiPath = apiPath;
        }

        public boolean isStripPrefix() {
            return stripPrefix;
        }

        public void setStripPrefix(boolean stripPrefix) {
            this.stripPrefix = stripPrefix;
        }
    }

    public static class Service {

        // Backend URI of the internal service
        private String uri;

        // Path pattern routed to the internal service, for example /apis/pudans.tolim.cn/v1alpha1/mq/**
        private String path;

        // Route order, lower values are matched before the IAM catch-all route
        private int order = DEFAULT_SERVICE_ROUTE_ORDER;

        // Whether the matched path prefix is stripped before forwarding
        private boolean stripPrefix = false;

        // Whether the service route is registered
        private boolean enabled = true;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public boolean isStripPrefix() {
            return stripPrefix;
        }

        public void setStripPrefix(boolean stripPrefix) {
            this.stripPrefix = stripPrefix;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}