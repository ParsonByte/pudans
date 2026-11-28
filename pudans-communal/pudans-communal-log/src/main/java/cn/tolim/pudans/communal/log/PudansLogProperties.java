package cn.tolim.pudans.communal.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "pudans.log")
public class PudansLogProperties {

    private boolean enabled = true;

    private boolean structured = false;

    private String traceHeader = "X-Request-Id";

    private boolean requestLogging = true;

    private Duration slowRequestThreshold = Duration.ofSeconds(1);

    private String environment;

    private List<String> ignoredPaths = new ArrayList<>(List.of(
            "/actuator/**", "/error", "/favicon.ico", "/health/**"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStructured() {
        return structured;
    }

    public void setStructured(boolean structured) {
        this.structured = structured;
    }

    public String getTraceHeader() {
        return traceHeader;
    }

    public void setTraceHeader(String traceHeader) {
        if (traceHeader == null || traceHeader.isBlank()) {
            throw new IllegalArgumentException("traceHeader must not be blank");
        }
        this.traceHeader = traceHeader;
    }

    public boolean isRequestLogging() {
        return requestLogging;
    }

    public void setRequestLogging(boolean requestLogging) {
        this.requestLogging = requestLogging;
    }

    public Duration getSlowRequestThreshold() {
        return slowRequestThreshold;
    }

    public void setSlowRequestThreshold(Duration slowRequestThreshold) {
        if (slowRequestThreshold == null || slowRequestThreshold.isNegative()) {
            throw new IllegalArgumentException("slowRequestThreshold must not be negative");
        }
        this.slowRequestThreshold = slowRequestThreshold;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public List<String> getIgnoredPaths() {
        return ignoredPaths;
    }

    public void setIgnoredPaths(List<String> ignoredPaths) {
        this.ignoredPaths = ignoredPaths == null ? new ArrayList<>() : new ArrayList<>(ignoredPaths);
    }
}