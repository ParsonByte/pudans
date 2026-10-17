package cn.tolim.pudans.core.webhook;

import java.time.Instant;
import java.util.Map;

public record WebhookEvent(String id, String type, Instant occurredAt, String traceFlowId, Map<String, Object> data) {}
