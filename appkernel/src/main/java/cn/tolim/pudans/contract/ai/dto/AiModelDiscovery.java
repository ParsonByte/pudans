package cn.tolim.pudans.contract.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiModelDiscovery(
        UUID channelId,
        String workspaceId,
        String provider,
        String providerDisplayName,
        String protocol,
        String baseUrl,
        String catalogProvider,
        Instant catalogFetchedAt,
        Instant discoveredAt,
        List<AiDiscoveredModel> models) {}
