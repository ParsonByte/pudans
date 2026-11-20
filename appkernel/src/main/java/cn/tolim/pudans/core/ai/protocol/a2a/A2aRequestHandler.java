package cn.tolim.pudans.core.ai.protocol.a2a;

import cn.tolim.pudans.core.ai.protocol.ProtocolAgentRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class A2aRequestHandler {

    private static final String ARTIFACT_SUFFIX = "-result";

    private final ProtocolAgentRuntime runtime;

    private final A2aTaskStore taskStore;

    public Mono<A2aTask> send(String userId, A2aSendParams params) {
        var message = params.message();
        var contextId = identifier(message.contextId());
        var taskId = identifier(message.taskId());
        var record = new A2aTaskRecord(userId, taskId, contextId);
        var saved = new AtomicBoolean();
        return runtime.start(runRequest(userId, params.agentId(), contextId, message, false))
                .flatMap(run -> {
                    record.attach(run);
                    record.working();
                    saved.set(true);
                    return taskStore.save(record).then(run.result());
                })
                .flatMap(result -> {
                    record.complete(A2aArtifact.ofText(taskId + ARTIFACT_SUFFIX, result.content()));
                    return taskStore.save(record).thenReturn(record.task());
                })
                .doOnCancel(() -> {
                    record.cancel();
                    taskStore.save(record).subscribe();
                })
                .onErrorResume(error -> {
                    record.fail();
                    var persist = saved.get() ? taskStore.save(record) : Mono.<Void>empty();
                    return persist.then(Mono.error(error));
                });
    }

    public Flux<A2aStreamEvent> stream(String userId, A2aSendParams params) {
        var message = params.message();
        var contextId = identifier(message.contextId());
        var taskId = identifier(message.taskId());
        var record = new A2aTaskRecord(userId, taskId, contextId);
        return Flux.defer(() -> runtime
                .start(runRequest(userId, params.agentId(), contextId, message, true))
                .flatMapMany(run -> {
                    record.attach(run);
                    var submitted = record.status();
                    record.working();
                    var start = Flux.just(
                            A2aStreamEvent.Status.of(taskId, contextId, submitted, false),
                            A2aStreamEvent.Status.of(taskId, contextId, record.status(), false));
                    var finish = taskStore
                            .save(record)
                            .thenMany(run.result()
                                    .flatMapMany(result -> {
                                        var events = complete(
                                                record, taskId, contextId, result.content());
                                        return taskStore
                                                .save(record)
                                                .thenMany(Flux.fromIterable(events));
                                    })
                                    .onErrorResume(error -> {
                                        var events = failed(record, taskId, contextId);
                                        return taskStore
                                                .save(record)
                                                .thenMany(Flux.fromIterable(events));
                                    })
                                    .doOnCancel(run::cancel));
                    return Flux.concat(start, finish);
                }));
    }

    public Mono<A2aTask> task(String userId, JsonNode params) {
        var taskId = requiredTaskId(params);
        return taskStore
                .find(userId, taskId)
                .map(A2aTaskRecord::task)
                .switchIfEmpty(Mono.error(A2aRpcException.taskNotFound(taskId)));
    }

    public Mono<A2aTask> cancel(String userId, JsonNode params) {
        var taskId = requiredTaskId(params);
        return taskStore
                .find(userId, taskId)
                .switchIfEmpty(Mono.error(A2aRpcException.taskNotFound(taskId)))
                .flatMap(record -> {
                    var run = record.run();
                    if (!record.cancel()) {
                        return Mono.error(A2aRpcException.taskNotCancelable(taskId));
                    }
                    if (run != null) {
                        run.cancel();
                    }
                    return taskStore.save(record).thenReturn(record.task());
                });
    }

    private static List<A2aStreamEvent> complete(
            A2aTaskRecord record, String taskId, String contextId, String content) {
        record.complete(A2aArtifact.ofText(taskId + ARTIFACT_SUFFIX, content));
        var artifact = record.artifact();
        if (artifact == null) {
            return List.of(A2aStreamEvent.Status.of(taskId, contextId, record.status(), true));
        }
        return List.of(
                A2aStreamEvent.Artifact.of(taskId, contextId, artifact),
                A2aStreamEvent.Status.of(taskId, contextId, record.status(), true));
    }

    private static List<A2aStreamEvent> failed(
            A2aTaskRecord record, String taskId, String contextId) {
        record.fail();
        return List.of(A2aStreamEvent.Status.of(taskId, contextId, record.status(), true));
    }

    private static ProtocolAgentRuntime.ProtocolRunRequest runRequest(
            String userId, @Nullable String agentId, String threadId, A2aMessage message, boolean streaming) {
        return new ProtocolAgentRuntime.ProtocolRunRequest(
                userId, agentId, threadId, null, UserMessage.from(message.text()), streaming, Map.of());
    }

    private static String requiredTaskId(JsonNode params) {
        if (params == null || !params.isObject()) {
            throw A2aRpcException.invalidParams("params 必须是对象。");
        }
        var id = params.get("id");
        if (id == null || !id.isTextual() || !StringUtils.hasText(id.asText())) {
            throw A2aRpcException.invalidParams("params.id 不能为空。");
        }
        return id.asText();
    }

    private static String identifier(@Nullable String value) {
        return StringUtils.hasText(value) ? value : UUID.randomUUID().toString();
    }
}