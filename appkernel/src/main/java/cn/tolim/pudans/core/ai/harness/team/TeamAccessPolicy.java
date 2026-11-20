package cn.tolim.pudans.core.ai.harness.team;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface TeamAccessPolicy {

    Mono<Void> requireMember(String teamName, String userId);
}