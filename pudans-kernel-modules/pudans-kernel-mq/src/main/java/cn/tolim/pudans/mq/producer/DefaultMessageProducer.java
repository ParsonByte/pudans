package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.mq.config.MessageCenterProperties;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.exception.MessageCenterException;
import cn.tolim.pudans.mq.model.MessagePublishRequest;
import cn.tolim.pudans.mq.model.MessagePublishResult;
import cn.tolim.pudans.mq.repository.MessageRepository;
import cn.tolim.pudans.mq.service.MessageMapper;
import cn.tolim.pudans.mq.util.MessageTexts;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class DefaultMessageProducer implements MessageProducer {

    private final MessageRepository messageRepository;

    private final MessageMapper messageMapper;

    private final MessageCenterProperties properties;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    public DefaultMessageProducer(MessageRepository messageRepository, MessageMapper messageMapper,
                                   MessageCenterProperties properties,
                                   ApplicationEventPublisher eventPublisher, Clock clock) {
        this.messageRepository = messageRepository;
        this.messageMapper = messageMapper;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MessagePublishResult publish(MessagePublishRequest request) {
        if (request == null) {
            throw MessageCenterException.badRequest("publish request is required");
        }
        String topic = MessageTexts.trimToNull(request.topic());
        if (!MessageTexts.isValidTopic(topic)) {
            throw MessageCenterException.badRequest("Invalid topic: " + request.topic());
        }
        String eventType = MessageTexts.trimToNull(request.eventType());
        if (eventType != null && eventType.length() > 128) {
            throw MessageCenterException.badRequest("eventType must not exceed 128 characters");
        }
        String producerName = MessageTexts.defaultIfBlank(request.producer(),
                properties.getDefaultProducer());
        if (producerName.length() > 100) {
            throw MessageCenterException.badRequest("producer must not exceed 100 characters");
        }
        String bizKey = MessageTexts.trimToNull(request.bizKey());
        if (bizKey != null && bizKey.length() > 128) {
            throw MessageCenterException.badRequest("bizKey must not exceed 128 characters");
        }
        if (request.payload() == null) {
            throw MessageCenterException.badRequest("payload is required");
        }
        if (bizKey != null) {
            Optional<MessageEntity> existing = messageRepository.findByBizKey(bizKey);
            if (existing.isPresent()) {
                MessageEntity message = existing.get();
                return new MessagePublishResult(message.getId(), message.getStatus(), true,
                        message.getCreateTime());
            }
        }
        Map<String, String> headers = request.headers() == null ? Map.of() : request.headers();
        if (headers.size() > properties.getMaxHeaderCount()) {
            throw MessageCenterException.badRequest("Message must not exceed "
                    + properties.getMaxHeaderCount() + " headers");
        }
        String payload = messageMapper.serializePayload(request.payload());
        if (payload.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
            throw MessageCenterException.badRequest("Message payload exceeds "
                    + properties.getMaxPayloadBytes() + " bytes");
        }
        int maxAttempts = request.maxAttempts() == null
                ? properties.getDefaultMaxAttempts() : request.maxAttempts();
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw MessageCenterException.badRequest("maxAttempts must be between 1 and 100");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MessageEntity message = new MessageEntity();
        message.setBizKey(bizKey);
        message.setTopic(topic);
        message.setEventType(eventType);
        message.setProducer(producerName);
        message.setPayload(payload);
        message.setHeaders(messageMapper.serializeHeaders(headers));
        message.setStatus(MessageStatus.PENDING);
        message.setAttempts(0);
        message.setMaxAttempts(maxAttempts);
        message.setNextAttemptAt(now);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        message.setCreateBy(message.getProducer());
        message.setUpdateBy(message.getProducer());
        messageRepository.save(message);
        eventPublisher.publishEvent(new MessageQueuedEvent(messageMapper.toEnvelope(message)));
        return new MessagePublishResult(message.getId(), message.getStatus(), false,
                message.getCreateTime());
    }
}