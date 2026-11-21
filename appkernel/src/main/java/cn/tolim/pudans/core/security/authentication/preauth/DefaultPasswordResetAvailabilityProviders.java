package cn.tolim.pudans.core.security.authentication.preauth;

import cn.tolim.pudans.core.infra.properties.PudansProperties;
import cn.tolim.pudans.core.infra.properties.SecurityProperties;
import cn.tolim.pudans.core.infra.properties.SecurityProperties.PasswordResetMethod;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Component
public class DefaultPasswordResetAvailabilityProviders implements PasswordResetAvailabilityProviders {

    private final SecurityProperties securityProperties;

    private final List<PasswordResetAvailabilityProvider> providers;

    public DefaultPasswordResetAvailabilityProviders(
            PudansProperties pudansProperties, ObjectProvider<PasswordResetAvailabilityProvider> providers) {
        this.securityProperties = pudansProperties.getSecurity();
        this.providers = providers.orderedStream().toList();
    }

    @Override
    public Flux<PasswordResetMethod> getAvailableMethods() {
        return Flux.fromIterable(securityProperties.getPasswordResetMethods())
                .filterWhen(method -> providers.stream()
                        .filter(provider -> provider.support(method.getName()))
                        .findFirst()
                        .map(provider -> provider.isAvailable(method))
                        .orElseGet(() -> Mono.just(false)));
    }
}
