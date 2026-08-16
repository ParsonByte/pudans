package cn.tolim.pudans.contract.audit;

import java.util.Map;

public record PluginAuditRecord(
        String pluginId,
        String operation,
        String resourceType,
        String resourceName,
        String result,
        String username,
        String traceFlowId,
        long durationMs,
        Map<String, Object> context) {}
