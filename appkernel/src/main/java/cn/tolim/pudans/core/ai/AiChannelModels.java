package cn.tolim.pudans.core.ai;

import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.dto.AiChannelModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiChannelModelUpdateRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiChannelModels {

    private final AiChannelModelRepository channelModels;

    private final AiChannelRepository channels;

    private final AiModelRepository models;

    private Clock clock = Clock.systemUTC();

    public Flux<AiChannelModel> list() {
        return channelModels.findAll();
    }

    public Mono<AiChannelModel> get(UUID id) {
        return channelModels
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-channel-model-not-found", "渠道路由不存在。")));
    }

    public Mono<AiChannelModel> create(AiChannelModelCreateRequest request) {
        if (request == null || request.channelId() == null || request.modelId() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-channel-model-required",
                    "渠道和模型不能为空。"));
        }
        var upstream = AiSupport.requiredText(
                request.upstreamModelKey(),
                "ai-channel-model-upstream-required",
                "上游模型键不能为空。");
        return validateReferences(request.channelId(), request.modelId())
                .then(Mono.defer(() -> {
                    var now = Instant.now(clock);
                    var model = new AiChannelModel(
                            UUID.randomUUID(),
                            request.channelId(),
                            request.modelId(),
                            upstream,
                            AiSupport.nonNegative(
                                    request.priority() == null ? 100 : request.priority(),
                                    "ai-channel-model-priority-invalid",
                                    "路由优先级不能为负数。"),
                            AiSupport.nonNegative(
                                    request.weight() == null ? 100 : request.weight(),
                                    "ai-channel-model-weight-invalid",
                                    "路由权重不能为负数。"),
                            request.enabled() == null || request.enabled(),
                            request.parameterOverrides() == null
                                    ? Map.of()
                                    : Map.copyOf(request.parameterOverrides()),
                            request.providerMetadata() == null
                                    ? Map.of()
                                    : Map.copyOf(request.providerMetadata()),
                            now,
                            now);
                    return channelModels.insert(model);
                }));
    }

    public Mono<AiChannelModel> update(UUID id, AiChannelModelUpdateRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "ai-channel-model-body-required", "请求体不能为空。"));
        }
        return get(id).flatMap(existing -> {
            var channelId = request.channelId() == null ? existing.channelId() : request.channelId();
            var modelId = request.modelId() == null ? existing.modelId() : request.modelId();
            var upstream = StringUtils.hasText(request.upstreamModelKey())
                    ? request.upstreamModelKey().trim()
                    : existing.upstreamModelKey();
            return validateReferences(channelId, modelId).then(Mono.defer(() -> {
                var updated = new AiChannelModel(
                        existing.id(),
                        channelId,
                        modelId,
                        upstream,
                        request.priority() == null
                                ? existing.priority()
                                : AiSupport.nonNegative(
                                        request.priority(),
                                        "ai-channel-model-priority-invalid",
                                        "路由优先级不能为负数。"),
                        request.weight() == null
                                ? existing.weight()
                                : AiSupport.nonNegative(
                                        request.weight(),
                                        "ai-channel-model-weight-invalid",
                                        "路由权重不能为负数。"),
                        request.enabled() == null ? existing.enabled() : request.enabled(),
                        request.parameterOverrides() == null
                                ? existing.parameterOverrides()
                                : Map.copyOf(request.parameterOverrides()),
                        request.providerMetadata() == null
                                ? existing.providerMetadata()
                                : Map.copyOf(request.providerMetadata()),
                        existing.createdAt(),
                        Instant.now(clock));
                return channelModels.update(updated);
            }));
        });
    }

    public Mono<Void> delete(UUID id) {
        return get(id).flatMap(model -> channelModels.delete(model.id()));
    }

    private Mono<Void> validateReferences(UUID channelId, UUID modelId) {
        return Mono.zip(
                        channels.findById(channelId).switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.NOT_FOUND, "ai-channel-not-found", "AI 渠道不存在。"))),
                        models.findById(modelId).switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.NOT_FOUND, "ai-model-not-found", "AI 模型不存在。"))))
                .then();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
