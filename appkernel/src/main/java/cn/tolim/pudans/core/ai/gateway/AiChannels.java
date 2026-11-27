package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.AiChannel;
import cn.tolim.pudans.contract.ai.dto.AiChannelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiChannelTestResult;
import cn.tolim.pudans.contract.ai.dto.AiChannelUpdateRequest;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiChannels {

    private final AiChannelRepository channels;

    private final AiChannelModelRepository channelModels;

    private final AiChannelSecrets secrets;

    private final AiChannelProbe probe;

    private final AiProviders providers;

    private Clock clock = Clock.systemUTC();

    public Flux<AiChannel> list(String workspaceId) {
        return channels.findAllByWorkspace(requireWorkspace(workspaceId)).map(AiChannelEntity::toContract);
    }

    public Flux<AiChannel> listAll() {
        return channels.findAll().map(AiChannelEntity::toContract);
    }

    public Mono<AiChannel> get(String workspaceId, UUID id) {
        return require(workspaceId, id).map(AiChannelEntity::toContract);
    }

    public Mono<AiChannel> get(UUID id) {
        return channels
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-channel-not-found", "AI 渠道不存在。")))
                .map(AiChannelEntity::toContract);
    }

    public Mono<AiChannel> create(String workspaceId, AiChannelCreateRequest request) {
        if (request == null) {
            return Mono.error(requestRequired());
        }
        return Mono.defer(() -> {
            var now = Instant.now(clock);
            var session = new LinkedHashMap<String, Object>();
            var entity = new AiChannelEntity();
            entity.setId(UUID.randomUUID());
            entity.setWorkspaceId(requireWorkspace(workspaceId));
            var providerName = AiSupport.requiredText(
                    request.provider(), "ai-channel-provider-required", "渠道供应商不能为空。");
            var provider = providers.find(providerName);
            entity.setProvider(provider.map(AiProvider::id).orElse(providerName));
            entity.setProtocol(protocol(provider, request.protocol()));
            entity.setBaseUrl(baseUrl(provider, request.baseUrl()));
            entity.setSecretRef(blankToNull(request.secretRef()));
            entity.setSecretKeys(normalizeSecretKeys(request.secretKeys()));
            var encrypted = secrets.mergeEncrypted(Map.of(), request.credentials());
            secrets.validateConflict(entity.getSecretRef(), encrypted);
            entity.setCredentialCiphertext(encrypted);
            entity.setCredentialStatus(credentialStatus(
                    entity.credentialConfigured(), session, null));
            entity.setStatus(AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE));
            entity.setTimeoutMs(positiveOrDefault(request.timeoutMs(), 30000));
            entity.setMaxConcurrency(positiveOrDefault(request.maxConcurrency(), 1));
            entity.setWeight(nonNegativeOrDefault(request.weight(), 100));
            entity.setRateLimit(nonNegativeOrNull(request.rateLimit()));
            entity.setConfig(request.config() == null ? Map.of() : Map.copyOf(request.config()));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return channels.insert(entity).map(AiChannelEntity::toContract);
        });
    }

    public Mono<AiChannel> update(
            String workspaceId, UUID id, AiChannelUpdateRequest request) {
        if (request == null) {
            return Mono.error(requestRequired());
        }
        return Mono.defer(() -> require(workspaceId, id).flatMap(entity -> {
            var providerChanged = StringUtils.hasText(request.provider())
                    && !request.provider().trim().equalsIgnoreCase(entity.getProvider());
            var provider = StringUtils.hasText(request.provider())
                    ? providers.find(request.provider())
                    : providers.find(entity.getProvider());
            if (StringUtils.hasText(request.provider())) {
                entity.setProvider(provider.map(AiProvider::id).orElse(request.provider().trim()));
            }
            if (StringUtils.hasText(request.protocol())) {
                entity.setProtocol(request.protocol().trim());
            } else if (providerChanged) {
                entity.setProtocol(provider.map(item -> item.descriptor().defaultProtocol())
                        .orElse(entity.getProtocol()));
            }
            if (request.baseUrl() != null) {
                entity.setBaseUrl(blankToNull(request.baseUrl()));
            } else if (providerChanged && provider.isPresent()) {
                entity.setBaseUrl(provider.get().descriptor().defaultBaseUrl());
            }
            if (request.secretRef() != null) {
                entity.setSecretRef(blankToNull(request.secretRef()));
            }
            if (request.secretKeys() != null) {
                entity.setSecretKeys(normalizeSecretKeys(request.secretKeys()));
            }
            if (request.credentials() != null) {
                entity.setCredentialCiphertext(secrets.mergeEncrypted(
                        entity.getCredentialCiphertext(), request.credentials()));
            }
            secrets.validateConflict(entity.getSecretRef(), entity.getCredentialCiphertext());
            if (request.status() != null) {
                entity.setStatus(AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE));
            }
            if (request.timeoutMs() != null) {
                entity.setTimeoutMs(positiveOrDefault(request.timeoutMs(), 30000));
            }
            if (request.maxConcurrency() != null) {
                entity.setMaxConcurrency(positiveOrDefault(request.maxConcurrency(), 1));
            }
            if (request.weight() != null) {
                entity.setWeight(nonNegativeOrDefault(request.weight(), 100));
            }
            if (request.rateLimit() != null) {
                entity.setRateLimit(nonNegativeOrNull(request.rateLimit()));
            }
            if (request.config() != null) {
                entity.setConfig(Map.copyOf(request.config()));
            }
            entity.setCredentialStatus(credentialStatus(
                    entity.credentialConfigured(), entity.getCredentialStatus(), null));
            entity.setUpdatedAt(Instant.now(clock));
            return channels.update(entity).map(AiChannelEntity::toContract);
        }));
    }

    public Mono<Void> delete(String workspaceId, UUID id) {
        return require(workspaceId, id)
                .flatMap(entity -> channelModels
                        .existsByChannelId(id)
                        .flatMap(inUse -> Boolean.TRUE.equals(inUse)
                                ? Mono.error(new ApiException(
                                        HttpStatus.CONFLICT,
                                        "ai-channel-in-use",
                                        "AI 渠道仍被模型映射引用，不能删除。"))
                                : channels.delete(id)))
                .onErrorMap(
                        DataIntegrityViolationException.class,
                        error -> new ApiException(
                                HttpStatus.CONFLICT,
                                "ai-channel-in-use",
                                "AI 渠道仍被引用，不能删除。",
                                error));
    }

    public Mono<AiChannelTestResult> test(String workspaceId, UUID id, String model) {
        return require(workspaceId, id)
                .flatMap(entity -> secrets
                        .resolve(entity)
                        .flatMap(credentials -> probe.test(entity, credentials, model))
                        .flatMap(result -> {
                            entity.setCredentialStatus(credentialStatus(
                                    true, entity.getCredentialStatus(), result));
                            entity.setUpdatedAt(Instant.now(clock));
                            return channels.update(entity).thenReturn(result);
                        }));
    }

    public Mono<Map<String, String>> credentials(UUID id) {
        return channels.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-channel-not-found", "AI 渠道不存在。")))
                .flatMap(secrets::resolve);
    }

    public Mono<ResolvedChannel> resolve(String workspaceId, UUID id) {
        return require(workspaceId, id).flatMap(channel -> secrets
                .resolve(channel)
                .map(credentials -> new ResolvedChannel(channel, credentials)));
    }

    private Mono<AiChannelEntity> require(String workspaceId, UUID id) {
        return channels
                .findByWorkspaceAndId(requireWorkspace(workspaceId), id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-channel-not-found", "AI 渠道不存在。")));
    }

    private static String requireWorkspace(String workspaceId) {
        return AiSupport.requiredText(
                workspaceId, "request-workspace-missing", "缺少当前工作区请求头。");
    }

    private static Map<String, String> normalizeSecretKeys(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var normalized = new LinkedHashMap<String, String>();
        values.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-channel-secret-key-invalid",
                        "Secret 键映射不能为空。");
            }
            normalized.put(key.trim(), value.trim());
        });
        return Map.copyOf(normalized);
    }

    private static Map<String, Object> credentialStatus(
            boolean configured,
            Map<String, Object> existing,
            AiChannelTestResult result) {
        var status = new LinkedHashMap<>(existing == null ? Map.of() : existing);
        status.put("configured", configured);
        if (result != null) {
            status.put("testedAt", result.testedAt());
            status.put("testStatus", result.success() ? "success" : "failure");
            if (result.code() != null) {
                status.put("testCode", result.code());
            }
            if (result.protocol() != null) {
                status.put("testProtocol", result.protocol());
            }
            if (result.statusCode() != null) {
                status.put("providerStatusCode", result.statusCode());
            }
            if (result.requestId() != null) {
                status.put("providerRequestId", result.requestId());
            }
        }
        return Map.copyOf(status);
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return AiSupport.positive(
                value == null ? defaultValue : value,
                "ai-channel-limit-invalid",
                "渠道并发与超时配置必须大于零。");
    }

    private static int nonNegativeOrDefault(Integer value, int defaultValue) {
        return AiSupport.nonNegative(
                value == null ? defaultValue : value,
                "ai-channel-weight-invalid",
                "渠道权重不能为负数。");
    }

    private static Integer nonNegativeOrNull(Integer value) {
        if (value == null) {
            return null;
        }
        return AiSupport.nonNegative(
                value, "ai-channel-rate-limit-invalid", "渠道限流不能为负数。");
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String protocol(
            java.util.Optional<AiProvider> provider, String requested) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return provider.map(item -> item.descriptor().defaultProtocol())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-channel-protocol-required",
                        "渠道协议不能为空。"));
    }

    private static String baseUrl(
            java.util.Optional<AiProvider> provider, String requested) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return provider.map(item -> item.descriptor().defaultBaseUrl()).orElse(null);
    }

    private static ApiException requestRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "ai-channel-body-required", "请求体不能为空。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public record ResolvedChannel(
            AiChannelEntity channel, Map<String, String> credentials) {}
}
