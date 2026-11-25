package cn.tolim.pudans.core.agent.memory;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pudans.memory.reme")
public class ReMeMemoryProperties {

    private boolean enabled;

    private String apiBaseUrl;

    private Duration timeout = Duration.ofSeconds(60);

    private int topK = 5;

    private int maxContextChars = 8_000;

    private String workspacePrefix = "pudans";

    public boolean configured() {
        return enabled
                && StringUtils.hasText(apiBaseUrl)
                && StringUtils.hasText(workspacePrefix)
                && timeout != null
                && !timeout.isZero()
                && !timeout.isNegative();
    }

    public Duration normalizedTimeout() {
        return timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(60)
                : timeout;
    }

    public int normalizedTopK() {
        return Math.max(1, Math.min(topK, 20));
    }

    public int normalizedMaxContextChars() {
        return Math.max(256, maxContextChars);
    }
}
