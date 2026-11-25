package cn.tolim.pudans.contract.ai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiChannelModel(
        UUID id,
        UUID channelId,
        UUID modelId,
        String upstreamModelKey,
        Integer priority,
        Integer weight,
        boolean enabled,
        Map<String, Object> parameterOverrides,
        Map<String, Object> providerMetadata,
        Instant createdAt,
        Instant updatedAt) {}
