package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.security.TwoFactorVerifier;
import cn.tolim.pudans.core.audit.service.PluginInvocationAuditor;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwoFactorAuthenticationManager implements ReactiveAuthenticationManager {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final UserService userService;

    private final ExtensionGetter extensionGetter;

    private final PluginInvocationAuditor pluginInvocationAuditor;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof TwoFactorAuthenticationToken token)) {
            return Mono.error(new TwoFactorAuthException("Unsupported two-factor authentication token."));
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(TwoFactorAuthentication.class::isInstance)
                .map(TwoFactorAuthentication.class::cast)
                .map(TwoFactorAuthentication::getPrevious)
                .switchIfEmpty(Mono.error(() -> new TwoFactorAuthException("MFA Authentication required.")))
                .flatMap(previous -> verify(token, previous));
    }

    private Mono<Authentication> verify(TwoFactorAuthenticationToken token, Authentication previous) {
        if (!(previous.getPrincipal() instanceof PudansUserDetailsContract)) {
            return Mono.error(new TwoFactorAuthException("Invalid authentication principal."));
        }
        return userService
                .getUser(previous.getName())
                .flatMap(user -> resolveVerifier(token.getType())
                        .flatMap(verifier -> pluginInvocationAuditor.audit(
                                "two-factor-verifier",
                                verifier,
                                previous.getName(),
                                Mono.defer(() -> verifier.verify(user, token.getCode())).timeout(TIMEOUT))))
                .flatMap(valid -> {
                    if (!Boolean.TRUE.equals(valid)) {
                        return Mono.error(new TwoFactorAuthException("Invalid two-factor code."));
                    }
                    if (previous instanceof CredentialsContainer container) {
                        container.eraseCredentials();
                    }
                    return Mono.just(previous);
                });
    }

    private Mono<TwoFactorVerifier> resolveVerifier(String type) {
        return extensionGetter
                .getEnabledExtensions(TwoFactorVerifier.class)
                .filter(verifier -> verifier.getType() != null && verifier.getType().equalsIgnoreCase(type))
                .next()
                .switchIfEmpty(Mono.error(
                        () -> new TwoFactorAuthException("Unsupported two-factor type " + type)));
    }
}
