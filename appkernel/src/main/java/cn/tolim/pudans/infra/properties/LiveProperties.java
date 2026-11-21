package cn.tolim.pudans.infra.properties;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "pudans.live")
public class LiveProperties {

    private Resolve resolve = new Resolve();

    private Monitor monitor = new Monitor();

    private Recorder recorder = new Recorder();

    private Retention retention = new Retention();

    private Alert alert = new Alert();

    @Data
    public static class Resolve {

        private Duration connectTimeout = Duration.ofSeconds(5);

        private Duration responseTimeout = Duration.ofSeconds(10);

        private DataSize maxHtmlBytes = DataSize.ofMegabytes(8);

        private Duration cacheTtl = Duration.ofSeconds(45);

        private Duration offlineCacheTtl = Duration.ofSeconds(20);

        private Duration lockTtl = Duration.ofSeconds(15);

        private Duration lockWait = Duration.ofSeconds(2);

        private Duration cooldown = Duration.ofMinutes(10);

        private Duration maxCooldown = Duration.ofHours(1);

        private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36 Edg/109.0.1518.49";

        private String cookie = "";
    }

    @Data
    public static class Monitor {

        private Duration tick = Duration.ofSeconds(5);

        private Duration defaultInterval = Duration.ofSeconds(60);

        private Duration liveInterval = Duration.ofSeconds(30);

        private int maxPerTick = 20;

        private int resolveConcurrency = 4;

        private Duration failureBackoff = Duration.ofMinutes(1);

        private Duration maxFailureBackoff = Duration.ofMinutes(15);

        private int errorThreshold = 3;

        private Duration leaderTtl = Duration.ofSeconds(15);
    }

    @Data
    public static class Recorder {

        private int maxConcurrent = 2;

        private int queueCapacity = 10;

        private String workDir;

        private int segmentSeconds = 30;

        private Merge merge = new Merge();

        private Duration maxDuration = Duration.ofHours(6);

        private Duration readTimeout = Duration.ofSeconds(15);

        private Duration streamIdleTimeout = Duration.ofSeconds(30);

        private int maxRetries = 5;

        private Duration retryBackoff = Duration.ofSeconds(1);

        private Duration maxRetryBackoff = Duration.ofSeconds(30);

        private double retryJitter = 0.2;

        private int uploadRetries = 3;

        private Duration finalizeTimeout = Duration.ofMinutes(5);

        private DataSize minFreeDiskBytes = DataSize.ofGigabytes(8);

        private Duration stopTimeout = Duration.ofSeconds(30);

        private Duration recoveryLockTtl = Duration.ofMinutes(10);
    }

    @Data
    public static class Merge {

        private DataSize maxLocalBytes = DataSize.ofGigabytes(6);

        private int maxLocalSegments = 360;

        private int maxPendingParts = 2;
    }

    @Data
    public static class Retention {

        private int days = 7;

        private Duration interval = Duration.ofHours(1);

        private int batchSize = 100;
    }

    @Data
    public static class Alert {

        private Duration dedupe = Duration.ofMinutes(10);

        private int rateLimitThreshold = 3;
    }

    // 分片时长仅允许 10 或 30 秒，便于合并与回放。
    @AssertTrue(message = "录制分片时长只允许 10 或 30 秒")
    public boolean isSegmentSecondsValid() {
        return recorder.getSegmentSeconds() == 10 || recorder.getSegmentSeconds() == 30;
    }

    // 保留足够空闲磁盘，避免录制中断。
    @AssertTrue(message = "最小空闲磁盘必须大于等于 1GiB")
    public boolean isMinFreeDiskValid() {
        return recorder.getMinFreeDiskBytes().toBytes() >= DataSize.ofGigabytes(1).toBytes();
    }
}
