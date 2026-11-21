package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.infra.utils.IpAddressUtils;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ExternalLogins {

    private final ReactiveUserDetailsService userDetailsService;

    private final ServerSecurityContextRepository securityContextRepository;

    private final LoginFlowHandler loginFlowHandler;

    private final DomainEventPublisher domainEventPublisher;

    public Mono<LoginCompletionResult> complete(ServerWebExchange exchange, String username, String method) {
        return userDetailsService
                .findByUsername(username)
                .switchIfEmpty(Mono.error(new LoginRejectedException("account_not_found")))
                .flatMap(userDetails ->
                        UserAccountStatusChecker.check(userDetails).thenReturn(userDetails))
                .onErrorMap(AuthenticationException.class, this::toRejection)
                .flatMap(userDetails -> complete(exchange, username, method, userDetails))
                .doOnNext(result -> {
                    exchange.getAttributes().put(AuditLog.USERNAME_ATTRIBUTE, username);
                    exchange.getAttributes().put(AuditLog.OPERATION_ATTRIBUTE, "LOGIN");
                    exchange.getAttributes()
                            .put(
                                    AuditLog.RESULT_ATTRIBUTE,
                                    result.twoFactorRequired() ? "CHALLENGE" : "SUCCESS");
                    exchange.getAttributes().put(AuditLog.CONTEXT_ATTRIBUTE, method);
                })
                .doOnError(
                        LoginRejectedException.class,
                        error -> {
                            exchange.getAttributes().put(AuditLog.USERNAME_ATTRIBUTE, username);
                            exchange.getAttributes().put(AuditLog.OPERATION_ATTRIBUTE, "LOGIN");
                            exchange.getAttributes().put(AuditLog.RESULT_ATTRIBUTE, "DENIED");
                            exchange.getAttributes().put(AuditLog.CONTEXT_ATTRIBUTE, error.getReason());
                            publishFailure(exchange, username, method, error.getReason());
                        });
    }

    private Mono<LoginCompletionResult> complete(
            ServerWebExchange exchange, String username, String method, UserDetails userDetails) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, userDetails.getAuthorities());
        var twoFactorRequired =
                userDetails instanceof PudansUserDetailsContract contract && contract.isTwoFactorAuthEnabled();
        var storedAuthentication =
                twoFactorRequired ? new TwoFactorAuthentication(authentication) : authentication;
        return securityContextRepository
                .save(exchange, new SecurityContextImpl(storedAuthentication))
                .then(Mono.defer(() -> twoFactorRequired
                        ? Mono.just(new LoginCompletionResult(true, username))
                        : loginFlowHandler
                                .onLoginSuccess(exchange, authentication)
                                .thenReturn(new LoginCompletionResult(false, username))));
    }

    private LoginRejectedException toRejection(AuthenticationException error) {
        if (error instanceof LoginRejectedException rejected) {
            return rejected;
        }
        var reason = switch (error) {
            case DisabledException ignored -> "account_disabled";
            case LockedException ignored -> "account_locked";
            case AccountExpiredException ignored -> "account_expired";
            case CredentialsExpiredException ignored -> "credentials_expired";
            case BadCredentialsException ignored -> "account_not_found";
            default -> "account_unavailable";
        };
        return new LoginRejectedException(reason, error);
    }

    private void publishFailure(ServerWebExchange exchange, String username, String method, String reason) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", username == null ? "anonymous" : username);
        payload.put("method", method);
        payload.put("reason", reason);
        payload.put("ip", IpAddressUtils.getClientIp(exchange.getRequest()));
        domainEventPublisher.publish(
                "user.login.failed",
                payload,
                (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE));
    }

    public record LoginCompletionResult(boolean twoFactorRequired, String username) {}
}
