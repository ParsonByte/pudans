package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.contract.rbac.TempRoleBinding;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.rbac.permission.RbacCache;

@Service
@RequiredArgsConstructor
public class TempGrants {

    private static final String KEY_PREFIX = "pudans:rbac:temp-roles:";

    private static final RedisScript<Long> EXTEND_TTL_SCRIPT = RedisScript.of("""
            local ttl = redis.call('PTTL', KEYS[1])
            local requested = tonumber(ARGV[1])
            if ttl < 0 or ttl < requested then
                redis.call('PEXPIREAT', KEYS[1], ARGV[2])
            end
            return 1
            """, Long.class);

    private static final char MEMBER_SEPARATOR = '|';

    private static final Duration LOCAL_CACHE_TTL = Duration.ofSeconds(5);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final RbacCache rbacCache;

    private final Cache<String, List<TempRoleGrant>> grantCache = CacheBuilder.newBuilder()
            .expireAfterWrite(LOCAL_CACHE_TTL)
            .maximumSize(10_000)
            .build();

    private Clock clock = Clock.systemUTC();

    // 拼出用户临时角色的有序集合键。
    public static String keyOf(String username) {
        return KEY_PREFIX + username;
    }

    // 把绑定名与角色名编码为集合成员。
    private static String memberOf(String bindingName, String roleName) {
        return bindingName + MEMBER_SEPARATOR + roleName;
    }

    // 解析集合成员与过期分数，非法成员返回空。
    private static TempRoleGrant toGrant(String member, Double score) {
        if (member == null || score == null) {
            return null;
        }
        var separatorIndex = member.lastIndexOf(MEMBER_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == member.length() - 1) {
            return null;
        }
        var bindingName = member.substring(0, separatorIndex);
        var roleName = member.substring(separatorIndex + 1);
        return new TempRoleGrant(bindingName, roleName, Instant.ofEpochMilli(score.longValue()));
    }

    // 替换时钟，便于测试控制过期时间。
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // 写入未过期授权，并延长集合键的存活时间。
    public Mono<Void> grant(TempRoleBinding binding) {
        var spec = binding.getSpec();
        if (spec == null || spec.getUsername() == null || CollectionUtils.isEmpty(spec.getRoleNames())) {
            return Mono.empty();
        }
        var expiresAt = spec.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            return Mono.empty();
        }
        var key = keyOf(spec.getUsername());
        var members = spec.getRoleNames().stream()
                .map(roleName -> ZSetOperations.TypedTuple.of(
                        memberOf(binding.getMetadata().getName(), roleName), (double) expiresAt.toEpochMilli()))
                .toList();
        return redisTemplate
                .opsForZSet()
                .addAll(key, members)
                .then(Mono.defer(() -> redisTemplate
                        .execute(
                                EXTEND_TTL_SCRIPT,
                                List.of(key),
                                List.of(
                                        String.valueOf(Duration.between(clock.instant(), expiresAt).toMillis()),
                                        String.valueOf(expiresAt.toEpochMilli())))
                        .next()))
                .then(rbacCache.evictUserRoles(spec.getUsername()))
                .doOnSuccess(ignored -> grantCache.invalidate(spec.getUsername()));
    }

    // 从集合移除该绑定授予的临时角色。
    public Mono<Void> revoke(TempRoleBinding binding) {
        var spec = binding.getSpec();
        if (spec == null || spec.getUsername() == null || CollectionUtils.isEmpty(spec.getRoleNames())) {
            return Mono.empty();
        }
        var key = keyOf(spec.getUsername());
        var members = spec.getRoleNames().stream()
                .map(roleName -> memberOf(binding.getMetadata().getName(), roleName))
                .toArray(String[]::new);
        return redisTemplate
                .opsForZSet()
                .remove(key, (Object[]) members)
                .then(rbacCache.evictUserRoles(spec.getUsername()))
                .doOnSuccess(ignored -> grantCache.invalidate(spec.getUsername()));
    }

    // 返回当前有效授权，短期本地缓存降低读压力。
    public Flux<TempRoleGrant> activeGrants(String username) {
        if (username == null || username.isBlank()) {
            return Flux.empty();
        }
        var now = clock.instant();
        var cached = grantCache.getIfPresent(username);
        if (cached != null) {
            return Flux.fromIterable(cached).filter(grant -> grant.expiresAt().isAfter(now));
        }
        return loadGrants(username)
                .collectList()
                .doOnNext(grants -> grantCache.put(username, grants))
                .flatMapMany(Flux::fromIterable);
    }

    // 清理过期成员后读取仍有效的授权条目。
    private Flux<TempRoleGrant> loadGrants(String username) {
        var key = keyOf(username);
        var now = clock.millis();
        return redisTemplate
                .opsForZSet()
                .removeRangeByScore(key, Range.closed(0D, (double) now))
                .thenMany(redisTemplate
                        .opsForZSet()
                        .rangeByScoreWithScores(key, Range.closed((double) now, Double.POSITIVE_INFINITY)))
                .map(tuple -> toGrant(tuple.getValue(), tuple.getScore()))
                .filter(grant -> grant != null);
    }

    // 返回用户当前有效的临时角色名列表。
    public Mono<List<String>> activeRoleNames(String username) {
        return activeGrants(username)
                .map(TempRoleGrant::roleName)
                .distinct()
                .collectList();
    }

    // 返回去重后的临时角色名集合。
    public Mono<Set<String>> activeRoleNameSet(String username) {
        return activeRoleNames(username).map(roles -> roles.stream().collect(Collectors.toSet()));
    }
}
