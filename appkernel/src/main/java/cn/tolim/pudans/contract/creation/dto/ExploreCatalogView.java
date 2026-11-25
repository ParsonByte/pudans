package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.ai.dto.AiExploreCatalog;
import java.time.Instant;

public record ExploreCatalogView(AiExploreCatalog catalog, String emptyState, Instant generatedAt) {}
