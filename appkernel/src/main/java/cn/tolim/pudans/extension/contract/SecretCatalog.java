package cn.tolim.pudans.extension.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record SecretCatalog(
        @Schema(description = "One-based page number.") int page,
        @Schema(description = "Page size.") int size,
        @Schema(description = "Total items after filters.") long total,
        @Schema(description = "Total pages after filters.") long totalPages,
        @Schema(description = "Catalog generation time.") Instant generatedAt,
        @Schema(description = "Summary across all secrets before filters.") SecretCatalogSummary summary,
        @Schema(description = "Catalog items for the requested page.") List<SecretCatalogItem> items) {}
