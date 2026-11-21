package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties.PasswordResetMethod;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Component
public class DefaultPasswordResetAvailabilityProviders implements PasswordResetAvailabilityProviders {

    private final SecurityProperties securityProperties;

    private final List<PasswordResetAvailabilityProvider> providers;

    // 缓存安全配置与有序提供者，保证结果稳定。
    public DefaultPasswordResetAvailabilityProviders(
            PudansProperties pudansProperties, ObjectProvider<PasswordResetAvailabilityProvider> providers) {
        this.securityProperties = pudansProperties.getSecurity();
        this.providers = providers.orderedStream().toList();
    }

    // 仅保留提供者支持且当前可用的重置方式。
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
