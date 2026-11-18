package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.Role.PolicyRule;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.rbac.service.PermissionCatalogService;
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
    RoleService roleService;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    PermissionCatalogService permissionCatalogService;

    private RolePermissionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RolePermissionPolicy(roleService, client, permissionCatalogService);
        lenient()
                .when(permissionCatalogService.expand(anyList()))
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

    private static PolicyRule rule(String[] apiGroups, String[] resources, String[] verbs) {
        return new PolicyRule(apiGroups, resources, null, null, verbs);
    }
}
