package cn.tolim.pudans.core.rbac.permission;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Watcher;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.infra.cache.ReactiveRedisCacheStore;

@Slf4j
@Service
@RequiredArgsConstructor
public class RbacCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final Duration GENERATION_TTL = Duration.ofSeconds(5);

    private static final String GENERATION_KEY = "pudans:rbac:generation";

    private static final String USER_ROLES_KEY_PREFIX = "pudans:rbac:user-roles:";

    private static final String ROLE_RULES_KEY_PREFIX = "pudans:rbac:role-rules:";

    private static final Set<String> INVALIDATING_KINDS = Set.of(
            Role.KIND,
            cn.tolim.pudans.contract.rbac.RoleBinding.KIND,
            cn.tolim.pudans.contract.rbac.UserGroup.KIND,
            cn.tolim.pudans.contract.rbac.UserGroupBinding.KIND);

    private final ReactiveRedisCacheStore cacheStore;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final ReactiveExtensionClient reactiveExtensionClient;

    private volatile long generation = 0L;

    private volatile long generationLoadedAt = 0L;

    @PostConstruct
    void registerWatcher() {
        reactiveExtensionClient.watch(new Watcher() {
            @Override
            public void onAdd(Extension extension) {
                invalidateIfRbac(extension);
            }

            @Override
            public void onUpdate(Extension oldExtension, Extension newExtension) {
                invalidateIfRbac(newExtension);
            }

            @Override
            public void onDelete(Extension extension) {
                invalidateIfRbac(extension);
            }

            @Override
            public void dispose() {}

            @Override
            public boolean isDisposed() {
                return false;
            }
        });
    }

    private void invalidateIfRbac(Extension extension) {
        if (extension == null) {
            return;
        }
        try {
            if (!INVALIDATING_KINDS.contains(extension.getKind())) {
                return;
            }
        } catch (RuntimeException e) {
            return;
        }
        invalidateAll().subscribe(ignored -> {}, error -> log.warn("无法失效 RBAC 缓存", error));
    }

    public Mono<Void> invalidateAll() {
        return redisTemplate
                .opsForValue()
                .increment(GENERATION_KEY)
                .doOnNext(value -> generationLoadedAt = 0L)
                .then();
    }

    private Mono<Long> currentGeneration() {
        var loadedAt = generationLoadedAt;
        if (loadedAt != 0L && System.currentTimeMillis() - loadedAt < GENERATION_TTL.toMillis()) {
            return Mono.just(generation);
        }
        return redisTemplate
                .opsForValue()
                .get(GENERATION_KEY)
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .defaultIfEmpty(0L)
                .doOnNext(value -> {
                    generation = value;
                    generationLoadedAt = System.currentTimeMillis();
                });
    }

    public Mono<List<String>> cachedUserRoles(String username, Mono<List<String>> loader) {
        return currentGeneration()
                .flatMap(generationValue -> {
                    var key = USER_ROLES_KEY_PREFIX + generationValue + ':' + username;
                    return cacheStore
                            .get(key)
                            .map(json -> JsonUtils.jsonToObject(json, List.class))
                            .map(values -> (List<String>) values)
                            .switchIfEmpty(Mono.defer(() -> loader.flatMap(roles -> {
                                var normalized = roles.stream().sorted().distinct().toList();
                                return cacheStore
                                        .put(key, JsonUtils.objectToJson(normalized), TTL)
                                        .thenReturn(normalized);
                            })));
                });
    }

    public Mono<Void> evictUserRoles(String username) {
        return currentGeneration()
                .flatMap(generationValue ->
                        cacheStore.evict(USER_ROLES_KEY_PREFIX + generationValue + ':' + username));
    }

    public Mono<List<Role>> cachedRoleDependencies(Set<String> roleNames, Mono<List<Role>> loader) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Mono.just(List.of());
        }
        return currentGeneration()
                .flatMap(generationValue -> {
                    var key = ROLE_RULES_KEY_PREFIX + generationValue + ':' + hash(roleNames);
                    return cacheStore
                            .get(key)
                            .map(json -> deserializeRoles(json))
                            .switchIfEmpty(Mono.defer(() -> loader.flatMap(roles -> cacheStore
                                    .put(key, JsonUtils.objectToJson(serializeRoles(roles)), TTL)
                                    .thenReturn(roles))));
                });
    }

    private static String hash(Set<String> roleNames) {
        try {
            var joined = roleNames.stream().sorted().reduce("", (a, b) -> a + "," + b);
            var digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(roleNames.hashCode());
        }
    }

    private static String[] nullSafe(String[] values) {
        return values == null ? new String[0] : values;
    }

    private static List<CachedRole> serializeRoles(List<Role> roles) {
        return roles.stream()
                .map(role -> new CachedRole(
                        role.getMetadata().getName(),
                        role.getRules() == null
                                ? List.of()
                                : role.getRules().stream()
                                        .map(rule -> new CachedRule(
                                                Arrays.asList(nullSafe(rule.getApiGroups())),
                                                Arrays.asList(nullSafe(rule.getResources())),
                                                Arrays.asList(nullSafe(rule.getResourceNames())),
                                                Arrays.asList(nullSafe(rule.getNonResourceURLs())),
                                                Arrays.asList(nullSafe(rule.getVerbs()))))
                                        .toList()))
                .toList();
    }

    private static List<Role> deserializeRoles(String json) {
        List<CachedRole> cachedRoles = JsonUtils.jsonToObject(
                json, new com.fasterxml.jackson.core.type.TypeReference<List<CachedRole>>() {});
        return cachedRoles.stream()
                .map(cached -> {
                    var role = new Role();
                    var metadata = new Metadata();
                    metadata.setName(cached.name());
                    role.setMetadata(metadata);
                    role.setRules(cached.rules().stream()
                            .map(rule -> new Role.PolicyRule(
                                    nullSafe(rule.apiGroups().toArray(String[]::new)),
                                    nullSafe(rule.resources().toArray(String[]::new)),
                                    nullSafe(rule.resourceNames().toArray(String[]::new)),
                                    nullSafe(rule.nonResourceURLs().toArray(String[]::new)),
                                    nullSafe(rule.verbs().toArray(String[]::new))))
                            .toList());
                    return role;
                })
                .toList();
    }

    public record CachedRole(String name, List<CachedRule> rules) {}

    public record CachedRule(
            List<String> apiGroups,
            List<String> resources,
            List<String> resourceNames,
            List<String> nonResourceURLs,
            List<String> verbs) {}
}
