package cn.tolim.pudans.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.rbac.BuiltinEntityDefinitions;
import cn.tolim.pudans.core.rbac.service.TempRoleService;
import java.util.List;
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

    @Mock
    RoleService roleService;

    @Mock
    TempRoleService tempRoleService;

    @Mock
    AuditService auditService;

    @Test
    void normalRoleAllowsSelfEndpointsButDeniesManagement() {
        var selfRequest = request("users", "-", "self", null, "get");
        StepVerifier.create(resolve(BuiltinEntityDefinitions.normalRole(), "alice", selfRequest))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();

        var managementRequest = request("users", "bob", "", null, "get");
        StepVerifier.create(resolve(BuiltinEntityDefinitions.normalRole(), "alice", managementRequest))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isFalse())
                .verifyComplete();
    }

    @Test
    void systemAdminRoleAllowsManagementEndpoints() {
        var managementRequest = request("users", "bob", "", null, "get");

        StepVerifier.create(resolve(BuiltinEntityDefinitions.systemAdminRole(), "admin", managementRequest))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();
    }

    @Test
    void superRoleStillMatchesEveryResource() {
        var request = request("users", "bob", "", null, "delete");

        StepVerifier.create(resolve(BuiltinEntityDefinitions.superRole(), "root", request))
                .assertNext(visitor -> assertThat(visitor.isAllowed()).isTrue())
                .verifyComplete();
    }

    private Mono<AuthorizingVisitor> resolve(Role role, String username, RequestInfo requestInfo) {
        when(roleService.listDependenciesFlux(Set.of(role.getMetadata().getName())))
                .thenReturn(Flux.just(role));
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
