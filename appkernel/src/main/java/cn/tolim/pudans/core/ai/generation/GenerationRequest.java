package cn.tolim.pudans.core.ai.generation;

import org.jspecify.annotations.Nullable;

public record GenerationRequest(
        @Nullable String routeGroupCode,
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        double temperature) {}
