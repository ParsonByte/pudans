package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.LeaseResult;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryDailyLease {

    private static final String KEY = "pudans:memory:daily:lock:global";

    private static final Duration TTL = Duration.ofMinutes(30);

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(10);

    private final ReactiveRedisTokenLease leases;

    public Mono<TokenLease> acquire() {
        return leases.acquire(KEY, TTL);
    }

    public Mono<Boolean> renew(TokenLease lease) {
        return leases.renew(lease, TTL).map(result -> result == LeaseResult.APPLIED);
    }

    public Flux<Boolean> heartbeats(TokenLease lease) {
        return Flux.interval(HEARTBEAT_INTERVAL)
                .concatMap(ignored -> renew(lease))
                .takeWhile(Boolean::booleanValue);
    }

    public Mono<LeaseResult> release(TokenLease lease) {
        return leases.release(lease);
    }
}
