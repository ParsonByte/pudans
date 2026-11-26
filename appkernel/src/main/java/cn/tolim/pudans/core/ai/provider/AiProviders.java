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

    private final AiProviderHttp http;

    public AiProviders(List<AiProvider> providers) {
        this(providers, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiProviders(List<AiProvider> providers, AiProviderHttp http) {
        var indexed = new LinkedHashMap<String, AiProvider>();
        for (var provider : providers) {
            indexed.put(provider.id(), provider);
        }
        this.aliases = Map.copyOf(indexed);
        this.http = http;
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
        var normalized = provider.trim().toLowerCase(Locale.ROOT);
        var configured = aliases.get(normalized);
        if (configured != null) {
            return Optional.of(configured);
        }
        return http == null
                ? Optional.empty()
                : Optional.of(new ConfiguredAiProvider(provider.trim(), http));
    }

    public Optional<AiProvider> builtIn(String provider) {
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

}
