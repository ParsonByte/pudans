package cn.tolim.pudans.security.authentication.login;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.security.authentication.login.UsernamePasswordAuthenticationManager;
import cn.tolim.pudans.core.audit.service.PluginInvocationAuditor;

@Slf4j
public class UsernamePasswordDelegatingAuthenticationManager implements ReactiveAuthenticationManager {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final ExtensionGetter extensionGetter;

    private final ReactiveAuthenticationManager defaultAuthenticationManager;

    private final PluginInvocationAuditor pluginInvocationAuditor;

    public UsernamePasswordDelegatingAuthenticationManager(
            ExtensionGetter extensionGetter,
            ReactiveAuthenticationManager defaultAuthenticationManager,
            PluginInvocationAuditor pluginInvocationAuditor) {
        this.extensionGetter = extensionGetter;
        this.defaultAuthenticationManager = defaultAuthenticationManager;
        this.pluginInvocationAuditor = pluginInvocationAuditor;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return extensionGetter
                .getEnabledExtensions(UsernamePasswordAuthenticationManager.class)
                .next()
                .flatMap(authenticationManager -> pluginInvocationAuditor
                        .audit(
                                "username-password-authentication-manager",
                                authenticationManager,
                                authentication.getName(),
                                Mono.defer(() -> authenticationManager.authenticate(authentication))
                                        .timeout(TIMEOUT))
                        .doOnError(t -> log.error(
                                "failed to authenticate with {}, fallback to default username password "
                                        + "authentication.",
                                authenticationManager.getClass(),
                                t))
                        .onErrorResume(
                                t -> !(t instanceof AuthenticationException) && !(t instanceof TimeoutException),
                                t -> Mono.empty()))
                .switchIfEmpty(Mono.defer(() -> defaultAuthenticationManager.authenticate(authentication)));
    }
}
