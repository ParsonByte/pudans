package cn.tolim.pudans.core.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.cache.ReactiveRedisCacheStore;

@ExtendWith(MockitoExtension.class)
class RbacCacheServiceTest {

    @Mock
    ReactiveRedisCacheStore cacheStore;

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    @Mock
    ReactiveExtensionClient reactiveExtensionClient;

    private RbacCacheService service;

    @BeforeEach
    void setUp() {
        service = new RbacCacheService(cacheStore, redisTemplate, reactiveExtensionClient);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(cacheStore.get(anyString())).thenReturn(Mono.empty());
        when(cacheStore.put(anyString(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    void policyRuleWithMissingArraysIsSerializedWithoutNpe() {
        var role = new Role();
        var metadata = new cn.tolim.pudans.contract.extension.Metadata();
        metadata.setName("role-a");
        role.setMetadata(metadata);
        role.setRules(List.of(new Role.PolicyRule(
                new String[] {"*"}, new String[] {"users"}, null, null, new String[] {"get"})));

        StepVerifier.create(service.cachedRoleDependencies(Set.of("role-a"), Mono.just(List.of(role))))
                .assertNext(roles -> {
                    assertThat(roles).hasSize(1);
                    assertThat(roles.get(0).getRules().get(0).getResourceNames()).isEmpty();
                    assertThat(roles.get(0).getRules().get(0).getNonResourceURLs()).isEmpty();
                })
                .verifyComplete();

        verify(cacheStore).put(anyString(), anyString(), any());
    }

    @Test
    void roleDeserializedWithoutOptionalRuleFieldsIsSerializedWithoutNpe() {
        var role = cn.tolim.pudans.contract.extension.Unstructured.OBJECT_MAPPER.convertValue(
                java.util.Map.of(
                        "apiVersion",
                        "pudans.tolim.cn/v1alpha1",
                        "kind",
                        "Role",
                        "metadata",
                        java.util.Map.of("name", "role-a"),
                        "rules",
                        List.of(java.util.Map.of(
                                "apiGroups", List.of("*"),
                                "resources", List.of("users"),
                                "verbs", List.of("list")))),
                Role.class);

        StepVerifier.create(service.cachedRoleDependencies(Set.of("role-a"), Mono.just(List.of(role))))
                .assertNext(roles -> {
                    assertThat(roles).hasSize(1);
                    assertThat(roles.get(0).getRules()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void cacheKeyIncludesTheRbacGeneration() {
        when(valueOperations.get("pudans:rbac:generation")).thenReturn(Mono.just("7"));
        var role = new Role();
        var metadata = new cn.tolim.pudans.contract.extension.Metadata();
        metadata.setName("role-a");
        role.setMetadata(metadata);
        role.setRules(List.of(new Role.PolicyRule(
                new String[] {"*"}, new String[] {"users"}, null, null, new String[] {"get"})));

        StepVerifier.create(service.cachedRoleDependencies(Set.of("role-a"), Mono.just(List.of(role))))
                .expectNextCount(1)
                .verifyComplete();

        var keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheStore).put(keyCaptor.capture(), anyString(), any());
        assertThat(keyCaptor.getValue()).startsWith("pudans:rbac:role-rules:7:");
    }
}
