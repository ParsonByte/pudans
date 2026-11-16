package cn.tolim.pudans.security.authentication;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static cn.tolim.pudans.infra.exception.Exceptions.createErrorResponse;
import static cn.tolim.pudans.security.authentication.WebExchangeMatchers.ignoringMediaTypeAll;

import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.infra.utils.IpAddressUtils;
import cn.tolim.pudans.security.authentication.exception.TooManyRequestsException;
import cn.tolim.pudans.security.authentication.login.LoginAuthenticationConverter;

public class LoginFailureHandler implements ServerAuthenticationFailureHandler {

    private final String loginMethod;

    private final ServerResponse.Context context;

    private final MessageSource messageSource;

    private final LoginFlowHandler loginFlowHandler;

    private final DomainEventPublisher domainEventPublisher;

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    public LoginFailureHandler(
            String loginMethod,
            ServerResponse.Context context,
            MessageSource messageSource,
            LoginFlowHandler loginFlowHandler,
            DomainEventPublisher domainEventPublisher) {
        this.loginMethod = loginMethod;
        this.context = context;
        this.messageSource = messageSource;
        this.loginFlowHandler = loginFlowHandler;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        var exchange = webFilterExchange.getExchange();
        exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "DENIED");
        exchange.getAttributes().put(AuditService.CONTEXT_ATTRIBUTE, exception.getClass().getSimpleName());
        publishLoginFailure(exchange, exception);
        return loginFlowHandler
                .onLoginFailure(exchange, exception)
                .then(ignoringMediaTypeAll(APPLICATION_JSON).matches(exchange).flatMap(matchResult -> {
                    if (matchResult.isMatch()) {
                        return handleAuthenticationException(exception, exchange);
                    }
                    var location = buildRedirectLocation(exception);
                    return redirectStrategy.sendRedirect(exchange, location);
                }));
    }

    private void publishLoginFailure(ServerWebExchange exchange, AuthenticationException exception) {
        var attempted =
                (String) exchange.getAttributes().get(LoginAuthenticationConverter.ATTEMPTED_USERNAME_ATTRIBUTE);
        if (attempted != null && !attempted.isBlank()) {
            exchange.getAttributes().put(AuditService.USERNAME_ATTRIBUTE, attempted);
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", attempted == null ? "anonymous" : attempted);
        payload.put("method", loginMethod);
        payload.put("reason", exception.getClass().getSimpleName());
        payload.put("ip", IpAddressUtils.getClientIp(exchange.getRequest()));
        domainEventPublisher.publish(
                "user.login.failed",
                payload,
                (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE));
    }

    private URI buildRedirectLocation(AuthenticationException exception) {
        if (exception instanceof DisabledException) {
            return URI.create("/auth/?error=account-disabled&method=" + loginMethod);
        }
        if (exception instanceof BadCredentialsException) {
            return URI.create("/auth/?error=invalid-credential&method=" + loginMethod);
        }
        if (exception instanceof TooManyRequestsException) {
            return URI.create("/auth/?error=rate-limit-exceeded&method=" + loginMethod);
        }
        return URI.create("/auth/?error&method=" + loginMethod);
    }

    private Mono<Void> handleAuthenticationException(Throwable exception, ServerWebExchange exchange) {
        var errorResponse = createErrorResponse(exception, UNAUTHORIZED, exchange, messageSource);
        return writeErrorResponse(errorResponse, exchange);
    }

    private Mono<Void> writeErrorResponse(ErrorResponse errorResponse, ServerWebExchange exchange) {
        return ServerResponse.status(errorResponse.getStatusCode())
                .contentType(APPLICATION_JSON)
                .bodyValue(errorResponse.getBody())
                .flatMap(response -> response.writeTo(exchange, context));
    }
}
