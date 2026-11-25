package cn.tolim.pudans.infra.properties;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "pudans.ai")
public class AiProperties {

    @NotNull
    private Path credentialKeyFile = Path.of("keys", "ai-channel.key");

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(20);

    @NotNull
    private Duration reservationTtl = Duration.ofMinutes(15);

    private int defaultTimeoutMs = 30000;
}
