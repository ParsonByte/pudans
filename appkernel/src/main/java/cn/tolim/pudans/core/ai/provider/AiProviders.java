package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiProviders {

    private final Map<String, AiProvider> aliases;

    public AiProviders(List<AiProvider> providers) {
        var indexed = new LinkedHashMap<String, AiProvider>();
        for (var provider : providers) {
            for (var alias : aliasesFor(provider.id())) {
                indexed.put(alias, provider);
            }
        }
        this.aliases = Map.copyOf(indexed);
    }

    public List<AiProviderDescriptor> list() {
        return aliases.values().stream()
                .map(AiProvider::descriptor)
                .distinct()
                .sorted(Comparator.comparing(AiProviderDescriptor::provider))
                .toList();
    }

    public Optional<AiProvider> find(String provider) {
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliases.get(provider.trim().toLowerCase(Locale.ROOT)));
    }

    public AiProvider require(String provider) {
        return find(provider)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-provider-unsupported",
                        "不支持的 AI 供应商：" + provider + "。"));
    }

    private static List<String> aliasesFor(String provider) {
        return switch (provider) {
            case "deepseek" -> List.of("deepseek");
            case "minimax" -> List.of("minimax");
            case "dashscope" -> List.of("dashscope", "qwen", "alibaba", "aliyun", "bailian");
            default -> List.of(provider);
        };
    }
}
