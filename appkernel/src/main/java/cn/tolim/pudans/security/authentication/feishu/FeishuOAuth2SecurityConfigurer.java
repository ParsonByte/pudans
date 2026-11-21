package cn.tolim.pudans.security.authentication.feishu;

import cn.tolim.pudans.security.authentication.ExternalLogins;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.FeishuAccounts;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;


@Component
@Order(10)
public class FeishuOAuth2SecurityConfigurer implements SecurityConfigurer {

    private final SecurityParameters securitySettings;

    private final FeishuClient feishuClient;

    private final FeishuAccounts feishuAccounts;

    private final ExternalLogins externalLogins;

    private final DomainEventPublisher domainEventPublisher;

    private final SecurityProperties securityProperties;

    private final ServerResponse.Context context;

    // 显式构造以按注册顺序注入过滤器所需的全部协作者
    public FeishuOAuth2SecurityConfigurer(
            SecurityParameters securitySettings,
            FeishuClient feishuClient,
            FeishuAccounts feishuAccounts,
            ExternalLogins externalLogins,
            DomainEventPublisher domainEventPublisher,
            SecurityProperties securityProperties,
            ServerResponse.Context context) {
        this.securitySettings = securitySettings;
        this.feishuClient = feishuClient;
        this.feishuAccounts = feishuAccounts;
        this.externalLogins = externalLogins;
        this.domainEventPublisher = domainEventPublisher;
        this.securityProperties = securityProperties;
        this.context = context;
    }

    // 在授权码过滤器位置插入飞书登录过滤器
    @Override
    public void configure(ServerHttpSecurity http) {
        var filter = new FeishuOAuth2LoginWebFilter(
                securitySettings,
                feishuClient,
                feishuAccounts,
                externalLogins,
                domainEventPublisher,
                securityProperties,
                context);
        http.addFilterAt(filter, SecurityWebFiltersOrder.OAUTH2_AUTHORIZATION_CODE);
    }

}
