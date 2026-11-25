package cn.tolim.pudans.contract.ai.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiCreationCatalog(List<AiCreationMediaMode> mediaModes) {

    public record AiCreationMediaMode(
            String id,
            String label,
            List<AiCatalogItem> operations,
            List<AiCatalogItem> parameterOptions,
            List<AiCreationModel> models) {}

    public record AiCreationModel(
            UUID id,
            String logicalName,
            String label,
            List<Map<String, Object>> params,
            Map<String, Object> limits,
            Map<String, Object> parameterSchema) {}
}
