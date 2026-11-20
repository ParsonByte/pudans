package cn.tolim.pudans.core.ai.harness.tool;

import reactor.core.publisher.Mono;

public interface ToolResultStore {

    Mono<String> save(String runId, String toolName, String output);

    Mono<String> read(String id);
}