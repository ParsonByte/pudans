package cn.tolim.pudans.core.ai.rerank;

import java.util.List;

public record RerankRequest(String query, List<String> documents, int topK) {}
