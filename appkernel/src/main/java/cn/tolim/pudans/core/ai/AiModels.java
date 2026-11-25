package cn.tolim.pudans.core.ai;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.dto.AiModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiModelUpdateRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiDefaultModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiPriceRuleRepository;
import java.time.Clock;
import java.time.Instant;
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
public class AiModels {

    private final AiModelRepository models;

    private final AiChannelModelRepository channelModels;

    private final AiDefaultModelRepository defaultModels;

    private final AiPriceRuleRepository priceRules;

    private Clock clock = Clock.systemUTC();

    public Flux<AiModel> list() {
        return models.findAll();
    }

    public Mono<AiModel> get(UUID id) {
        return models
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-model-not-found", "AI 模型不存在。")));
    }

    public Mono<AiModel> create(AiModelCreateRequest request) {
        if (request == null) {
            return Mono.error(requestRequired());
        }
        return Mono.defer(() -> {
            var now = Instant.now(clock);
            var model = new AiModel(
                    UUID.randomUUID(),
                    AiSupport.requiredText(
                            request.logicalName(),
                            "ai-model-logical-name-required",
                            "模型逻辑名称不能为空。"),
                    AiSupport.requiredText(
                            request.displayName(),
                            "ai-model-display-name-required",
                            "模型显示名称不能为空。"),
                    request.description(),
                    capabilities(request.capabilities()),
                    positiveOrNull(request.contextWindow(), "ai-model-context-window-invalid"),
                    positiveOrNull(request.maxOutputTokens(), "ai-model-output-tokens-invalid"),
                    request.parameterSchema() == null ? Map.of() : Map.copyOf(request.parameterSchema()),
                    AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE),
                    now,
                    now);
            return models.insert(model);
        });
    }

    public Mono<AiModel> update(UUID id, AiModelUpdateRequest request) {
        if (request == null) {
            return Mono.error(requestRequired());
        }
        return Mono.defer(() -> get(id).flatMap(existing -> {
            var model = new AiModel(
                    existing.id(),
                    StringUtils.hasText(request.logicalName())
                            ? request.logicalName().trim()
                            : existing.logicalName(),
                    StringUtils.hasText(request.displayName())
                            ? request.displayName().trim()
                            : existing.displayName(),
                    request.description() == null ? existing.description() : request.description(),
                    request.capabilities() == null
                            ? existing.capabilities()
                            : capabilities(request.capabilities()),
                    request.contextWindow() == null
                            ? existing.contextWindow()
                            : positiveOrNull(request.contextWindow(), "ai-model-context-window-invalid"),
                    request.maxOutputTokens() == null
                            ? existing.maxOutputTokens()
                            : positiveOrNull(request.maxOutputTokens(), "ai-model-output-tokens-invalid"),
                    request.parameterSchema() == null
                            ? existing.parameterSchema()
                            : Map.copyOf(request.parameterSchema()),
                    request.status() == null
                            ? existing.status()
                            : AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE),
                    existing.createdAt(),
                    Instant.now(clock));
            return models.update(model);
        }));
    }

    public Mono<Void> delete(UUID id) {
        return get(id)
                .flatMap(model -> Mono.zip(
                                channelModels.existsByModelId(id),
                                defaultModels.existsByModelId(id),
                                priceRules.existsByModelId(id))
                        .flatMap(inUse -> Boolean.TRUE.equals(inUse.getT1())
                                        || Boolean.TRUE.equals(inUse.getT2())
                                        || Boolean.TRUE.equals(inUse.getT3())
                                ? Mono.error(new ApiException(
                                        HttpStatus.CONFLICT,
                                        "ai-model-in-use",
                                        "AI 模型仍被渠道、默认模型或价格规则引用。"))
                                : models.delete(id)))
                .onErrorMap(
                        DataIntegrityViolationException.class,
                        error -> new ApiException(
                                HttpStatus.CONFLICT,
                                "ai-model-in-use",
                                "AI 模型仍被会话、任务或其他配置引用。",
                                error));
    }

    static Map<String, Object> capabilities(Map<String, Object> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-model-capabilities-required",
                    "模型至少需要一个能力。");
        }
        var normalized = new java.util.LinkedHashMap<String, Object>();
        capabilities.forEach((key, value) ->
                normalized.put(AiSupport.normalizeCapability(key), value));
        return Map.copyOf(normalized);
    }

    private static Integer positiveOrNull(Integer value, String code) {
        if (value == null) {
            return null;
        }
        return AiSupport.positive(value, code, "模型窗口参数必须大于零。");
    }

    private static ApiException requestRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "ai-model-body-required", "请求体不能为空。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
