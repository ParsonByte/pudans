
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.AuditEventPublish;
import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import com.google.common.collect.Lists;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.util.Assert;

import static cn.tolim.pudans.audit.event.type.EventType.APP_SLO;
import static cn.tolim.pudans.audit.event.type.EventType.APP_SSO;


public class JwtAuthenticationFailureEventListener implements
        ApplicationListener<AbstractAuthenticationFailureEvent> {


    private final AuditEventPublish auditEventPublish;

    public JwtAuthenticationFailureEventListener(AuditEventPublish auditEventPublish) {
        Assert.notNull(auditEventPublish, "auditEventPublish 不能为空");
        this.auditEventPublish = auditEventPublish;
    }

    @Override
    public void onApplicationEvent(@NonNull AbstractAuthenticationFailureEvent event) {

        if (event.getAuthentication() instanceof JwtLoginAuthenticationToken authentication) {
            JwtProtocolConfig config = authentication.getConfig();
            auditEventPublish.publish(APP_SSO, authentication, EventStatus.FAIL,
                    Lists.newArrayList(Target.builder().id(config.getAppId())
                            .type(TargetType.APPLICATION).name(config.getAppName()).build()));
        }

        if (event.getAuthentication() instanceof JwtLogoutAuthenticationToken authentication) {
            JwtProtocolConfig config = authentication.getConfig();
            auditEventPublish.publish(APP_SLO, authentication, EventStatus.FAIL,
                    Lists.newArrayList(Target.builder().id(config.getAppId())
                            .type(TargetType.APPLICATION).name(config.getAppName()).build()));
        }
    }
}
