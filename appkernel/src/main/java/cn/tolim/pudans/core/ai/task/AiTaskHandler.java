package cn.tolim.pudans.core.ai.task;

import reactor.core.publisher.Mono;

public interface AiTaskHandler {

    String taskType();

    Mono<Void> handle(AiTask task);
}
