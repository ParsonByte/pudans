package cn.tolim.pudans.core.ai.rag.connector;

import org.jspecify.annotations.Nullable;

public record ExternalRagTarget(
        String endpoint,
        @Nullable String dataset,
        @Nullable String knowledgeBaseId,
        @Nullable String apiKey,
        @Nullable String retrievePath) {

    // 记录对象时只暴露密钥是否配置，避免明文进入日志
    @Override
    public String toString() {
        return "ExternalRagTarget[endpoint=" + endpoint
                + ", dataset=" + dataset
                + ", knowledgeBaseId=" + knowledgeBaseId
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "未配置" : "已配置")
                + ", retrievePath=" + retrievePath + ']';
    }
}