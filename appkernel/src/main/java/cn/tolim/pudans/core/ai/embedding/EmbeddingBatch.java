package cn.tolim.pudans.core.ai.embedding;

import java.util.List;

public record EmbeddingBatch(String modelCode, int dimension, List<float[]> vectors) {}
