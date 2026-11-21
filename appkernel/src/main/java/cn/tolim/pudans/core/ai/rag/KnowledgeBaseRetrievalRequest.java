package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record KnowledgeBaseRetrievalRequest(
        String userId,
        @Nullable String requestId,
        String query,
        List<KnowledgeBase> knowledgeBases,
        @Nullable Set<String> roleNames,
        @Nullable String deptId,
        @Nullable float[] queryEmbedding,
        RetrievalOptions options,
        int candidateLimit) {

    public KnowledgeBaseRetrievalRequest {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户标识不能为空。");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空。");
        }
        if (knowledgeBases == null) {
            throw new IllegalArgumentException("知识库列表不能为空。");
        }
        knowledgeBases = List.copyOf(knowledgeBases);
        roleNames = roleNames == null ? Set.of() : Set.copyOf(roleNames);
        if (options == null) {
            throw new IllegalArgumentException("检索参数不能为空。");
        }
        if (candidateLimit < 1) {
            throw new IllegalArgumentException("候选数量必须大于 0。");
        }
    }
}
