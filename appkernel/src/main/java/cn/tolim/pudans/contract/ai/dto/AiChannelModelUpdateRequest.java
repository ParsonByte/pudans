package cn.tolim.pudans.contract.ai.dto;

import java.util.Map;
import java.util.UUID;

public record AiChannelModelUpdateRequest(
        UUID channelId,
        UUID modelId,
        String upstreamModelKey,
        Integer priority,
        Integer weight,
        Boolean enabled,
        Map<String, Object> parameterOverrides,
        Map<String, Object> providerMetadata) {}
