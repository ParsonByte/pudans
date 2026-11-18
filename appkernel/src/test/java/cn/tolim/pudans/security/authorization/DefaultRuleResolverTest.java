package cn.tolim.pudans.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;
import cn.tolim.pudans.core.rbac.service.TempRoleService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DefaultRuleResolverTest {

    private static final BuiltinRoleDefinitions BUILTIN_ROLES = new BuiltinRoleDefinitions();

    @Mock
    RoleService roleService;

    @Mock
    TempRoleService tempRoleService;

    @Mock
    AuditService auditService;

    @Test
    void normalRoleAllowsSelfEndpointsButDeniesManagement() {
        var selfRequest = request("users", "-", "self", null, "get");
        StepVerifier.create(resolve(builtinRole("normal-role"), "alice", selfRequest))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();

        var managementRequest = request("users", "bob", "", null, "get");
        StepVerifier.create(resolve(builtinRole("normal-role"), "alice", managementRequest))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isFalse())
                .verifyComplete();
    }

    @Test
    void normalRoleAllowsOwnAvatarAndAnnouncementState() {
        assertAllowed(normalRoleRequest("users", "-", "avatar", null, "create"));
        assertAllowed(normalRoleRequest("users", "-", "avatar", null, "delete"));
        assertAllowed(normalRoleRequest("users", "-", "announcement-state", null, "get"));
        assertAllowed(normalRoleRequest("users", "-", "announcement-state", "dismiss", "create"));
        assertAllowed(normalRoleRequest("avatars", "alice-1.png", "", null, "get"));
    }

    @Test
    void aRoleWithoutRulesCannotUseTheNewEndpoints() {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName("ruleless-role");
        role.setMetadata(metadata);
        role.setRules(List.of());
        StepVerifier.create(resolve(role, "alice", request("users", "-", "avatar", null, "create")))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isFalse())
                .verifyComplete();
        StepVerifier.create(resolve(role, "alice", request("users", "-", "announcement-state", null, "get")))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isFalse())
                .verifyComplete();
    }

    @Test
    void systemAdminRoleAllowsManagementEndpoints() {
        var managementRequest = request("users", "bob", "", null, "get");

        StepVerifier.create(resolve(builtinRole("system-admin-role"), "admin", managementRequest))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();
    }

    @Test
    void jwtAuthenticationDoesNotInheritTemporaryRoles() {
        var role = builtinRole("normal-role");
        when(roleService.listDependenciesFlux(Set.of(role.getMetadata().getName())))
                .thenReturn(Flux.fromIterable(withDependencies(role)));
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "alice")
                .build();
        var authentication = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role.getMetadata().getName())));

        StepVerifier.create(new DefaultRuleResolver(roleService, tempRoleService, auditService)
                        .visitRules(authentication, request("users", "-", "self", null, "get")))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();
        org.mockito.Mockito.verify(tempRoleService, org.mockito.Mockito.never())
                .activeRoleNameSet(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void superRoleStillMatchesEveryResource() {
        var request = request("users", "bob", "", null, "delete");

        StepVerifier.create(resolve(builtinRole("super-role"), "root", request))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();
    }

    private void assertAllowed(RequestInfo requestInfo) {
        StepVerifier.create(resolve(builtinRole("normal-role"), "alice", requestInfo))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();
    }

    private static Role builtinRole(String name) {
        return BUILTIN_ROLES.role(name).orElseThrow();
    }

    private static List<Role> withDependencies(Role role) {
        var roles = new LinkedHashMap<String, Role>();
        collectDependencies(role, roles);
        return List.copyOf(roles.values());
    }

    private static void collectDependencies(Role role, Map<String, Role> collected) {
        if (collected.putIfAbsent(role.getMetadata().getName(), role) != null) {
            return;
        }
        var raw = MetadataUtil.nullSafeAnnotations(role).get(Role.ROLE_DEPENDENCIES_ANNO);
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (var dependency : JsonUtils.jsonToObject(raw, new TypeReference<List<String>>() {})) {
            BUILTIN_ROLES.allRoles().stream()
                    .filter(candidate -> dependency.equals(candidate.getMetadata().getName()))
                    .findFirst()
                    .ifPresent(candidate -> collectDependencies(candidate, collected));
        }
    }

    private static RequestInfo normalRoleRequest(
            String resource, String name, String subresource, String subName, String verb) {
        return request(resource, name, subresource, subName, verb);
    }

    private Mono<AuthorizingVisitor> resolve(Role role, String username, RequestInfo requestInfo) {
        when(roleService.listDependenciesFlux(Set.of(role.getMetadata().getName())))
                .thenReturn(Flux.fromIterable(withDependencies(role)));
        when(tempRoleService.activeRoleNameSet(username)).thenReturn(Mono.just(Set.of()));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.getMetadata().getName())));
        return new DefaultRuleResolver(roleService, tempRoleService, auditService)
                .visitRules(authentication, requestInfo);
    }

    private static RequestInfo request(
            String resource, String name, String subresource, String subName, String verb) {
        var path = ApiGroups.API_PREFIX + "/" + resource;
        if (!name.isEmpty()) {
            path += "/" + name;
        }
        if (!subresource.isEmpty()) {
            path += "/" + subresource;
        }
        if (subName != null && !subName.isEmpty()) {
            path += "/" + subName;
        }
        var parts = new java.util.ArrayList<String>();
        parts.add(resource);
        if (!name.isEmpty()) {
            parts.add(name);
        }
        if (!subresource.isEmpty()) {
            parts.add(subresource);
        }
        if (subName != null && !subName.isEmpty()) {
            parts.add(subName);
        }
        return new RequestInfo(
                true,
                path,
                null,
                "",
                verb,
                "apis",
                ApiGroups.API,
                "v1alpha1",
                resource,
                name,
                subresource,
                subName,
                parts.toArray(String[]::new));
    }
}
