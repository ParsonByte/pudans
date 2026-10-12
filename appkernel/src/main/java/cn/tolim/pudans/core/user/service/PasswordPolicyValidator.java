package cn.tolim.pudans.core.user.service;

import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.security.PasswordPolicy;
import cn.tolim.pudans.core.audit.service.PluginInvocationAuditor;
import cn.tolim.pudans.infra.exception.PasswordPolicyViolationException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final ExtensionGetter extensionGetter;

    private final PluginInvocationAuditor pluginInvocationAuditor;

    public Mono<Void> validate(String username, String rawPassword) {
        return extensionGetter
                .getEnabledExtension(PasswordPolicy.class)
                .flatMap(policy -> pluginInvocationAuditor.audit(
                        "password-policy",
                        policy,
                        username,
                        Mono.defer(() -> policy.validate(username, rawPassword)).timeout(TIMEOUT)))
                .onErrorMap(this::mapError)
                .then();
    }

    private Throwable mapError(Throwable error) {
        if (error instanceof ResponseStatusException responseStatusException) {
            return responseStatusException;
        }
        return new PasswordPolicyViolationException("The password does not satisfy the password policy", error);
    }
}
