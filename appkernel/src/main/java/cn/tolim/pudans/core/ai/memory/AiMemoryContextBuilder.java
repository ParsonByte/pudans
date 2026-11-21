package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.asset.MemoryAssetBindingResolver;
import cn.tolim.pudans.core.ai.prompt.MemoryPrompts;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiMemoryContextBuilder {

    private final MemoryRecall memoryRecall;

    private final AiSettings aiSettings;

    private final MemoryAssetBindingResolver bindingResolver;

    public Mono<MemoryInjection> build(MemoryContext context, String query, int l1Limit, int l2Limit) {
        Objects.requireNonNull(context, "context");
        if (query == null || query.isBlank()) {
            return Mono.just(MemoryInjection.empty());
        }
        return aiSettings.memoryEnabled().flatMap(enabled -> {
            if (!Boolean.TRUE.equals(enabled)) {
                return Mono.just(MemoryInjection.empty());
            }
            return bindingResolver
                    .borrowedScopes(context)
                    .flatMap(scopes -> memoryRecall.build(context, query, l1Limit, l2Limit, null, scopes))
                    .defaultIfEmpty(MemoryInjection.empty());
        });
    }

    public String systemMessage(MemoryInjection injection) {
        Objects.requireNonNull(injection, "injection");
        if (injection.isEmpty()) {
            return "";
        }
        var builder = new StringBuilder(MemoryPrompts.CONTEXT_HEADER);
        if (!injection.prependContext().isBlank()) {
            builder.append(MemoryPrompts.INJECT_PREPEND).append(injection.prependContext());
        }
        if (!injection.appendSystemContext().isBlank()) {
            builder.append(MemoryPrompts.INJECT_APPEND).append(injection.appendSystemContext());
        }
        return builder.toString();
    }
}
