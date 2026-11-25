package cn.tolim.pudans.infra.utils;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.exception.ApiException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.test.StepVerifier;

class ReactiveRequestIdentityTest {

    private final ReactiveRequestIdentity identity = new ReactiveRequestIdentity();

    @Test
    void resolvesTheAuthenticatedOwnerAndWorkspace() {
        var request = request("workspace-a");

        StepVerifier.create(identity
                        .resolve(request)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", List.of()))))
                .expectNext(new RequestIdentity("alice", "workspace-a"))
                .verifyComplete();
    }

    @Test
    void requiresAWorkspaceHeader() {
        var request = request(null);

        StepVerifier.create(identity.resolve(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("request-workspace-missing")))
                .verify();
    }

    @Test
    void requiresAnAuthenticatedOwner() {
        var request = request("workspace-a");

        StepVerifier.create(identity.resolve(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("request-owner-missing")))
                .verify();
    }

    @Test
    void exposesRoleNamesFromTheAuthenticationContext() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "alice",
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_role-a"),
                        new SimpleGrantedAuthority("SCOPE_scope-a")));

        StepVerifier.create(identity
                        .roles()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .expectNext(Set.of("role-a"))
                .verifyComplete();
    }

    private static ServerRequest request(String workspace) {
        var builder = MockServerHttpRequest.get("/");
        if (workspace != null) {
            builder.header(ReactiveRequestIdentity.WORKSPACE_HEADER, workspace);
        }
        return ServerRequest.create(
                MockServerWebExchange.builder(builder.build()).build(),
                List.of());
    }
}
