package cn.tolim.pudans.notification.support;

import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;

import java.time.LocalDateTime;

// Fills audit columns because the notification module can run without a JPA auditor.
public final class NotificationAudit {

    public static final String SYSTEM_ACTOR = "notification";

    private NotificationAudit() {
    }

    public static <T extends BaseEntity> T touch(T entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreateBy() == null) {
            entity.setCreateBy(SYSTEM_ACTOR);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        entity.setUpdateBy(SYSTEM_ACTOR);
        entity.setUpdateTime(now);
        return entity;
    }
}