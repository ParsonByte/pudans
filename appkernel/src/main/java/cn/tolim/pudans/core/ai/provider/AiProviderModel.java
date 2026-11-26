package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiModelType;
import java.util.List;
import java.util.Map;

public record AiProviderModel(
        String modelId,
        String displayName,
        AiModelType modelType,
        String source,
        Integer contextWindow,
        Integer maxOutputTokens,
        List<String> inputModalities,
        List<String> outputModalities,
        List<String> features,
        Map<String, Object> catalog) {

    public AiProviderModel(
            String modelId, String displayName, AiModelType modelType, String source) {
        this(modelId, displayName, modelType, source, null, null, List.of(), List.of(), List.of(), Map.of());
    }

    public AiProviderModel {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("供应商模型标识不能为空");
        }
        modelId = modelId.trim();
        displayName = displayName == null || displayName.isBlank() ? modelId : displayName.trim();
        modelType = modelType == null ? AiModelType.UNKNOWN : modelType;
        source = source == null || source.isBlank() ? "provider" : source.trim();
        contextWindow = contextWindow == null || contextWindow <= 0 ? null : contextWindow;
        maxOutputTokens = maxOutputTokens == null || maxOutputTokens <= 0 ? null : maxOutputTokens;
        inputModalities = inputModalities == null ? List.of() : List.copyOf(inputModalities);
        outputModalities = outputModalities == null ? List.of() : List.copyOf(outputModalities);
        features = features == null ? List.of() : List.copyOf(features);
        catalog = catalog == null ? Map.of() : Map.copyOf(catalog);
    }
}
