package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.core.ai.task.AiTaskHandler;
import cn.tolim.pudans.core.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiModelTaskHandler implements AiTaskHandler {

    public static final String TASK_TYPE = AiModelTaskScheduler.TASK_TYPE;

    private static final String STATUS_RUNNING = "RUNNING";

    private static final Duration MAX_POLL_AGE = Duration.ofHours(2);

    private final AiModelRequestRepository requestRepository;

    private final ReactiveExtensionClient client;

    private final AiModelAdapterRegistry adapterRegistry;

    private final AiGenerationRecorder recorder;

    private final AiModelTaskScheduler scheduler;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Mono<Void> handle(AiTask task) {
        var requestId = text(task.payload().get("requestId"));
        if (requestId == null) {
            return Mono.empty();
        }
        return requestRepository
                .findByRequestId(requestId)
                .filter(row -> STATUS_RUNNING.equals(row.status()))
                .filter(row -> row.providerTaskId() != null)
                .flatMap(row -> expired(row)
                        ? recorder.fail(row, new IllegalStateException("生成任务超时。"), row.createdAt())
                        : load(row)
                                .flatMap(resources -> poll(task, row, resources))
                                .onErrorResume(error ->
                                        recorder.fail(row, error, row.createdAt())))
                .then();
    }

    private static boolean expired(ModelRequestRow row) {
        return row.createdAt().plus(MAX_POLL_AGE).isBefore(Instant.now());
    }

    private Mono<Void> poll(AiTask task, ModelRequestRow row, Resources resources) {
        var capability = AiModel.Capability.valueOf(row.capability());
        var adapter = adapterRegistry.require(
                resources.provider().getSpec().getProtocol(), capability);
        var free = resources.provider().getSpec().isFree()
                || resources.account().getSpec().isFreePlan();
        var estimate = estimate(row);
        return adapter
                .poll(
                        resources.provider(),
                        resources.account(),
                        resources.model(),
                        capability,
                        row.providerTaskId(),
                        params(row))
                .switchIfEmpty(Mono.error(new IllegalStateException("供应商返回空任务状态。")))
                .flatMap(response -> {
                    if (response.pending()) {
                        return scheduler.reschedule(task).then();
                    }
                    if (response.status() == AiModelResponse.TaskStatus.FAILED
                            || response.status() == AiModelResponse.TaskStatus.CANCELLED) {
                        return recorder.fail(
                                row, new IllegalStateException("供应商生成任务失败。"), row.createdAt());
                    }
                    return recorder
                            .succeed(row, resources.model(), free, response, estimate, row.createdAt())
                            .then();
                });
    }

    private Mono<Resources> load(ModelRequestRow row) {
        return client.get(AiProvider.class, row.selectedProvider())
                .flatMap(provider -> client.get(AiProviderAccount.class, row.selectedAccount())
                        .flatMap(account -> client.get(AiModel.class, row.selectedModel())
                                .map(model -> new Resources(provider, account, model))));
    }

    private static Map<String, JsonNode> params(ModelRequestRow row) {
        var payload = row.requestPayload();
        var params = payload == null ? null : payload.get("params");
        if (params == null || !params.isObject()) {
            return Map.of();
        }
        return JsonUtils.jsonToObject(
                params.toString(), new TypeReference<Map<String, JsonNode>>() {});
    }

    private static AiModelUsage estimate(ModelRequestRow row) {
        var payload = row.requestPayload();
        var usage = payload == null ? null : payload.get("estimatedUsage");
        if (usage == null || usage.isNull()) {
            return AiModelUsage.EMPTY;
        }
        return JsonUtils.jsonToObject(usage.toString(), AiModelUsage.class);
    }

    private static @Nullable String text(Object value) {
        if (value == null) {
            return null;
        }
        var text = value.toString();
        return text.isBlank() ? null : text;
    }

    private record Resources(AiProvider provider, AiProviderAccount account, AiModel model) {}
}
