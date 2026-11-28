package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.configuration.NotificationProperties;
import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.enums.SendStatus;
import cn.tolim.pudans.notification.repository.NotificationSendRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// Picks due send records and retries them until they succeed or enter the dead letter queue.
@Service
public class NotificationRetryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryService.class);

    private final NotificationProperties properties;
    private final NotificationSendRecordRepository recordRepository;
    private final NotificationSendService sendService;

    public NotificationRetryService(NotificationProperties properties,
                                    NotificationSendRecordRepository recordRepository,
                                    NotificationSendService sendService) {
        this.properties = properties;
        this.recordRepository = recordRepository;
        this.sendService = sendService;
    }

    @Scheduled(fixedDelayString = "${pudans.notification.retry.scheduler-fixed-delay-ms:30000}")
    public void retryDueRecords() {
        if (!properties.getRetry().isSchedulerEnabled()) {
            return;
        }
        int batchSize = Math.max(1, properties.getRetry().getBatchSize());
        List<NotificationSendRecordEntity> due = recordRepository
                .findByStatusInAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                        List.of(SendStatus.PENDING, SendStatus.FAILED), LocalDateTime.now(),
                        PageRequest.of(0, batchSize));
        for (NotificationSendRecordEntity record : due) {
            try {
                sendService.retry(record.getId());
            } catch (Exception exception) {
                log.error("Notification retry failed for record {}: {}", record.getId(),
                        exception.getMessage());
            }
        }
    }
}