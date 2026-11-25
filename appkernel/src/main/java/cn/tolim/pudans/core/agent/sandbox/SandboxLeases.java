package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.LeaseResult;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SandboxLeases {

    private static final String KEY_PREFIX = "pudans:agent:sandbox-lock:";

    private final ReactiveRedisTokenLease leases;

    private final SandboxProperties properties;

    // 每个沙箱的创建、执行、快照和停止通过令牌租约串行化。
    public Mono<TokenLease> acquire(UUID sandboxId) {
        return leases.acquire(KEY_PREFIX + sandboxId, properties.normalizedLeaseTtl());
    }

    public Mono<LeaseResult> release(TokenLease lease) {
        return leases.release(lease);
    }
}
