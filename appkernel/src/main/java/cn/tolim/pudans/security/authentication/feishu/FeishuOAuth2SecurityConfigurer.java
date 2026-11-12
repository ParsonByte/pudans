package cn.tolim.pudans.security.authentication.feishu;

import cn.tolim.pudans.contract.security.LoginHandlerEnhancer;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@Order(10)
public class FeishuOAuth2SecurityConfigurer implements SecurityConfigurer {

    private final FeishuProperties properties;

    private final FeishuClient feishuClient;

    private final FeishuUserService feishuUserService;

    private final UserGroupService userGroupService;

    private final ServerSecurityContextRepository securityContextRepository;

    private final LoginHandlerEnhancer loginHandlerEnhancer;

    private final DomainEventPublisher domainEventPublisher;

    private final SecurityProperties securityProperties;

    public FeishuOAuth2SecurityConfigurer(
            FeishuProperties properties,
            FeishuClient feishuClient,
            FeishuUserService feishuUserService,
            UserGroupService userGroupService,
            ServerSecurityContextRepository securityContextRepository,
            LoginHandlerEnhancer loginHandlerEnhancer,
            DomainEventPublisher domainEventPublisher,
            SecurityProperties securityProperties) {
        this.properties = properties;
        this.feishuClient = feishuClient;
        this.feishuUserService = feishuUserService;
        this.userGroupService = userGroupService;
        this.securityContextRepository = securityContextRepository;
        this.loginHandlerEnhancer = loginHandlerEnhancer;
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
                userGroupService,
                securityContextRepository,
                loginHandlerEnhancer,
                domainEventPublisher,
                securityProperties);
        http.addFilterAt(filter, SecurityWebFiltersOrder.OAUTH2_AUTHORIZATION_CODE);
    }

}
