package cn.tolim.pudans.core.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UsageAccessResolverTest {

    private static final String USER_ID = "user-1";

    @Mock
    AiEndpointRequests requests;

    private UsageAccessResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new UsageAccessResolver(requests);
    }

    @Test
    void defaultsToTheSelfScopeForAnOrdinaryUser() {
        var request = request(null);
        authenticatedUser();

        StepVerifier.create(resolver.viewer(request).contextWrite(securityContext("ROLE_ai-user")))
                .assertNext(viewer -> {
                    assertThat(viewer.userId()).isEqualTo(USER_ID);
                    assertThat(viewer.all()).isFalse();
                    assertThat(viewer.admin()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void grantsTheAllScopeToAdministratorsOnly() {
        var request = request("all");
        authenticatedUser();

        StepVerifier.create(resolver.viewer(request).contextWrite(securityContext("ROLE_super-role")))
                .assertNext(viewer -> {
                    assertThat(viewer.all()).isTrue();
                    assertThat(viewer.admin()).isTrue();
                })
                .verifyComplete();
        StepVerifier.create(resolver.viewer(request).contextWrite(securityContext("ROLE_system-admin-role")))
                .assertNext(viewer -> {
                    assertThat(viewer.all()).isTrue();
                    assertThat(viewer.admin()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void ignoresTheAllScopeForNonAdministrators() {
        var request = request("all");
        authenticatedUser();

        StepVerifier.create(resolver.viewer(request).contextWrite(securityContext("ROLE_ai-user")))
                .assertNext(viewer -> {
                    assertThat(viewer.all()).isFalse();
                    assertThat(viewer.admin()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void rejectsAnUnknownScopeBeforeResolvingTheUser() {
        var request = request("team");

        assertThatThrownBy(() -> resolver.viewer(request))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.SCOPE_INVALID);
                });
        verify(requests, never()).userId(any(), anyString(), anyString());
    }

    @Test
    void propagatesTheAuthenticationFailure() {
        var request = mock(ServerRequest.class);
        when(request.queryParam("scope")).thenReturn(Optional.empty());
        when(requests.userId(any(), anyString(), anyString()))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.UNAUTHORIZED, AiUsageErrorCodes.UNAUTHENTICATED, "未认证。")));

        StepVerifier.create(resolver.viewer(request).contextWrite(securityContext()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                            assertThat(exception.getCode()).isEqualTo(AiUsageErrorCodes.UNAUTHENTICATED);
                        }))
                .verify();
    }

    private ServerRequest request(String scope) {
        var request = mock(ServerRequest.class);
        when(request.queryParam("scope")).thenReturn(Optional.ofNullable(scope));
        return request;
    }

    private void authenticatedUser() {
        when(requests.userId(any(), anyString(), anyString())).thenReturn(Mono.just(USER_ID));
    }

    private static reactor.util.context.Context securityContext(String... authorities) {
        var granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", granted);
        return ReactiveSecurityContextHolder.withAuthentication(authentication);
    }
}