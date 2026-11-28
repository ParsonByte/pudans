package cn.tolim.pudans.communal.feign.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pudans.feign")
public class PudansFeignProperties {

    private boolean enabled = true;

    private boolean propagateRequestId = true;

    private boolean propagateTraceparent = true;

    private String serviceName;

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(10);

    private boolean followRedirects = false;

    // Only register global timeouts when the application explicitly opts in.
    private boolean overrideTimeouts = false;

    private boolean includeResponseBody = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPropagateRequestId() {
        return propagateRequestId;
    }

    public void setPropagateRequestId(boolean propagateRequestId) {
        this.propagateRequestId = propagateRequestId;
    }

    public boolean isPropagateTraceparent() {
        return propagateTraceparent;
    }

    public void setPropagateTraceparent(boolean propagateTraceparent) {
        this.propagateTraceparent = propagateTraceparent;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        this.readTimeout = readTimeout;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public boolean isOverrideTimeouts() {
        return overrideTimeouts;
    }

    public void setOverrideTimeouts(boolean overrideTimeouts) {
        this.overrideTimeouts = overrideTimeouts;
    }

    public boolean isIncludeResponseBody() {
        return includeResponseBody;
    }

    public void setIncludeResponseBody(boolean includeResponseBody) {
        this.includeResponseBody = includeResponseBody;
    }
}