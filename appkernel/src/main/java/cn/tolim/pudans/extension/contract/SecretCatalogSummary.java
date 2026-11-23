package cn.tolim.pudans.extension.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SecretCatalogSummary(
        @Schema(description = "Total secrets.") long total,
        @Schema(description = "Total key entries.") long entries,
        @Schema(description = "Secrets without keys.") long empty,
        @Schema(description = "Secrets created in the last seven days.") long createdLastSevenDays,
        @Schema(description = "Per-type totals sorted by count and type.") List<SecretTypeCount> types) {}
