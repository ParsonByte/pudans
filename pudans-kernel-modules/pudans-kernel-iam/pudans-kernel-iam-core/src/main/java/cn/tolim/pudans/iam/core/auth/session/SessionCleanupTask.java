package cn.tolim.pudans.iam.core.auth.session;

import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.ticket.SsoTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCleanupTask.class);

    private final GlobalSessionService globalSessionService;
    private final SsoTicketService ssoTicketService;
    private final IamAuthProperties properties;

    public SessionCleanupTask(GlobalSessionService globalSessionService,
                              SsoTicketService ssoTicketService,
                              IamAuthProperties properties) {
        this.globalSessionService = globalSessionService;
        this.ssoTicketService = ssoTicketService;
        this.properties = properties;
    }

    // Removes expired global sessions, application session caches and SSO tickets
    @Scheduled(cron = "${pudans.iam.auth.session.cleanup-cron:0 */10 * * * *}")
    public void cleanup() {
        if (Boolean.FALSE.equals(properties.getSession().getCleanupEnabled())) {
            return;
        }
        try {
            int removedSessions = globalSessionService.cleanupExpired();
            int removedTickets = ssoTicketService.cleanupExpired();
            LOGGER.debug("IAM cleanup removed {} sessions and {} tickets", removedSessions, removedTickets);
        } catch (RuntimeException exception) {
            LOGGER.warn("IAM cleanup failed: {}", exception.getMessage());
        }
    }
}