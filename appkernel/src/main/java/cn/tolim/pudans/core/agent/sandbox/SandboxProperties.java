package cn.tolim.pudans.core.agent.sandbox;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pudans.sandbox")
public class SandboxProperties {

    private boolean enabled;

    private String provider = "agentrun";

    private String apiKey;

    private String accountId;

    private String region;

    private String dataPlaneBaseUrl;

    private String template;

    private String mcpServerUrl;

    private String workspaceRoot = "/home/pudans/workspace";

    private Duration defaultTtl = Duration.ofMinutes(30);

    private int maxTtlSeconds = 24 * 60 * 60;

    private Duration leaseTtl = Duration.ofMinutes(10);

    private int defaultExecTimeoutSeconds = 120;

    private int maxOutputBytes = 64 * 1024;

    private int maxFileBytes = 512 * 1024;

    private int connectTimeoutSeconds = 30;

    private int readTimeoutSeconds = 120;

    private int maxRetries = 3;

    private Duration snapshotTtl = Duration.ofDays(7);

    private E2b e2b = new E2b();

    // 只有完整凭据和模板配置时才启用真实供应商。
    public boolean configured(String requestedProvider) {
        if (!enabled
                || !StringUtils.hasText(requestedProvider)
                || !requestedProvider.equalsIgnoreCase(provider)) {
            return false;
        }
        if (isE2b(requestedProvider)) {
            return e2b != null && e2b.configured();
        }
        if (!StringUtils.hasText(apiKey)
                || !StringUtils.hasText(template)
                || !StringUtils.hasText(mcpServerUrl)
                || !StringUtils.hasText(workspaceRoot)
                || !StringUtils.hasText(provider)) {
            return false;
        }
        return StringUtils.hasText(dataPlaneBaseUrl)
                || (StringUtils.hasText(accountId) && StringUtils.hasText(region));
    }

    public String defaultTemplate(String requestedProvider) {
        return isE2b(requestedProvider) ? e2b.getTemplate() : template;
    }

    public String defaultRegion(String requestedProvider) {
        return isE2b(requestedProvider) ? null : region;
    }

    public String defaultWorkspaceRoot(String requestedProvider) {
        return isE2b(requestedProvider) ? e2b.getWorkspaceRoot() : workspaceRoot;
    }

    public static boolean isE2b(String requestedProvider) {
        return "e2b".equalsIgnoreCase(requestedProvider);
    }

    // 空闲超时取默认生命周期且限制在供应商可接受的正整数范围。
    public int sandboxIdleTimeoutSeconds() {
        var seconds = defaultTtl == null ? 1_800 : defaultTtl.toSeconds();
        return (int) Math.max(1, Math.min(seconds, Integer.MAX_VALUE));
    }

    public int normalizedDefaultExecTimeoutSeconds() {
        return Math.max(1, Math.min(defaultExecTimeoutSeconds, 300));
    }

    public int normalizedMaxOutputBytes() {
        return Math.max(1_024, maxOutputBytes);
    }

    public int normalizedMaxFileBytes() {
        return Math.max(1_024, maxFileBytes);
    }

    public int normalizedConnectTimeoutSeconds() {
        return Math.max(1, connectTimeoutSeconds);
    }

    public int normalizedReadTimeoutSeconds() {
        return Math.max(1, readTimeoutSeconds);
    }

    public int normalizedMaxRetries() {
        return Math.max(0, maxRetries);
    }

    public Duration normalizedLeaseTtl() {
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            return Duration.ofMinutes(10);
        }
        return leaseTtl;
    }

    public Duration normalizedSnapshotTtl() {
        if (snapshotTtl == null || snapshotTtl.isZero() || snapshotTtl.isNegative()) {
            return Duration.ofDays(7);
        }
        return snapshotTtl;
    }

    public int normalizedMaxTtlSeconds() {
        return Math.max(60, maxTtlSeconds);
    }

    @Getter
    @Setter
    public static class E2b {

        private String apiKey;

        private String apiBaseUrl = "https://api.e2b.app";

        private String domain = "e2b.app";

        private String template = "base";

        private String workspaceRoot = "/home/user";

        private String runUser = "user";

        private String persistenceMode = "TAR";

        public boolean configured() {
            return StringUtils.hasText(apiKey)
                    && StringUtils.hasText(apiBaseUrl)
                    && StringUtils.hasText(domain)
                    && StringUtils.hasText(template)
                    && StringUtils.hasText(workspaceRoot)
                    && workspaceRoot.startsWith("/");
        }
    }
}
