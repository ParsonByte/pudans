package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiModelType;

public record AiProviderModel(
        String modelId,
        String displayName,
        AiModelType modelType,
        String source) {

    public AiProviderModel {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("供应商模型标识不能为空");
        }
        modelId = modelId.trim();
        displayName = displayName == null || displayName.isBlank() ? modelId : displayName.trim();
        modelType = modelType == null ? AiModelType.UNKNOWN : modelType;
        source = source == null || source.isBlank() ? "provider" : source.trim();
    }
}
