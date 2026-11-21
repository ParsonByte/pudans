package cn.tolim.pudans.core.ai.memory.prompt;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.extension.api.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.api.controller.Controller;
import cn.tolim.pudans.extension.api.controller.ControllerBuilder;
import cn.tolim.pudans.extension.api.controller.ReactiveReconciler;
import cn.tolim.pudans.extension.api.controller.Reconciler;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryPromptLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiMemoryPromptLogReconciler implements ReactiveReconciler<Reconciler.Request> {

    private final InternalReactiveExtensionClient client;

    private final AiMemoryPromptLogRepository logRepository;

    @Override
    public Mono<Result> reconcileReactive(Request request) {
        return client
                .fetch(AiMemoryPrompt.class, request.name())
                .flatMap(prompt -> {
                    if (prompt.getSpec() == null) {
                        return Mono.just(Result.doNotRetry());
                    }
                    var spec = prompt.getSpec();
                    var actor = StringUtils.hasText(spec.getUpdatedBy())
                            ? spec.getUpdatedBy()
                            : spec.getCreatedBy();
                    return logRepository
                            .insert(new MemoryPromptLog(
                                    null,
                                    request.name(),
                                    spec.getVersion(),
                                    MemoryPromptLog.Action.UPDATE,
                                    null,
                                    null,
                                    null,
                                    spec.getLayer(),
                                    actor,
                                    null))
                            .thenReturn(Result.doNotRetry());
                })
                .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AiMemoryPrompt())
                .syncAllOnStart(false)
                .onAddMatcher(extension -> false)
                .onDeleteMatcher(extension -> false)
                .onUpdateMatcher(extension -> true)
                .build();
    }
}