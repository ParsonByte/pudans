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
import org.springframework.web.reactive.function.server.ServerResponse;


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

    private final ServerResponse.Context context;

    public FeishuOAuth2SecurityConfigurer(
            FeishuProperties properties,
            FeishuClient feishuClient,
            FeishuUserService feishuUserService,
            ExternalLoginCompletionService externalLoginCompletionService,
            DomainEventPublisher domainEventPublisher,
            SecurityProperties securityProperties,
            ServerResponse.Context context) {
        this.properties = properties;
        this.feishuClient = feishuClient;
        this.feishuUserService = feishuUserService;
        this.externalLoginCompletionService = externalLoginCompletionService;
        this.domainEventPublisher = domainEventPublisher;
        this.securityProperties = securityProperties;
        this.context = context;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        if (!properties.isEnabled()) {
            log.info("飞书登录已禁用；授权和回调请求将返回 JSON 错误。");
        }
        var filter = new FeishuOAuth2LoginWebFilter(
                properties,
                feishuClient,
                feishuUserService,
                externalLoginCompletionService,
                domainEventPublisher,
                securityProperties,
                context);
        http.addFilterAt(filter, SecurityWebFiltersOrder.OAUTH2_AUTHORIZATION_CODE);
    }

}
