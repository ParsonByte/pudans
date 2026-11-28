package cn.tolim.pudans.mq.service;

import cn.tolim.pudans.mq.consumer.MessageConsumerRegistry;
import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.entity.MessageDeliveryEntity;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.enums.DeliveryStatus;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.exception.MessageCenterException;
import cn.tolim.pudans.mq.model.ConsumerDescriptor;
import cn.tolim.pudans.mq.model.DeadLetterQuery;
import cn.tolim.pudans.mq.model.DeadLetterResponse;
import cn.tolim.pudans.mq.model.DeliveryResponse;
import cn.tolim.pudans.mq.model.MessageDetailResponse;
import cn.tolim.pudans.mq.model.MessageQuery;
import cn.tolim.pudans.mq.model.MessageResponse;
import cn.tolim.pudans.mq.model.MessageStatsResponse;
import cn.tolim.pudans.mq.model.PageResponse;
import cn.tolim.pudans.mq.producer.MessageQueuedEvent;
import cn.tolim.pudans.mq.repository.DeadLetterRepository;
import cn.tolim.pudans.mq.repository.MessageDeliveryRepository;
import cn.tolim.pudans.mq.repository.MessageRepository;
import cn.tolim.pudans.mq.repository.MessageSpecifications;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageCenterService {

    private final MessageRepository messageRepository;

    private final MessageDeliveryRepository deliveryRepository;

    private final DeadLetterRepository deadLetterRepository;

    private final MessageConsumerRegistry consumerRegistry;

    private final MessageMapper messageMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    public MessageCenterService(MessageRepository messageRepository,
                                MessageDeliveryRepository deliveryRepository,
                                DeadLetterRepository deadLetterRepository,
                                MessageConsumerRegistry consumerRegistry,
                                MessageMapper messageMapper,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock) {
        this.messageRepository = messageRepository;
        this.deliveryRepository = deliveryRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.consumerRegistry = consumerRegistry;
        this.messageMapper = messageMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> listMessages(MessageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        Page<MessageEntity> page = messageRepository.findAll(
                MessageSpecifications.messages(query), pageable);
        return PageResponse.of(page, messageMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public MessageDetailResponse getMessage(String id) {
        MessageEntity message = requireMessage(id);
        List<DeliveryResponse> deliveries = deliveryRepository.findByMessageId(id).stream()
                .map(messageMapper::toDeliveryResponse)
                .toList();
        List<DeadLetterResponse> deadLetters = deadLetterRepository.findByMessageId(id).stream()
                .map(messageMapper::toDeadLetterResponse)
                .toList();
        return new MessageDetailResponse(messageMapper.toResponse(message), deliveries, deadLetters);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getDeliveries(String id) {
        requireMessage(id);
        return deliveryRepository.findByMessageId(id).stream()
                .map(messageMapper::toDeliveryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<DeadLetterResponse> listDeadLetters(DeadLetterQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Direction.DESC, "deadLetteredAt"));
        Page<DeadLetterEntity> page = deadLetterRepository.findAll(
                MessageSpecifications.deadLetters(query), pageable);
        return PageResponse.of(page, messageMapper::toDeadLetterResponse);
    }

    @Transactional(readOnly = true)
    public DeadLetterResponse getDeadLetter(String id) {
        return messageMapper.toDeadLetterResponse(requireDeadLetter(id));
    }

    @Transactional
    public MessageResponse retryMessage(String id) {
        MessageEntity message = requireMessage(id);
        LocalDateTime now = LocalDateTime.now(clock);
        switch (message.getStatus()) {
            case PENDING, RETRYING -> message.setNextAttemptAt(now);
            case DEAD_LETTER -> requeue(message, now);
            default -> throw MessageCenterException.conflict("Message " + id
                    + " cannot be retried in status " + message.getStatus());
        }
        messageRepository.save(message);
        eventPublisher.publishEvent(new MessageQueuedEvent(messageMapper.toEnvelope(message)));
        return messageMapper.toResponse(message);
    }

    @Transactional
    public MessageResponse cancelMessage(String id) {
        MessageEntity message = requireMessage(id);
        MessageStatus status = message.getStatus();
        if (status != MessageStatus.PENDING && status != MessageStatus.RETRYING
                && status != MessageStatus.DEAD_LETTER) {
            throw MessageCenterException.conflict("Message " + id
                    + " cannot be cancelled in status " + status);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        message.transitionTo(MessageStatus.CANCELLED);
        message.setNextAttemptAt(null);
        messageRepository.save(message);
        List<MessageDeliveryEntity> deliveries = deliveryRepository.findByMessageId(id);
        for (MessageDeliveryEntity delivery : deliveries) {
            DeliveryStatus deliveryStatus = delivery.getStatus();
            if (deliveryStatus == DeliveryStatus.PENDING || deliveryStatus == DeliveryStatus.RETRYING
                    || deliveryStatus == DeliveryStatus.DEAD_LETTER) {
                delivery.markCancelled(now);
            }
        }
        if (!deliveries.isEmpty()) {
            deliveryRepository.saveAll(deliveries);
        }
        List<DeadLetterEntity> deadLetters = deadLetterRepository.findByMessageIdAndStatus(id,
                DeadLetterStatus.PENDING);
        for (DeadLetterEntity deadLetter : deadLetters) {
            deadLetter.markDiscarded(now);
        }
        if (!deadLetters.isEmpty()) {
            deadLetterRepository.saveAll(deadLetters);
        }
        return messageMapper.toResponse(message);
    }

    @Transactional
    public DeadLetterResponse replayDeadLetter(String id) {
        DeadLetterEntity deadLetter = requireDeadLetter(id);
        if (deadLetter.getStatus() != DeadLetterStatus.PENDING) {
            throw MessageCenterException.conflict("Dead letter " + id
                    + " cannot be replayed in status " + deadLetter.getStatus());
        }
        MessageEntity message = requireMessage(deadLetter.getMessageId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (message.getStatus() == MessageStatus.DEAD_LETTER) {
            requeue(message, now);
            messageRepository.save(message);
            if (deadLetter.getStatus() == DeadLetterStatus.PENDING) {
                deadLetter.markReplayed(now);
                deadLetterRepository.save(deadLetter);
            }
        } else if (message.getStatus() == MessageStatus.PENDING
                || message.getStatus() == MessageStatus.RETRYING) {
            message.setNextAttemptAt(now);
            message.setLastError(null);
            messageRepository.save(message);
            MessageDeliveryEntity delivery = findDeadLetterDelivery(deadLetter);
            if (delivery != null) {
                delivery.resetForReplay(now);
                deliveryRepository.save(delivery);
            }
            deadLetter.markReplayed(now);
            deadLetterRepository.save(deadLetter);
        } else {
            throw MessageCenterException.conflict("Dead letter " + id
                    + " belongs to message " + message.getId() + " in status " + message.getStatus());
        }
        eventPublisher.publishEvent(new MessageQueuedEvent(messageMapper.toEnvelope(message)));
        return messageMapper.toDeadLetterResponse(deadLetter);
    }

    @Transactional
    public DeadLetterResponse discardDeadLetter(String id) {
        DeadLetterEntity deadLetter = requireDeadLetter(id);
        if (deadLetter.getStatus() != DeadLetterStatus.PENDING) {
            throw MessageCenterException.conflict("Dead letter " + id
                    + " cannot be discarded in status " + deadLetter.getStatus());
        }
        deadLetter.markDiscarded(LocalDateTime.now(clock));
        deadLetterRepository.save(deadLetter);
        return messageMapper.toDeadLetterResponse(deadLetter);
    }

    @Transactional(readOnly = true)
    public MessageStatsResponse stats() {
        return new MessageStatsResponse(
                messageRepository.count(),
                messageRepository.countByStatus(MessageStatus.PENDING),
                messageRepository.countByStatus(MessageStatus.DISPATCHING),
                messageRepository.countByStatus(MessageStatus.DELIVERED),
                messageRepository.countByStatus(MessageStatus.RETRYING),
                messageRepository.countByStatus(MessageStatus.CONSUMED),
                messageRepository.countByStatus(MessageStatus.DEAD_LETTER),
                messageRepository.countByStatus(MessageStatus.CANCELLED),
                deadLetterRepository.countByStatus(DeadLetterStatus.PENDING),
                deadLetterRepository.countByStatus(DeadLetterStatus.REPLAYED),
                deadLetterRepository.countByStatus(DeadLetterStatus.DISCARDED),
                deliveryRepository.countByStatus(DeliveryStatus.RETRYING),
                deliveryRepository.countByStatus(DeliveryStatus.DEAD_LETTER));
    }

    @Transactional(readOnly = true)
    public List<ConsumerDescriptor> consumers() {
        return consumerRegistry.descriptors();
    }

    private void requeue(MessageEntity message, LocalDateTime now) {
        if (message.getStatus() == MessageStatus.DEAD_LETTER
                || message.getStatus() == MessageStatus.RETRYING) {
            message.transitionTo(MessageStatus.PENDING);
        }
        message.setAttempts(0);
        message.setNextAttemptAt(now);
        message.setDeadLetterAt(null);
        message.setLastError(null);
        List<MessageDeliveryEntity> deliveries = deliveryRepository.findByMessageId(message.getId());
        for (MessageDeliveryEntity delivery : deliveries) {
            delivery.resetForReplay(now);
        }
        if (!deliveries.isEmpty()) {
            deliveryRepository.saveAll(deliveries);
        }
        List<DeadLetterEntity> deadLetters = deadLetterRepository.findByMessageIdAndStatus(
                message.getId(), DeadLetterStatus.PENDING);
        for (DeadLetterEntity deadLetter : deadLetters) {
            deadLetter.markReplayed(now);
        }
        if (!deadLetters.isEmpty()) {
            deadLetterRepository.saveAll(deadLetters);
        }
    }

    private MessageDeliveryEntity findDeadLetterDelivery(DeadLetterEntity deadLetter) {
        if (deadLetter.getDeliveryId() != null) {
            return deliveryRepository.findById(deadLetter.getDeliveryId()).orElse(null);
        }
        if (deadLetter.getConsumerGroup() == null) {
            return null;
        }
        return deliveryRepository.findByMessageIdAndConsumerGroup(deadLetter.getMessageId(),
                deadLetter.getConsumerGroup()).orElse(null);
    }

    private MessageEntity requireMessage(String id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> MessageCenterException.notFound("message", id));
    }

    private DeadLetterEntity requireDeadLetter(String id) {
        return deadLetterRepository.findById(id)
                .orElseThrow(() -> MessageCenterException.notFound("dead letter", id));
    }
}