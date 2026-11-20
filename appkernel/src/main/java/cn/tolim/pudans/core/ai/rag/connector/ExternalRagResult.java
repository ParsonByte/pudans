package cn.tolim.pudans.core.ai.rag.connector;

import java.util.List;

public record ExternalRagResult(List<ExternalRagDocument> documents) {

    public ExternalRagResult {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}