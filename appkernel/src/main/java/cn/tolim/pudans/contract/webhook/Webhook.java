package cn.tolim.pudans.contract.webhook;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = Webhook.GROUP,
        version = Webhook.VERSION,
        kind = Webhook.KIND,
        plural = "webhooks",
        singular = "webhook")
public class Webhook extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "Webhook";

    public static final String EVENT_ALL = "*";

    public static final Map<String, String> LEGACY_EVENT_ALIASES = Map.of(
            "temprole.applied", "tempRole.applied",
            "temprole.granted", "tempRole.granted",
            "temprole.revoked", "tempRole.revoked",
            "temprole.expired", "tempRole.expired");

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec = new Spec();

    public boolean subscribes(String eventType) {
        var events = spec.getEvents();
        if (events == null || events.isEmpty()) {
            return false;
        }
        if (events.contains(EVENT_ALL) || events.contains(eventType)) {
            return true;
        }
        var legacyType = LEGACY_EVENT_ALIASES.get(eventType);
        return legacyType != null && events.contains(legacyType);
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String url;

        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        private String secret;

        private List<String> events = new ArrayList<>();

        private String description;

        private boolean enabled = true;

        private int maxAttempts = 3;

        private long timeoutMillis = 5000L;

        private Map<String, String> headers = new LinkedHashMap<>();

        private String lastDeliveryStatus;

        private Instant lastDeliveredAt;

        private int failureCount;
    }
}
