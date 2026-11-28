package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.repository.AuditRepository;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener implements ApplicationListener<AuditEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditRepository auditRepository;

    public AuditEventListener(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public void onApplicationEvent(@NonNull AuditEvent auditEvent) {
        AuditEntity entity = null;
        try {
            entity = AuditRecordFactory.create(auditEvent);
            auditRepository.save(entity);
        } catch (Exception e) {
            // Audit persistence must never break the business operation that published the event
            logger.error("Failed to persist audit event: {}",
                    entity == null ? auditEvent.getEvent() : JSONObject.toJSONString(entity), e);
        }
    }
}