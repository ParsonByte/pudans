package cn.tolim.pudans.notification.repository;

import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository
        extends JpaRepository<NotificationTemplateEntity, String>,
        JpaSpecificationExecutor<NotificationTemplateEntity> {

    Optional<NotificationTemplateEntity> findByCode(String code);

    Optional<NotificationTemplateEntity> findByCodeAndEnabledTrue(String code);

    boolean existsByCode(String code);
}