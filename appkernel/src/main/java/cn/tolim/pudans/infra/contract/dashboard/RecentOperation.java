package cn.tolim.pudans.infra.contract.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "One recent audit operation rendered on the dashboard.")
public record RecentOperation(
        String auditId,
        String operator,
        String operation,
        String resourceType,
        String resourceName,
        String result,
        Instant timestamp) {}
