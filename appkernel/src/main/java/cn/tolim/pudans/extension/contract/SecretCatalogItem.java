package cn.tolim.pudans.extension.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SecretCatalogItem(
        @Schema(description = "Secret name.") String name,
        @Schema(description = "Secret type.") String type,
        @Schema(description = "Sorted union of data and stringData keys.") List<String> keys,
        @Schema(description = "Sorted stringData keys.") List<String> stringDataKeys,
        @Schema(description = "Sorted data keys.") List<String> dataKeys,
        @Schema(description = "Whether stringData contains any keys.") boolean hasStringData,
        @Schema(description = "Whether data contains any keys.") boolean hasData,
        @Schema(description = "Secret labels.") Map<String, String> labels,
        @Schema(description = "Secret annotations.") Map<String, String> annotations,
        @Schema(description = "Creation time.") Instant creationTimestamp,
        @Schema(description = "Optimistic version.") long version) {}
