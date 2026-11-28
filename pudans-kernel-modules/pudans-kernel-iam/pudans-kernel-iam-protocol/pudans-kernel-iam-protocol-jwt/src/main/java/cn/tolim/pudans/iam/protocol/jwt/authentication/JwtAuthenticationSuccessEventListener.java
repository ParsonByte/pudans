
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.AuditEventPublish;
import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import com.google.common.collect.Lists;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;

import static cn.tolim.pudans.audit.event.type.EventType.APP_SSO;


public class JwtAuthenticationSuccessEventListener implements
        ApplicationListener<AuthenticationSuccessEvent> {


    private final AuditEventPublish auditEventPublish;

    public JwtAuthenticationSuccessEventListener(AuditEventPublish auditEventPublish) {
        Assert.notNull(auditEventPublish, "auditEventPublish 不能为空");
        this.auditEventPublish = auditEventPublish;
    }

    @Override
    public void onApplicationEvent(@NonNull AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof JwtAuthenticationToken authentication){
            JwtProtocolConfig config = authentication.getConfig();
            auditEventPublish.publish(APP_SSO, (Authentication) authentication.getPrincipal(),EventStatus.SUCCESS,Lists.newArrayList(Target.builder().id(config.getAppId()).type(TargetType.APPLICATION).name(config.getAppName()).build()));
        }
    }
}
