package cn.tolim.pudans.core.security.authentication.sudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.extension.ApiGroups;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.ContextView;

@ExtendWith(MockitoExtension.class)
class SudoModeWebFilterTest {

    @Mock
    SudoChallenges sudoChallenges;

    @Mock
    ServerResponse.Context context;

    private SudoModeWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SudoModeWebFilter(sudoChallenges, context);
    }

    @Test
    void tokenAuthenticationMustConfirmSudoBeforeChangingThePassword() {
        var exchange = exchange(MockServerHttpRequest.put(ApiGroups.API_PREFIX + "/users/-/password"));
        var chain = new RecordingChain();
        when(sudoChallenges.requireSudo(exchange, "alice"))
                .thenReturn(Mono.error(new SudoRequiredException(List.of("TOTP"))));

        StepVerifier.create(filter.filter(exchange, chain).contextWrite(authentication("alice")))
                .expectError(SudoRequiredException.class)
                .verify();

        assertThat(chain.called()).isFalse();
    }

    @Test
    void sensitiveRequestPassesAfterSudoIsActive() {
        var exchange = exchange(MockServerHttpRequest.delete(ApiGroups.API_PREFIX + "/personalaccesstokens/pat-1"));
        var chain = new RecordingChain();
        when(sudoChallenges.requireSudo(exchange, "alice")).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain).contextWrite(authentication("alice")))
                .verifyComplete();

        assertThat(chain.called()).isTrue();
    }

    @Test
    void anonymousAuthenticationIsNotAskedForSudo() {
        var exchange = exchange(MockServerHttpRequest.put(ApiGroups.API_PREFIX + "/users/-/password"));
        var chain = new RecordingChain();
        var anonymous = new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(anonymous)))
                .verifyComplete();

        assertThat(chain.called()).isTrue();
        verifyNoInteractions(sudoChallenges);
    }

    @Test
    void requestsOutsideSensitivePathsAreNotEnforced() {
        var exchange = exchange(MockServerHttpRequest.get(ApiGroups.API_PREFIX + "/users"));
        var chain = new RecordingChain();

        StepVerifier.create(filter.filter(exchange, chain).contextWrite(authentication("alice")))
                .verifyComplete();

        assertThat(chain.called()).isTrue();
        verifyNoInteractions(sudoChallenges);
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request.build());
    }

    private static ContextView authentication(String username) {
        var authentication = new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority("ROLE_authenticated")));
        return ReactiveSecurityContextHolder.withAuthentication(authentication);
    }

    private static final class RecordingChain implements WebFilterChain {

        private final AtomicBoolean called = new AtomicBoolean();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            called.set(true);
            return Mono.empty();
        }

        boolean called() {
            return called.get();
        }
    }
}
