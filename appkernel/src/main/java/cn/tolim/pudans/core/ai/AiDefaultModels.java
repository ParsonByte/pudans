package cn.tolim.pudans.core.ai;

import cn.tolim.pudans.contract.ai.AiDefaultModel;
import cn.tolim.pudans.contract.ai.dto.AiDefaultModelRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiDefaultModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiDefaultModels {

    private final AiDefaultModelRepository defaults;

    private final AiModelRepository models;

    private Clock clock = Clock.systemUTC();

    public Flux<AiDefaultModel> list(String workspaceId) {
        return defaults.findAllByWorkspace(requireWorkspace(workspaceId));
    }

    public Mono<AiDefaultModel> put(
            String workspaceId, String capability, AiDefaultModelRequest request) {
        if (request == null || request.modelId() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-default-model-required",
                    "默认模型不能为空。"));
        }
        var workspace = requireWorkspace(workspaceId);
        var normalizedCapability = AiSupport.normalizeCapability(capability);
        return models
                .findById(request.modelId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-model-not-found", "AI 模型不存在。")))
                .flatMap(model -> {
                    if (model.capabilities() == null
                            || !model.capabilities().containsKey(normalizedCapability)) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "ai-model-capability-missing",
                                "模型不支持请求的能力。"));
                    }
                    return models
                            .existsAvailable(request.modelId(), workspace)
                            .flatMap(available -> Boolean.TRUE.equals(available)
                                    ? Mono.just(model)
                                    : Mono.error(new ApiException(
                                            HttpStatus.SERVICE_UNAVAILABLE,
                                            "ai-model-unavailable",
                                            "默认模型在当前工作区没有可用渠道。")));
                })
                .then(defaults.findByWorkspaceAndCapability(workspace, normalizedCapability))
                .flatMap(existing -> {
                    var updated = new AiDefaultModel(
                            existing.id(),
                            existing.workspaceId(),
                            existing.capability(),
                            request.modelId(),
                            existing.createdAt(),
                            Instant.now(clock));
                    return defaults.update(updated);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    var now = Instant.now(clock);
                    return defaults.insert(new AiDefaultModel(
                            UUID.randomUUID(),
                            workspace,
                            normalizedCapability,
                            request.modelId(),
                            now,
                            now));
                }));
    }

    private static String requireWorkspace(String workspaceId) {
        return AiSupport.requiredText(
                workspaceId, "request-workspace-missing", "缺少当前工作区请求头。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
