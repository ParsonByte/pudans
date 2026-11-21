package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record RagContext(
        String userId,
        @Nullable String requestId,
        List<KnowledgeBase> knowledgeBases,
        Set<String> roleNames,
        @Nullable String deptId,
        RetrievalOptions options,
        int candidateLimit) {

    public RagContext {
        knowledgeBases = knowledgeBases == null ? List.of() : List.copyOf(knowledgeBases);
        roleNames = roleNames == null ? Set.of() : Set.copyOf(roleNames);
        options = options == null ? new RetrievalOptions(60, 0.30, 10, 4000) : options;
        candidateLimit = Math.max(1, candidateLimit);
    }

    public boolean isEmpty() {
        return knowledgeBases.isEmpty();
    }
}