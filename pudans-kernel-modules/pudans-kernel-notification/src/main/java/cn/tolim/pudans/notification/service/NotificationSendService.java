package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.notification.entity.NotificationDeadLetterEntity;
import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.SendStatus;
import cn.tolim.pudans.notification.exception.NotificationResourceNotFoundException;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import cn.tolim.pudans.notification.repository.NotificationDeadLetterRepository;
import cn.tolim.pudans.notification.repository.NotificationSendRecordRepository;
import cn.tolim.pudans.notification.sender.NotificationMessage;
import cn.tolim.pudans.notification.sender.SendResult;
import cn.tolim.pudans.notification.support.NotificationAudit;
import cn.tolim.pudans.notification.support.NotificationJson;
import cn.tolim.pudans.notification.template.NotificationTemplateRenderer;
import cn.tolim.pudans.notification.template.RenderedNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationSendService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSendService.class);

    private final NotificationSendRecordRepository recordRepository;
    private final NotificationDeadLetterRepository deadLetterRepository;
    private final NotificationTemplateService templateService;
    private final NotificationTemplateRenderer renderer;
    private final NotificationProviderSelector selector;
    private final NotificationRetryPolicy retryPolicy;

    public NotificationSendService(NotificationSendRecordRepository recordRepository,
                                   NotificationDeadLetterRepository deadLetterRepository,
                                   NotificationTemplateService templateService,
                                   NotificationTemplateRenderer renderer,
                                   NotificationProviderSelector selector,
                                   NotificationRetryPolicy retryPolicy) {
        this.recordRepository = recordRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.templateService = templateService;
        this.renderer = renderer;
        this.selector = selector;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public SendNotificationResponse send(SendNotificationRequest request) {
        validateRequest(request);
        if (request.requestId() != null && !request.requestId().isBlank()) {
            Optional<NotificationSendRecordEntity> existing = recordRepository
                    .findByRequestId(request.requestId());
            if (existing.isPresent()) {
                return SendNotificationResponse.from(existing.get());
            }
        }
        NotificationTemplateEntity template = resolveTemplate(request.templateCode());
        NotificationChannel channel = resolveChannel(request, template);
        Map<String, String> parameters = request.parameters() == null ? Map.of()
                : request.parameters();
        String subject = request.subject();
        String content = request.content();
        if (template != null) {
            RenderedNotification rendered = renderer.render(template, parameters);
            subject = subject == null ? rendered.subject() : subject;
            content = content == null ? rendered.content() : content;
        }
        String providerTemplateCode = firstNonBlank(request.providerTemplateCode(),
                template == null ? null : template.getProviderTemplateCode());
        validateContent(channel, content, providerTemplateCode);
        NotificationProviderSelector.Selection selection = selector.select(channel,
                request.providerCode());
        NotificationSendRecordEntity record = new NotificationSendRecordEntity();
        record.setRequestId(blankToNull(request.requestId()));
        record.setTemplateCode(template == null ? null : template.getCode());
        record.setProviderTemplateCode(providerTemplateCode);
        record.setChannel(channel);
        record.setProviderCode(selection.provider().getCode());
        record.setProviderType(selection.provider().getProvider());
        record.setRecipient(request.recipient().trim());
        record.setSubject(subject);
        record.setContent(content);
        record.setParameters(NotificationJson.writeMap(parameters));
        record.setTemplateParameters(NotificationJson.writeList(request.templateParameters()));
        record.setStatus(SendStatus.PENDING);
        record.setAttempts(0);
        record.setMaxAttempts(retryPolicy.resolveMaxAttempts(request.maxAttempts()));
        record = recordRepository.save(NotificationAudit.touch(record));
        if (Boolean.TRUE.equals(request.async())) {
            record.setNextRetryAt(LocalDateTime.now());
            return SendNotificationResponse.from(recordRepository.save(NotificationAudit.touch(record)));
        }
        return deliver(record, selection, buildMessage(record));
    }

    @Transactional
    public SendNotificationResponse retry(String id) {
        NotificationSendRecordEntity record = recordRepository.findLockedById(id)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification send record not found: " + id));
        if (SendStatus.SUCCESS.equals(record.getStatus())) {
            return SendNotificationResponse.from(record);
        }
        if (SendStatus.DEAD_LETTER.equals(record.getStatus())) {
            throw new NotificationValidationException(
                    "Send record is in the dead letter queue; replay it instead");
        }
        NotificationProviderSelector.Selection selection;
        try {
            selection = selector.selectForRecord(record);
        } catch (RuntimeException exception) {
            record.setAttempts((record.getAttempts() == null ? 0 : record.getAttempts()) + 1);
            return applyFailure(record, exception.getMessage(), null);
        }
        return deliver(record, selection, buildMessage(record));
    }

    @Transactional
    public SendNotificationResponse replayDeadLetter(String deadLetterId) {
        NotificationDeadLetterEntity deadLetter = deadLetterRepository.findById(deadLetterId)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification dead letter not found: " + deadLetterId));
        if (Boolean.TRUE.equals(deadLetter.getReplayed())) {
            throw new NotificationValidationException("Notification dead letter was already replayed");
        }
        NotificationSendRecordEntity record = recordRepository
                .findLockedById(deadLetter.getRecordId())
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification send record not found: " + deadLetter.getRecordId()));
        record.setStatus(SendStatus.PENDING);
        record.setAttempts(0);
        record.setNextRetryAt(LocalDateTime.now());
        record.setLastError(null);
        record.setProviderMessageId(null);
        record.setResponseBody(null);
        recordRepository.save(NotificationAudit.touch(record));
        deadLetter.setReplayed(Boolean.TRUE);
        deadLetter.setReplayedAt(LocalDateTime.now());
        deadLetterRepository.save(NotificationAudit.touch(deadLetter));
        return retry(record.getId());
    }

    @Transactional(readOnly = true)
    public SendNotificationResponse get(String id) {
        return SendNotificationResponse.from(recordRepository.findById(id)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification send record not found: " + id)));
    }

    private SendNotificationResponse deliver(NotificationSendRecordEntity record,
                                             NotificationProviderSelector.Selection selection,
                                             NotificationMessage message) {
        int attempts = (record.getAttempts() == null ? 0 : record.getAttempts()) + 1;
        record.setAttempts(attempts);
        record.setProviderCode(selection.provider().getCode());
        record.setProviderType(selection.provider().getProvider());
        SendResult result;
        try {
            result = selection.sender().send(message, selection.config());
        } catch (Exception exception) {
            log.error("Notification delivery failed for record {}: {}", record.getId(),
                    exception.getMessage());
            result = SendResult.failure(exception.getMessage());
        }
        if (result.success()) {
            record.setStatus(SendStatus.SUCCESS);
            record.setProviderMessageId(result.providerMessageId());
            record.setResponseBody(result.response());
            record.setLastError(null);
            record.setNextRetryAt(null);
            record.setSentAt(LocalDateTime.now());
            return SendNotificationResponse.from(recordRepository.save(NotificationAudit.touch(record)));
        }
        return applyFailure(record, result.error(), result.response());
    }

    private SendNotificationResponse applyFailure(NotificationSendRecordEntity record, String error,
                                                  String response) {
        int attempts = record.getAttempts() == null ? 0 : record.getAttempts();
        int maxAttempts = record.getMaxAttempts() == null
                ? retryPolicy.resolveMaxAttempts(null) : record.getMaxAttempts();
        record.setProviderMessageId(null);
        record.setLastError(error);
        record.setResponseBody(response);
        if (retryPolicy.exhausted(attempts, maxAttempts)) {
            record.setStatus(SendStatus.DEAD_LETTER);
            record.setNextRetryAt(null);
            deadLetterRepository.save(NotificationAudit.touch(toDeadLetter(record)));
        } else {
            record.setStatus(SendStatus.FAILED);
            record.setNextRetryAt(retryPolicy.nextRetryAt(attempts));
        }
        return SendNotificationResponse.from(recordRepository.save(NotificationAudit.touch(record)));
    }

    private NotificationDeadLetterEntity toDeadLetter(NotificationSendRecordEntity record) {
        NotificationDeadLetterEntity deadLetter = new NotificationDeadLetterEntity();
        deadLetter.setRecordId(record.getId());
        deadLetter.setRequestId(record.getRequestId());
        deadLetter.setTemplateCode(record.getTemplateCode());
        deadLetter.setChannel(record.getChannel());
        deadLetter.setProviderCode(record.getProviderCode());
        deadLetter.setProviderType(record.getProviderType());
        deadLetter.setRecipient(record.getRecipient());
        deadLetter.setSubject(record.getSubject());
        deadLetter.setContent(record.getContent());
        deadLetter.setParameters(record.getParameters());
        deadLetter.setTemplateParameters(record.getTemplateParameters());
        deadLetter.setAttempts(record.getAttempts());
        deadLetter.setLastError(record.getLastError());
        deadLetter.setFailedAt(LocalDateTime.now());
        deadLetter.setReplayed(Boolean.FALSE);
        return deadLetter;
    }

    private NotificationMessage buildMessage(NotificationSendRecordEntity record) {
        return NotificationMessage.builder()
                .recipient(record.getRecipient())
                .subject(record.getSubject())
                .content(record.getContent())
                .providerTemplateCode(record.getProviderTemplateCode())
                .parameters(NotificationJson.readMap(record.getParameters()))
                .templateParameters(NotificationJson.readList(record.getTemplateParameters()))
                .build();
    }

    private NotificationTemplateEntity resolveTemplate(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return null;
        }
        return templateService.getEnabledByCode(templateCode);
    }

    private NotificationChannel resolveChannel(SendNotificationRequest request,
                                               NotificationTemplateEntity template) {
        if (request.channel() != null) {
            if (template != null && !template.getChannel().equals(request.channel())) {
                throw new NotificationValidationException(
                        "Template " + template.getCode() + " belongs to channel "
                                + template.getChannel());
            }
            return request.channel();
        }
        if (template != null) {
            return template.getChannel();
        }
        throw new NotificationValidationException("Notification channel is required");
    }

    private void validateRequest(SendNotificationRequest request) {
        if (request == null) {
            throw new NotificationValidationException("Notification request is required");
        }
        if (request.recipient() == null || request.recipient().isBlank()) {
            throw new NotificationValidationException("Notification recipient is required");
        }
    }

    private void validateContent(NotificationChannel channel, String content,
                                 String providerTemplateCode) {
        if (NotificationChannel.SMS.equals(channel)) {
            if (providerTemplateCode == null || providerTemplateCode.isBlank()) {
                throw new NotificationValidationException(
                        "SMS notifications require a provider template code");
            }
            return;
        }
        if (content == null || content.isBlank()) {
            throw new NotificationValidationException(
                    "Notification content is required for channel " + channel);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}