package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.dto.DeadLetterResponse;
import cn.tolim.pudans.notification.dto.NotificationStatsResponse;
import cn.tolim.pudans.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.notification.dto.TemplateResponse;
import cn.tolim.pudans.notification.entity.NotificationDeadLetterEntity;
import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.SendStatus;
import cn.tolim.pudans.notification.exception.NotificationResourceNotFoundException;
import cn.tolim.pudans.notification.repository.NotificationDeadLetterRepository;
import cn.tolim.pudans.notification.repository.NotificationSendRecordRepository;
import cn.tolim.pudans.notification.repository.NotificationTemplateRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationQueryService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationSendRecordRepository recordRepository;
    private final NotificationDeadLetterRepository deadLetterRepository;

    public NotificationQueryService(NotificationTemplateRepository templateRepository,
                                    NotificationSendRecordRepository recordRepository,
                                    NotificationDeadLetterRepository deadLetterRepository) {
        this.templateRepository = templateRepository;
        this.recordRepository = recordRepository;
        this.deadLetterRepository = deadLetterRepository;
    }

    @Transactional(readOnly = true)
    public Page<TemplateResponse> searchTemplates(NotificationChannel channel, Boolean enabled,
                                                  String keyword, Pageable pageable) {
        Specification<NotificationTemplateEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel != null) {
                predicates.add(builder.equal(root.get("channel"), channel));
            }
            if (enabled != null) {
                predicates.add(builder.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(specification, pageable).map(TemplateResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SendNotificationResponse> searchSendRecords(SendStatus status,
                                                            NotificationChannel channel,
                                                            String providerCode, String recipient,
                                                            String requestId, String templateCode,
                                                            Pageable pageable) {
        Specification<NotificationSendRecordEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (channel != null) {
                predicates.add(builder.equal(root.get("channel"), channel));
            }
            if (providerCode != null && !providerCode.isBlank()) {
                predicates.add(builder.equal(root.get("providerCode"), providerCode.trim()));
            }
            if (recipient != null && !recipient.isBlank()) {
                predicates.add(builder.equal(root.get("recipient"), recipient.trim()));
            }
            if (requestId != null && !requestId.isBlank()) {
                predicates.add(builder.equal(root.get("requestId"), requestId.trim()));
            }
            if (templateCode != null && !templateCode.isBlank()) {
                predicates.add(builder.equal(root.get("templateCode"), templateCode.trim()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return recordRepository.findAll(specification, pageable)
                .map(SendNotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<DeadLetterResponse> searchDeadLetters(NotificationChannel channel, Boolean replayed,
                                                      Pageable pageable) {
        Specification<NotificationDeadLetterEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel != null) {
                predicates.add(builder.equal(root.get("channel"), channel));
            }
            if (replayed != null) {
                predicates.add(builder.equal(root.get("replayed"), replayed));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return deadLetterRepository.findAll(specification, pageable).map(DeadLetterResponse::from);
    }

    @Transactional(readOnly = true)
    public SendNotificationResponse getSendRecord(String id) {
        return SendNotificationResponse.from(recordRepository.findById(id)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification send record not found: " + id)));
    }

    @Transactional(readOnly = true)
    public DeadLetterResponse getDeadLetter(String id) {
        return DeadLetterResponse.from(deadLetterRepository.findById(id)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification dead letter not found: " + id)));
    }

    @Transactional(readOnly = true)
    public NotificationStatsResponse stats() {
        Map<SendStatus, Long> counts = new EnumMap<>(SendStatus.class);
        for (SendStatus status : SendStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : recordRepository.countGroupedByStatus()) {
            if (row[0] != null && row[1] != null) {
                counts.put((SendStatus) row[0], ((Number) row[1]).longValue());
            }
        }
        return new NotificationStatsResponse(counts, deadLetterRepository.countByReplayedFalse());
    }
}