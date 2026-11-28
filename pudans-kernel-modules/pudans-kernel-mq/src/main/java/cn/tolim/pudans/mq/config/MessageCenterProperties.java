package cn.tolim.pudans.mq.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pudans.mq")
public class MessageCenterProperties {

    // Enables the periodic dispatch scheduler
    private boolean schedulerEnabled = true;

    // Scheduler fixed delay in milliseconds
    private long dispatchIntervalMs = 2000;

    // Scheduler initial delay in milliseconds
    private long dispatchInitialDelayMs = 3000;

    // Maximum messages claimed per dispatch tick
    private int batchSize = 50;

    // RocketMQ topic carrying persisted message announcements
    private String announcementTopic = "pudans-message-announcement";

    // Consumer group this application uses for announcements
    private String announcementConsumerGroup = "pudans-mq-announcement";

    // Default retry budget when the request does not specify one
    private int defaultMaxAttempts = 5;

    // First retry delay in milliseconds
    private long retryInitialDelayMs = 1000;

    // Retry delay ceiling in milliseconds
    private long retryMaxDelayMs = 60000;

    // Exponential backoff multiplier
    private double retryMultiplier = 2.0;

    // DISPATCHING messages older than this are considered stuck
    private long dispatchTimeoutMs = 60000;

    // Producer name used when the request does not specify one
    private String defaultProducer = "system";

    // Maximum payload size in bytes
    private int maxPayloadBytes = 1048576;

    // Maximum number of headers accepted per message
    private int maxHeaderCount = 50;
}