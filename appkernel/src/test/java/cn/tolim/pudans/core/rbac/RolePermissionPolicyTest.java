package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.rbac.resource.Role;
import cn.tolim.pudans.contract.rbac.resource.Role.PolicyRule;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.rbac.permission.PermissionDirectory;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RolePermissionPolicyTest {

    @Mock
    Roles roleService;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    PermissionDirectory permissionDirectory;

    private RolePermissionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RolePermissionPolicy(roleService, client, permissionDirectory);
        lenient()
                .when(permissionDirectory.expand(anyList()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient()
                .when(client.listAll(eq(Role.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.empty());
    }

    @Test
    void createRoleWithCoveredPermissionsPasses() {
        var actor = role(
                "limited-manager",
                List.of(rule(
                        new String[] {"api.pudans.tolim.cn"},
                        new String[] {"users"},
                        new String[] {"get", "list"})),
                "users:view");
        var requested = role(
                "new-role",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"get"})),
                "users:view");
        stubRoles(actor);

        StepVerifier.create(withActor(policy.checkCreate(requested), "limited-manager"))
                .verifyComplete();
    }

    @Test
    void createRoleWithUncoveredRuleIsRejected() {
        var actor = role(
                "limited-manager",
                List.of(rule(
                        new String[] {"api.pudans.tolim.cn"},
                        new String[] {"users"},
                        new String[] {"get", "list"})),
                "users:view");
        var requested = role(
                "new-role",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "users:view");
        stubRoles(actor);

        StepVerifier.create(withActor(policy.checkCreate(requested), "limited-manager"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("超出当前账号权限范围");
                })
                .verify();
    }

    @Test
    void createRoleWithWildcardVerbRequiresWildcardGrant() {
        var actor = role(
                "limited-manager",
                List.of(rule(
                        new String[] {"api.pudans.tolim.cn"},
                        new String[] {"users"},
                        new String[] {"get", "list"})));
        var requested = role(
                "new-role",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"*"})));
        stubRoles(actor);

        StepVerifier.create(withActor(policy.checkCreate(requested), "limited-manager"))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createRoleWithUiPermissionBeyondActorIsRejected() {
        var actor = role("limited-manager", List.of(), "users:view");
        var requested = role("new-role", List.of(), "users:manage");
        stubRoles(actor);

        StepVerifier.create(withActor(policy.checkCreate(requested), "limited-manager"))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createRoleWithDependencyBeyondActorIsRejected() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"get"})),
                "users:view");
        var template = role(
                "role-template-secrets",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "secrets:view");
        var requested = withDependencies(role("new-role", List.of()), "role-template-secrets");
        stubRoles(actor, template);

        StepVerifier.create(withActor(policy.checkCreate(requested), "limited-manager"))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createRoleWithDependencyWithinActorPermissionsPasses() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "secrets:view");
        var template = role(
                "role-template-secrets",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "secrets:view");
        var requested = withDependencies(role("new-role", List.of()), "role-template-secrets");
        stubRoles(actor, template);

        StepVerifier.create(withActor(policy.checkCreate(requested), "limited-manager"))
                .verifyComplete();
    }

    @Test
    void superRoleActorCanCreateAnyRole() {
        var requested = role(
                "new-role",
                List.of(rule(new String[] {"*"}, new String[] {"*"}, new String[] {"*"})),
                "*");

        StepVerifier.create(withActor(policy.checkCreate(requested), "super-role"))
                .verifyComplete();
    }

    @Test
    void updateRoleMayKeepExistingPermissionsBeyondActor() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"get"})),
                "users:view");
        var existing = role(
                "existing-role",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "secrets:view");
        var requested = role(
                "existing-role",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "secrets:view");
        stubRoles(actor, existing);

        StepVerifier.create(withActor(policy.checkUpdate(requested), "limited-manager"))
                .verifyComplete();
    }

    @Test
    void updateRoleCannotAddPermissionsBeyondActor() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"get"})),
                "users:view");
        var existing = role(
                "existing-role",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "secrets:view");
        var requested = role(
                "existing-role",
                List.of(
                        rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"}),
                        rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"list"})),
                "secrets:view");
        stubRoles(actor, existing);

        StepVerifier.create(withActor(policy.checkUpdate(requested), "limited-manager"))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createRoleWithAggregateLabelIsRejectedForNonSuperActor() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"roles"}, new String[] {"update"})));
        var requested = withAggregateLabel(
                role("existing-role", List.of(rule(new String[] {"*"}, new String[] {"*"}, new String[] {"*"}))),
                "limited-manager");
        stubRoles(actor);

        StepVerifier.create(withActor(policy.checkCreate(requested), "limited-manager"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("聚合标签");
                })
                .verify();
    }

    @Test
    void updateRoleWithAggregateLabelIsRejectedForNonSuperActor() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"roles"}, new String[] {"update"})));
        var existing = role(
                "existing-role",
                List.of(rule(new String[] {"*"}, new String[] {"*"}, new String[] {"*"})));
        var requested = withAggregateLabel(existing, "limited-manager");
        stubRoles(actor, existing);

        StepVerifier.create(withActor(policy.checkUpdate(requested), "limited-manager"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("聚合标签");
                })
                .verify();
    }

    @Test
    void createRoleWithMissingAggregateTargetIsRejected() {
        var requested = withAggregateLabel(role("new-role", List.of()), "missing-role");
        when(client.fetch(Role.class, "missing-role")).thenReturn(Mono.empty());

        StepVerifier.create(withActor(policy.checkCreate(requested), "super-role"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不存在");
                })
                .verify();
    }

    @Test
    void createRoleWithExistingAggregateTargetPasses() {
        var requested = withAggregateLabel(role("new-role", List.of()), "existing-role");
        when(client.fetch(Role.class, "existing-role")).thenReturn(Mono.just(role("existing-role", List.of())));

        StepVerifier.create(withActor(policy.checkCreate(requested), "super-role"))
                .verifyComplete();
    }

    @Test
    void grantRoleWithinActorPermissionsPasses() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"get"})),
                "users:view");
        var granted = role(
                "custom-user-reader",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"get"})),
                "users:view");
        stubRoles(actor, granted);

        StepVerifier.create(withActor(policy.checkGrant(List.of("custom-user-reader")), "limited-manager"))
                .verifyComplete();
    }

    @Test
    void grantRoleBeyondActorPermissionsIsRejected() {
        var actor = role(
                "limited-manager",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"users"}, new String[] {"get"})),
                "users:view");
        var granted = role(
                "custom-secret-reader",
                List.of(rule(new String[] {"api.pudans.tolim.cn"}, new String[] {"secrets"}, new String[] {"get"})),
                "secrets:view");
        stubRoles(actor, granted);

        StepVerifier.create(withActor(policy.checkGrant(List.of("custom-secret-reader")), "limited-manager"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("超出当前账号权限范围");
                })
                .verify();
    }

    @Test
    void superRoleActorCanGrantAnyRole() {
        StepVerifier.create(withActor(policy.checkGrant(List.of("system-admin-role")), "super-role"))
                .verifyComplete();
    }

    private void stubRoles(Role... roles) {
        Map<String, Role> byName = Arrays.stream(roles)
                .collect(Collectors.toMap(role -> role.getMetadata().getName(), role -> role));
        when(roleService.listDependenciesFlux(anySet())).thenAnswer(invocation -> {
            Set<String> names = invocation.getArgument(0);
            return Flux.fromIterable(names).map(byName::get).filter(Objects::nonNull);
        });
        when(roleService.listPermissions(anySet())).thenAnswer(invocation -> {
            Set<String> names = invocation.getArgument(0);
            return Flux.fromIterable(names).map(byName::get).filter(Objects::nonNull);
        });
    }

    private static <T> Mono<T> withActor(Mono<T> mono, String... roleNames) {
        var authorities = Arrays.stream(roleNames)
                .map(roleName -> new SimpleGrantedAuthority(AuthorityUtils.ROLE_PREFIX + roleName))
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken("alice", "n/a", authorities);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private static Role role(String name, List<PolicyRule> rules, String... uiPermissions) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        if (uiPermissions.length > 0) {
            metadata.setAnnotations(Map.of(
                    Role.UI_PERMISSIONS_ANNO, JsonUtils.objectToJson(Arrays.asList(uiPermissions))));
        }
        role.setMetadata(metadata);
        role.setRules(rules);
        return role;
    }

    private static Role withDependencies(Role role, String... dependencies) {
        var annotations = new HashMap<String, String>();
        if (role.getMetadata().getAnnotations() != null) {
            annotations.putAll(role.getMetadata().getAnnotations());
        }
        annotations.put(
                Role.ROLE_DEPENDENCIES_ANNO, JsonUtils.objectToJson(Arrays.asList(dependencies)));
        role.getMetadata().setAnnotations(annotations);
        return role;
    }

    private static Role withAggregateLabel(Role role, String targetRoleName) {
        var labels = new HashMap<String, String>();
        if (role.getMetadata().getLabels() != null) {
            labels.putAll(role.getMetadata().getLabels());
        }
        labels.put(Role.ROLE_AGGREGATE_LABEL_PREFIX + targetRoleName, Boolean.TRUE.toString());
        role.getMetadata().setLabels(labels);
        return role;
    }

    private static PolicyRule rule(String[] apiGroups, String[] resources, String[] verbs) {
        return new PolicyRule(apiGroups, resources, null, null, verbs);
    }
}
