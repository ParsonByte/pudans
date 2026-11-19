package cn.tolim.pudans.core.ai.rag;

import java.util.List;

public record KnowledgeBaseRetrievalResult(String prompt, List<Citation> citations) {

    public KnowledgeBaseRetrievalResult {
        prompt = prompt == null ? "" : prompt;
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
