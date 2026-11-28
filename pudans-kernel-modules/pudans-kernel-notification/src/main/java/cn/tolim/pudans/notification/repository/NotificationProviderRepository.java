package cn.tolim.pudans.notification.repository;

import cn.tolim.pudans.notification.entity.NotificationProviderEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationProviderRepository
        extends JpaRepository<NotificationProviderEntity, String>,
        JpaSpecificationExecutor<NotificationProviderEntity> {

    Optional<NotificationProviderEntity> findByCode(String code);

    Optional<NotificationProviderEntity> findByCodeAndEnabledTrue(String code);

    List<NotificationProviderEntity> findByChannelAndEnabledTrueOrderByPriorityAsc(
            NotificationChannel channel);

    List<NotificationProviderEntity> findByChannelOrderByPriorityAsc(NotificationChannel channel);

    boolean existsByCode(String code);
}