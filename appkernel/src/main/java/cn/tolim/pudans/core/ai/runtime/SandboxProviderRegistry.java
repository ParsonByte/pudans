package cn.tolim.pudans.core.ai.runtime;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.resource.AiSandboxProvider;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderSecretCipher;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.ai.runtime.sandbox.AliyunSandboxAdapter;
import cn.tolim.pudans.core.ai.runtime.sandbox.E2BSandboxAdapter;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxErrors;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxProviderAdapter;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxProviderConfig;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxRuntimeSettings;
import cn.tolim.pudans.core.ai.runtime.sandbox.TencentSandboxAdapter;
import cn.tolim.pudans.infra.properties.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SandboxProviderRegistry {

    private static final List<String> CAPABILITIES = List.of("start", "stop", "restart", "exec", "files", "logs");

    private final ReactiveExtensionClient client;

    private final AiProviderSecretCipher secretCipher;

    private final AiProperties properties;

    private final ObjectMapper mapper;

    private final WebClient webClient;

    public SandboxProviderRegistry(
            ReactiveExtensionClient client,
            AiProviderSecretCipher secretCipher,
            AiProperties properties,
            ObjectMapper mapper,
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient) {
        this.client = client;
        this.secretCipher = secretCipher;
        this.properties = properties;
        this.mapper = mapper;
        this.webClient = webClient;
    }

    public Mono<SandboxProviderAdapter> adapter(AiSandboxProvider.Provider provider) {
        if (provider == null) {
            return Mono.error(SandboxErrors.providerMissing("未指定"));
        }
        var options = ListOptions.builder()
                .andQuery(equal("spec.provider", provider.name()))
                .andQuery(equal("spec.enabled", true))
                .build();
        return client.listAll(AiSandboxProvider.class, options, defaultSort())
                .filter(this::enabled)
                .next()
                .switchIfEmpty(Mono.error(SandboxErrors.providerMissing(provider.name())))
                .map(this::adapter);
    }

    public Flux<ProviderInfo> providers() {
        var options = ListOptions.builder()
                .andQuery(equal("spec.enabled", true))
                .build();
        return client.listAll(AiSandboxProvider.class, options, defaultSort())
                .filter(this::enabled)
                .map(this::info);
    }

    private SandboxProviderAdapter adapter(AiSandboxProvider resource) {
        var spec = resource.getSpec();
        if (spec == null || spec.getProvider() == null) {
            throw SandboxErrors.configInvalid("沙箱提供方资源缺少 provider。");
        }
        var secret = spec.getAccessKeySecret();
        if (!StringUtils.hasText(secret)) {
            throw SandboxErrors.configInvalid(spec.getProvider() + " 提供方未配置 accessKeySecret。");
        }
        var decrypted = decrypt(secret);
        var config = new SandboxProviderConfig(
                spec.getProvider(),
                spec.getDisplayName(),
                spec.getEndpoint(),
                spec.getRegion(),
                spec.getAccessKeyId(),
                decrypted,
                spec.getDefaultImage(),
                spec.getMaxTimeoutSeconds() == null ? 3600 : spec.getMaxTimeoutSeconds());
        var runtime = properties.getRuntime();
        var settings = new SandboxRuntimeSettings(
                runtime.getLogFile(),
                runtime.getTencentDataPlaneTemplate(),
                runtime.getTencentApiVersion(),
                runtime.getRequestTimeoutSeconds());
        return switch (spec.getProvider()) {
            case ALIYUN -> new AliyunSandboxAdapter(webClient, mapper, config, settings);
            case TENCENT -> new TencentSandboxAdapter(webClient, mapper, config, settings);
            case E2B -> new E2BSandboxAdapter(webClient, mapper, config, settings);
        };
    }

    private ProviderInfo info(AiSandboxProvider resource) {
        var spec = resource.getSpec();
        var provider = spec == null || spec.getProvider() == null ? "" : spec.getProvider().name();
        return new ProviderInfo(
                provider,
                spec == null ? null : spec.getDisplayName(),
                spec == null ? null : spec.getRegion(),
                spec == null ? null : spec.getEndpoint(),
                spec == null ? null : spec.getDefaultImage(),
                spec == null || spec.getMaxTimeoutSeconds() == null ? 3600 : spec.getMaxTimeoutSeconds(),
                CAPABILITIES);
    }

    private boolean enabled(AiSandboxProvider resource) {
        return resource.getSpec() != null && resource.getSpec().isEnabled();
    }

    private String decrypt(String secret) {
        if (!secretCipher.isEncrypted(secret)) {
            return secret;
        }
        try {
            return secretCipher.decrypt(secret);
        } catch (RuntimeException error) {
            throw SandboxErrors.configInvalid("沙箱提供方 accessKeySecret 无法解密。");
        }
    }

    public record ProviderInfo(
            String provider,
            String displayName,
            String region,
            String endpoint,
            String defaultImage,
            int maxTimeoutSeconds,
            List<String> capabilities) {}
}