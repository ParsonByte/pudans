package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiChatCatalog(
        List<AiCatalogItem> answerModes,
        List<AiCatalogItem> reasoningLevels,
        List<AiModelCatalogItem> models,
        List<AiToolDescriptor> tools) {}
