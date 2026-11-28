package cn.tolim.pudans.notification.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pudans.notification")
public class NotificationProperties {

    // Default maximum delivery attempts including the first try.
    private int defaultMaxAttempts = 3;

    private final Retry retry = new Retry();

    private final Mail mail = new Mail();

    private final Sms sms = new Sms();

    private final Crypto crypto = new Crypto();

    @Getter
    @Setter
    public static class Retry {

        private boolean schedulerEnabled = true;

        private long schedulerFixedDelayMs = 30_000L;

        private int batchSize = 50;

        private int initialDelaySeconds = 30;

        private int maxDelaySeconds = 3_600;

        private double backoffMultiplier = 2.0D;
    }

    @Getter
    @Setter
    public static class Mail {

        // Global mail channel toggle; provider credentials are managed through the provider API.
        private boolean enabled = false;

        // Default sender address advertised by the mail channel when no provider overrides it.
        private String from = "no-reply@pudans.local";
    }

    @Getter
    @Setter
    public static class Sms {

        // Global SMS channel toggle; provider credentials are managed through the provider API.
        private boolean enabled = false;

        // Optional provider code preferred when a send request does not select one.
        private String defaultProvider;
    }

    @Getter
    @Setter
    public static class Crypto {

        // spissatus4j key provider: memory, file, environment or keystore.
        private String keyProvider = "memory";

        private String keyDirectory;

        private String masterPassword;

        private String environmentPrefix = "SPISSATUS_KEY_";

        private String keystorePath;

        private String keystorePassword;

        // Caller tenant used for notification key operations.
        private String tenantId = "pudans-notification";
    }
}