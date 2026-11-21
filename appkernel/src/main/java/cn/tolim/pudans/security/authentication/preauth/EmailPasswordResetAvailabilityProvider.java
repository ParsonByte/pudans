package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.properties.SecurityProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
public class EmailPasswordResetAvailabilityProvider implements PasswordResetAvailabilityProvider {

    // 邮箱方式始终可用，投递能力在发送时再校验。
    @Override
    public Mono<Boolean> isAvailable(SecurityProperties.PasswordResetMethod method) {

        return Mono.just(true);
    }

    // 仅声明处理邮箱方式，其余名称一律拒绝。
    @Override
    public boolean support(String name) {
        return "email".equals(name);
    }
}
