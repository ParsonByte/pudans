package cn.tolim.pudans.core.rbac.permission;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Watcher;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.cache.ReactiveRedisCacheStore;
import cn.tolim.pudans.infra.utils.Digests;

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

    // 监听授权资源变化并失效相关缓存。
    @PostConstruct
    void registerWatcher() {
        reactiveExtensionClient.watch(new Watcher() {
            // 新增资源可能改变授权，需失效缓存。
            @Override
            public void onAdd(Extension extension) {
                invalidateIfRbac(extension);
            }

            // 更新资源可能改变授权，需失效缓存。
            @Override
            public void onUpdate(Extension oldExtension, Extension newExtension) {
                invalidateIfRbac(newExtension);
            }

            // 删除资源可能改变授权，需失效缓存。
            @Override
            public void onDelete(Extension extension) {
                invalidateIfRbac(extension);
            }

            @Override
            public void dispose() {}

            // 观察者常驻，始终返回未释放状态。
            @Override
            public boolean isDisposed() {
                return false;
            }
        });
    }

    // 仅当变更涉及授权资源时才失效缓存。
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

    // 递增代际号，使所有节点同时失效缓存。
    public Mono<Void> invalidateAll() {
        return redisTemplate
                .opsForValue()
                .increment(GENERATION_KEY)
                .doOnNext(value -> generationLoadedAt = 0L)
                .then();
    }

    // 读取缓存代际号，短窗口内复用本地值。
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

    // 缓存用户角色，未命中时通过加载器回源。
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

    // 删除指定用户的角色缓存。
    public Mono<Void> evictUserRoles(String username) {
        return currentGeneration()
                .flatMap(generationValue ->
                        cacheStore.evict(USER_ROLES_KEY_PREFIX + generationValue + ':' + username));
    }

    // 按角色名集合缓存角色依赖查询结果。
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

    // 角色名排序后摘要，保证键与顺序无关。
    private static String hash(Set<String> roleNames) {
        var joined = roleNames.stream().sorted().reduce("", (a, b) -> a + "," + b);
        return Digests.sha256Hex(joined);
    }

    // 把可空数组转换为空数组。
    private static String[] nullSafe(String[] values) {
        return values == null ? new String[0] : values;
    }

    // 只序列化缓存需要的角色名与规则字段。
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

    // 从缓存结构重建角色，缺失数组按空处理。
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
