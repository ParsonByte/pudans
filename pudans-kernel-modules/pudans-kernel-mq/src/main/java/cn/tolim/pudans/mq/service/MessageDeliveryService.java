package cn.tolim.pudans.mq.service;

import cn.tolim.pudans.mq.config.MessageCenterProperties;
import cn.tolim.pudans.mq.consumer.MessageConsumer;
import cn.tolim.pudans.mq.consumer.MessageConsumerRegistry;
import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.entity.MessageDeliveryEntity;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.enums.CompensationStatus;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.enums.DeliveryStatus;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.exception.MessageCenterException;
import cn.tolim.pudans.mq.model.MessageEnvelope;
import cn.tolim.pudans.mq.repository.DeadLetterRepository;
import cn.tolim.pudans.mq.repository.MessageDeliveryRepository;
import cn.tolim.pudans.mq.repository.MessageRepository;
import cn.tolim.pudans.mq.util.MessageTexts;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageDeliveryService {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final MessageRepository messageRepository;

    private final MessageDeliveryRepository deliveryRepository;

    private final DeadLetterRepository deadLetterRepository;

    private final MessageConsumerRegistry consumerRegistry;

    private final RetryPolicy retryPolicy;

    private final MessageCenterProperties properties;

    private final MessageMapper messageMapper;

    private final Clock clock;

    public MessageDeliveryService(MessageRepository messageRepository,
                                  MessageDeliveryRepository deliveryRepository,
                                  DeadLetterRepository deadLetterRepository,
                                  MessageConsumerRegistry consumerRegistry,
                                  RetryPolicy retryPolicy,
                                  MessageCenterProperties properties,
                                  MessageMapper messageMapper,
                                  Clock clock) {
        this.messageRepository = messageRepository;
        this.deliveryRepository = deliveryRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.consumerRegistry = consumerRegistry;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.messageMapper = messageMapper;
        this.clock = clock;
    }

    public List<String> findDispatchableIds(LocalDateTime now) {
        int limit = Math.max(1, properties.getBatchSize());
        return messageRepository.findDispatchableIds(MessageStatus.PENDING, MessageStatus.RETRYING,
                now, PageRequest.of(0, limit));
    }

    // Returns DISPATCHING messages and DELIVERING deliveries stuck after a node crash
    @Transactional
    public int recoverStuckMessages() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.minus(Duration.ofMillis(
                Math.max(1L, properties.getDispatchTimeoutMs())));
        deliveryRepository.recoverStuckDeliveries(DeliveryStatus.RETRYING, DeliveryStatus.DELIVERING,
                now, threshold);
        return messageRepository.recoverStuck(MessageStatus.PENDING, MessageStatus.DISPATCHING,
                now, threshold);
    }

    @Transactional
    public DispatchOutcome dispatch(String messageId) {
        if (MessageTexts.isBlank(messageId)) {
            return DispatchOutcome.SKIPPED;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int claimed = messageRepository.claim(messageId, MessageStatus.DISPATCHING,
                MessageStatus.PENDING, MessageStatus.RETRYING, now);
        if (claimed == 0) {
            return DispatchOutcome.SKIPPED;
        }
        MessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> MessageCenterException.notFound("message", messageId));
        List<MessageConsumer> consumers = distinctConsumers(
                consumerRegistry.consumersFor(message.getTopic()));
        if (consumers.isEmpty()) {
            message.transitionTo(MessageStatus.DELIVERED);
            message.setDeliveredAt(now);
            message.setNextAttemptAt(null);
            message.setLastError(null);
            messageRepository.save(message);
            return DispatchOutcome.DELIVERED;
        }
        MessageEnvelope envelope = messageMapper.toEnvelope(message);
        List<MessageDeliveryEntity> deliveries = new ArrayList<>();
        List<DeadLetterEntity> newDeadLetters = new ArrayList<>();
        boolean anyFailure = false;
        String lastError = null;
        for (MessageConsumer consumer : consumers) {
            MessageDeliveryEntity delivery = deliveryRepository
                    .findByMessageIdAndConsumerGroup(messageId, consumer.consumerGroup())
                    .orElseGet(() -> deliveryRepository.save(newDelivery(message,
                            consumer.consumerGroup(), now)));
            if (delivery.getStatus() == DeliveryStatus.CONSUMED
                    || delivery.getStatus() == DeliveryStatus.CANCELLED) {
                continue;
            }
            delivery.markDelivering(now);
            try {
                consumer.onMessage(envelope);
                delivery.markConsumed(now);
            } catch (Exception exception) {
                anyFailure = true;
                lastError = errorMessage(exception);
                delivery.recordFailure(lastError, now);
                if (delivery.getStatus() == DeliveryStatus.RETRYING) {
                    delivery.setNextAttemptAt(retryPolicy.nextAttemptAt(now,
                            delivery.getAttempts()));
                } else if (delivery.getStatus() == DeliveryStatus.DEAD_LETTER) {
                    newDeadLetters.add(createDeadLetter(message, delivery, now));
                }
            }
            deliveries.add(delivery);
        }
        if (deliveries.isEmpty()) {
            message.transitionTo(MessageStatus.CONSUMED);
            message.setConsumedAt(now);
            message.setNextAttemptAt(null);
            message.setLastError(null);
            messageRepository.save(message);
            return DispatchOutcome.CONSUMED;
        }
        boolean anyDeadLetter = deliveries.stream()
                .anyMatch(delivery -> delivery.getStatus() == DeliveryStatus.DEAD_LETTER);
        boolean budgetExhausted = message.getAttempts() >= message.getMaxAttempts();
        if (anyFailure && (anyDeadLetter || budgetExhausted)) {
            for (MessageDeliveryEntity delivery : deliveries) {
                if (delivery.getStatus() == DeliveryStatus.RETRYING) {
                    delivery.markDeadLetter(now);
                    newDeadLetters.add(createDeadLetter(message, delivery, now));
                }
            }
            message.transitionTo(MessageStatus.DEAD_LETTER);
            message.setDeadLetterAt(now);
            message.setNextAttemptAt(null);
            message.setLastError(MessageTexts.truncate(lastError, MAX_ERROR_LENGTH));
        } else if (anyFailure) {
            message.transitionTo(MessageStatus.RETRYING);
            message.setNextAttemptAt(retryPolicy.nextAttemptAt(now, message.getAttempts()));
            message.setLastError(MessageTexts.truncate(lastError, MAX_ERROR_LENGTH));
        } else {
            message.transitionTo(MessageStatus.CONSUMED);
            message.setConsumedAt(now);
            message.setNextAttemptAt(null);
            message.setLastError(null);
        }
        messageRepository.save(message);
        deliveryRepository.saveAll(deliveries);
        if (!newDeadLetters.isEmpty()) {
            deadLetterRepository.saveAll(newDeadLetters);
        }
        if (!anyFailure) {
            return DispatchOutcome.CONSUMED;
        }
        return message.getStatus() == MessageStatus.DEAD_LETTER
                ? DispatchOutcome.DEAD_LETTER : DispatchOutcome.RETRYING;
    }

    private List<MessageConsumer> distinctConsumers(List<MessageConsumer> consumers) {
        Map<String, MessageConsumer> byGroup = new LinkedHashMap<>();
        for (MessageConsumer consumer : consumers) {
            byGroup.putIfAbsent(consumer.consumerGroup(), consumer);
        }
        return new ArrayList<>(byGroup.values());
    }

    private MessageDeliveryEntity newDelivery(MessageEntity message, String consumerGroup,
                                              LocalDateTime now) {
        MessageDeliveryEntity delivery = new MessageDeliveryEntity();
        delivery.setMessageId(message.getId());
        delivery.setConsumerGroup(consumerGroup);
        delivery.setTopic(message.getTopic());
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setMaxAttempts(message.getMaxAttempts());
        delivery.setNextAttemptAt(now);
        delivery.setCreateTime(now);
        delivery.setUpdateTime(now);
        return delivery;
    }

    private DeadLetterEntity createDeadLetter(MessageEntity message, MessageDeliveryEntity delivery,
                                              LocalDateTime now) {
        DeadLetterEntity deadLetter = new DeadLetterEntity();
        deadLetter.setMessageId(message.getId());
        deadLetter.setDeliveryId(delivery.getId());
        deadLetter.setTopic(message.getTopic());
        deadLetter.setEventType(message.getEventType());
        deadLetter.setProducer(message.getProducer());
        deadLetter.setConsumerGroup(delivery.getConsumerGroup());
        deadLetter.setPayload(message.getPayload());
        deadLetter.setHeaders(message.getHeaders());
        deadLetter.setAttempts(delivery.getAttempts());
        deadLetter.setLastError(delivery.getLastError());
        deadLetter.setStatus(DeadLetterStatus.PENDING);
        deadLetter.setDeadLetteredAt(now);
        deadLetter.setCompensationStatus(CompensationStatus.NONE);
        deadLetter.setCreateTime(now);
        deadLetter.setUpdateTime(now);
        return deadLetter;
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return MessageTexts.isBlank(message) ? exception.getClass().getName() : message;
    }
}