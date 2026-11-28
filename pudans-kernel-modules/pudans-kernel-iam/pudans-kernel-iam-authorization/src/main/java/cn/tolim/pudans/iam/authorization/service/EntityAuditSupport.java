package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;

import java.time.LocalDateTime;


public final class EntityAuditSupport {

    private EntityAuditSupport() {
    }

    // Stamps creation audit fields of an entity
    // Parameter entity: BaseEntity
    public static void stampCreate(BaseEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        String actor = SecurityUtils.getCurrentUserId();
        if (entity.getCreateBy() == null || entity.getCreateBy().isBlank()) {
            entity.setCreateBy(actor);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        stampUpdate(entity);
    }

    // Stamps update audit fields of an entity
    // Parameter entity: BaseEntity
    public static void stampUpdate(BaseEntity entity) {
        String actor = SecurityUtils.getCurrentUserId();
        if (entity.getUpdateBy() == null || entity.getUpdateBy().isBlank()) {
            entity.setUpdateBy(actor);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
    }
}