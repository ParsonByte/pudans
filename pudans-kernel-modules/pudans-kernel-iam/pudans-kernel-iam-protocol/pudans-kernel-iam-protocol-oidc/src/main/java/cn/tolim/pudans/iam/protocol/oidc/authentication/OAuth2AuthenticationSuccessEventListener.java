package cn.tolim.pudans.iam.protocol.oidc.authentication;

import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.AuditEventPublish;
import cn.tolim.pudans.audit.event.type.EventType;
import com.google.common.collect.Lists;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.util.Assert;

import java.security.Principal;
import java.util.Objects;

import static cn.tolim.pudans.iam.protocol.oidc.constant.OidcProtocolConstants.ID_TOKEN;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class OAuth2AuthenticationSuccessEventListener implements
        ApplicationListener<AuthenticationSuccessEvent> {

    private final AuditEventPublish auditEventPublish;
    private final OAuth2AuthorizationService authorizationService;

    public OAuth2AuthenticationSuccessEventListener(AuditEventPublish auditEventPublish,
                                                    OAuth2AuthorizationService authorizationService) {
        Assert.notNull(auditEventPublish, "auditEventPublish 不能为空");
        Assert.notNull(authorizationService, "authorizationService 不能为空");
        this.auditEventPublish = auditEventPublish;
        this.authorizationService = authorizationService;
    }

    // Handle an application event.
    // Parameter event the event to respond to
    @Override
    public void onApplicationEvent(@NonNull AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof OAuth2AccessTokenAuthenticationToken authorization){
            OAuth2Authorization token = authorizationService.findByToken(authorization.getAccessToken().getTokenValue(), OAuth2TokenType.ACCESS_TOKEN);
            if (!Objects.isNull(token) && !Objects.isNull(token.getAttribute(Principal.class.getName()))){
                Authentication principal = token.getAttribute(Principal.class.getName());
                if (!Objects.isNull(principal)) {
                    Target target = Target.builder().id(authorization.getRegisteredClient().getId()).type(TargetType.APPLICATION).name(authorization.getRegisteredClient().getClientName()).build();
                    auditEventPublish.publish(EventType.APP_SSO, principal, EventStatus.SUCCESS, Lists.newArrayList(target));
                }
            }
        }
        if (event.getAuthentication() instanceof OidcLogoutAuthenticationToken authorization) {
            OAuth2Authorization token = authorizationService.findByToken(authorization.getIdTokenHint(), ID_TOKEN);
            if (!Objects.isNull(token) && !Objects.isNull(token.getAttribute(Principal.class.getName()))){
                Authentication principal = token.getAttribute(Principal.class.getName());
                if (!Objects.isNull(principal)) {
                    Target target = Target.builder().id(token.getRegisteredClientId()).name(token.getPrincipalName()).type(TargetType.APPLICATION).build();
                    auditEventPublish.publish(EventType.APP_SLO, principal, EventStatus.SUCCESS, Lists.newArrayList(target));
                }
            }
        }
    }
}
