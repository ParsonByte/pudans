package cn.tolim.pudans.iam.core.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = IamAuthProperties.PREFIX)
public class IamAuthProperties {

    public static final String PREFIX = "pudans.iam.auth";

    // Global login session settings
    private final Session session = new Session();

    // Session cookie settings
    private final Cookie cookie = new Cookie();

    // SSO ticket settings
    private final Ticket ticket = new Ticket();

    // Back-channel logout settings
    private final Backchannel backchannel = new Backchannel();

    // CORS settings for the login page and homepage
    private final Cors cors = new Cors();

    // Multi-factor authentication settings
    private final Mfa mfa = new Mfa();

    public Session getSession() {
        return session;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Backchannel getBackchannel() {
        return backchannel;
    }

    public Cors getCors() {
        return cors;
    }

    public Mfa getMfa() {
        return mfa;
    }

    public static class Session {
        // Global login session lifetime
        private Duration ttl = Duration.ofHours(8);

        // Application session cache lifetime, never longer than the global session ttl
        private Duration appSessionTtl = Duration.ofHours(8);

        // Whether expired sessions are deleted in the background
        private Boolean cleanupEnabled = Boolean.TRUE;

        // Cleanup cron expression
        private String cleanupCron = "0 */10 * * * *";

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getAppSessionTtl() {
            return appSessionTtl;
        }

        public void setAppSessionTtl(Duration appSessionTtl) {
            this.appSessionTtl = appSessionTtl;
        }

        public Boolean getCleanupEnabled() {
            return cleanupEnabled;
        }

        public void setCleanupEnabled(Boolean cleanupEnabled) {
            this.cleanupEnabled = cleanupEnabled;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }
    }

    public static class Cookie {
        // Global login session cookie name
        private String name = "PUDANS_SID";

        // HttpOnly is always on, Secure is configurable for local development
        private Boolean secure = Boolean.FALSE;

        // SameSite policy
        private String sameSite = "Lax";

        // Cookie path
        private String path = "/";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Boolean getSecure() {
            return secure;
        }

        public void setSecure(Boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Ticket {
        // SSO ticket lifetime, short lived and one-time
        private Duration ttl = Duration.ofSeconds(60);

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class Backchannel {
        // Application code to back-channel logout endpoint mapping
        private Map<String, String> appUrls = new LinkedHashMap<>();

        // HTTP connect timeout
        private Duration connectTimeout = Duration.ofSeconds(3);

        // HTTP read timeout
        private Duration readTimeout = Duration.ofSeconds(5);

        public Map<String, String> getAppUrls() {
            return appUrls;
        }

        public void setAppUrls(Map<String, String> appUrls) {
            this.appUrls = appUrls;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Mfa {
        // Lifetime of the one-time challenge issued after password authentication
        private Duration challengeTtl = Duration.ofMinutes(5);

        public Duration getChallengeTtl() {
            return challengeTtl;
        }

        public void setChallengeTtl(Duration challengeTtl) {
            this.challengeTtl = challengeTtl;
        }
    }

    public static class Cors {
        // Allowed origins, for example the homepage and login page origins
        private List<String> allowedOrigins = new ArrayList<>();

        // Allowed origin patterns
        private List<String> allowedOriginPatterns = new ArrayList<>();

        // Allowed methods
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allowed headers
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        // Whether credentials are allowed
        private Boolean allowCredentials = Boolean.TRUE;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public Boolean getAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(Boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }
}