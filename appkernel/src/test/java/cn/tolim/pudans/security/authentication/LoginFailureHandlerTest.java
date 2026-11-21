package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.security.authentication.exception.TooManyRequestsException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LoginFailureHandlerTest {

    @Mock
    LoginFlowHandler loginFlowHandler;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private ServerResponse.Context context;

    @BeforeEach
    void setUp() {
        context = mock(ServerResponse.Context.class);
        lenient().when(context.messageWriters()).thenReturn(ServerCodecConfigurer.create().getWriters());
        lenient().when(context.viewResolvers()).thenReturn(List.of());
    }

    @Test
    void rateLimitedLoginReturnsTooManyRequests() {
        when(loginFlowHandler.onLoginFailure(any(), any())).thenReturn(Mono.empty());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login").build());
        var handler = new LoginFailureHandler("local", context, loginFlowHandler, domainEventPublisher);
        WebFilterChain chain = ignored -> Mono.empty();

        StepVerifier.create(handler.onAuthenticationFailure(
                        new WebFilterExchange(exchange, chain), new TooManyRequestsException(null)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
