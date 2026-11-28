package cn.tolim.pudans.iam.core.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AuthAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAuditService.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public AuthAuditService(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    // Records an authentication related audit event and publishes it to listeners
    // Parameter type: AuthAuditType
    // Parameter userId: String
    // Parameter userType: String
    // Parameter appCode: String
    // Parameter sid: String
    // Parameter ip: String
    // Parameter userAgent: String
    // Parameter success: boolean
    // Parameter reason: String
    public void record(AuthAuditType type, String userId, String userType, String appCode,
                       String sid, String ip, String userAgent, boolean success, String reason) {
        AuthAuditEvent event = new AuthAuditEvent(type, userId, userType, appCode, sid, ip,
                userAgent, success ? "SUCCESS" : "FAILURE", reason);
        LOGGER.info("PUDANS_AUTH_AUDIT type={} result={} userId={} userType={} appCode={} sid={} ip={} reason={}",
                type, event.getResult(), userId, userType, appCode, sid, ip, reason);
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Publish authentication audit event failed: {}", exception.getMessage());
        }
    }
}