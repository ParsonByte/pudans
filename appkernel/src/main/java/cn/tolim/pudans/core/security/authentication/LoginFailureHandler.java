package cn.tolim.pudans.core.security.authentication;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static cn.tolim.pudans.core.infra.exception.Exceptions.createErrorResponse;

import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.infra.utils.IpAddressUtils;
import cn.tolim.pudans.core.security.authentication.exception.TooManyRequestsException;
import cn.tolim.pudans.core.security.authentication.login.LoginAuthenticationConverter;

public class LoginFailureHandler implements ServerAuthenticationFailureHandler {

    private final String loginMethod;

    private final ServerResponse.Context context;

    private final LoginFlowHandler loginFlowHandler;

    private final DomainEventPublisher domainEventPublisher;

    public LoginFailureHandler(
            String loginMethod,
            ServerResponse.Context context,
            LoginFlowHandler loginFlowHandler,
            DomainEventPublisher domainEventPublisher) {
        this.loginMethod = loginMethod;
        this.context = context;
        this.loginFlowHandler = loginFlowHandler;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        var exchange = webFilterExchange.getExchange();
        exchange.getAttributes().put(AuditLog.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditLog.RESULT_ATTRIBUTE, "DENIED");
        exchange.getAttributes().put(AuditLog.CONTEXT_ATTRIBUTE, exception.getClass().getSimpleName());
        publishLoginFailure(exchange, exception);
        return loginFlowHandler
                .onLoginFailure(exchange, exception)
                .then(handleAuthenticationException(exception, exchange));
    }

    private void publishLoginFailure(ServerWebExchange exchange, AuthenticationException exception) {
        var attempted =
                (String) exchange.getAttributes().get(LoginAuthenticationConverter.ATTEMPTED_USERNAME_ATTRIBUTE);
        if (attempted != null && !attempted.isBlank()) {
            exchange.getAttributes().put(AuditLog.USERNAME_ATTRIBUTE, attempted);
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", attempted == null ? "anonymous" : attempted);
        payload.put("method", loginMethod);
        payload.put("reason", exception.getClass().getSimpleName());
        payload.put("ip", IpAddressUtils.getClientIp(exchange.getRequest()));
        domainEventPublisher.publish(
                "user.login.failed",
                payload,
                (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE));
    }

    private Mono<Void> handleAuthenticationException(Throwable exception, ServerWebExchange exchange) {
        var status = exception instanceof TooManyRequestsException ? HttpStatus.TOO_MANY_REQUESTS : UNAUTHORIZED;
        var errorResponse = createErrorResponse(exception, status, exchange);
        return writeErrorResponse(errorResponse, exchange);
    }

    private Mono<Void> writeErrorResponse(ErrorResponse errorResponse, ServerWebExchange exchange) {
        return ServerResponse.status(errorResponse.getStatusCode())
                .contentType(APPLICATION_JSON)
                .bodyValue(errorResponse.getBody())
                .flatMap(response -> response.writeTo(exchange, context));
    }
}
