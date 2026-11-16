package cn.tolim.pudans.security.authentication.feishu;

import cn.tolim.pudans.security.authentication.ExternalLoginCompletionService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@Order(10)
public class FeishuOAuth2SecurityConfigurer implements SecurityConfigurer {

    private final FeishuProperties properties;

    private final FeishuClient feishuClient;

    private final FeishuUserService feishuUserService;

    private final ExternalLoginCompletionService externalLoginCompletionService;

    private final DomainEventPublisher domainEventPublisher;

    private final SecurityProperties securityProperties;

    public FeishuOAuth2SecurityConfigurer(
            FeishuProperties properties,
            FeishuClient feishuClient,
            FeishuUserService feishuUserService,
            ExternalLoginCompletionService externalLoginCompletionService,
            DomainEventPublisher domainEventPublisher,
            SecurityProperties securityProperties) {
        this.properties = properties;
        this.feishuClient = feishuClient;
        this.feishuUserService = feishuUserService;
        this.externalLoginCompletionService = externalLoginCompletionService;
        this.domainEventPublisher = domainEventPublisher;
        this.securityProperties = securityProperties;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        if (!properties.isEnabled()) {
            log.info("Feishu login is disabled; authorization and callback requests will redirect to /auth with an error.");
        }
        var filter = new FeishuOAuth2LoginWebFilter(
                properties,
                feishuClient,
                feishuUserService,
                externalLoginCompletionService,
                domainEventPublisher,
                securityProperties);
        http.addFilterAt(filter, SecurityWebFiltersOrder.OAUTH2_AUTHORIZATION_CODE);
    }

}
