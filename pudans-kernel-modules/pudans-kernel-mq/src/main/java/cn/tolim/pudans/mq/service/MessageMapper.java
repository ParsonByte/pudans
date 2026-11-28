package cn.tolim.pudans.mq.service;

import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.entity.MessageDeliveryEntity;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.exception.MessageCenterException;
import cn.tolim.pudans.mq.model.DeadLetterResponse;
import cn.tolim.pudans.mq.model.DeliveryResponse;
import cn.tolim.pudans.mq.model.MessageEnvelope;
import cn.tolim.pudans.mq.model.MessageResponse;
import cn.tolim.pudans.mq.util.MessageTexts;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MessageMapper {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private static final TypeReference<Map<String, String>> HEADER_TYPE = new TypeReference<>() {
    };

    public MessageEnvelope toEnvelope(MessageEntity entity) {
        return new MessageEnvelope(entity.getId(), entity.getBizKey(), entity.getTopic(),
                entity.getEventType(), entity.getProducer(), entity.getPayload(),
                parseHeaders(entity.getHeaders()), entity.getAttempts(), entity.getCreateTime());
    }

    public MessageResponse toResponse(MessageEntity entity) {
        return new MessageResponse(entity.getId(), entity.getBizKey(), entity.getTopic(),
                entity.getEventType(), entity.getProducer(), parsePayload(entity.getPayload()),
                parseHeaders(entity.getHeaders()), entity.getStatus(), entity.getAttempts(),
                entity.getMaxAttempts(), entity.getNextAttemptAt(), entity.getLastAttemptAt(),
                entity.getDeliveredAt(), entity.getConsumedAt(), entity.getDeadLetterAt(),
                entity.getLastError(), entity.getCreateTime(), entity.getUpdateTime());
    }

    public DeliveryResponse toDeliveryResponse(MessageDeliveryEntity entity) {
        return new DeliveryResponse(entity.getId(), entity.getMessageId(), entity.getConsumerGroup(),
                entity.getTopic(), entity.getStatus(), entity.getAttempts(), entity.getMaxAttempts(),
                entity.getNextAttemptAt(), entity.getLastAttemptAt(), entity.getConsumedAt(),
                entity.getLastError(), entity.getCreateTime());
    }

    public DeadLetterResponse toDeadLetterResponse(DeadLetterEntity entity) {
        return new DeadLetterResponse(entity.getId(), entity.getMessageId(), entity.getDeliveryId(),
                entity.getTopic(), entity.getEventType(), entity.getProducer(), entity.getConsumerGroup(),
                parsePayload(entity.getPayload()), parseHeaders(entity.getHeaders()), entity.getAttempts(),
                entity.getLastError(), entity.getStatus(), entity.getDeadLetteredAt(),
                entity.getReplayedAt(), entity.getDiscardedAt(), entity.getCompensationStatus(),
                entity.getCompensatedAt(), entity.getCompensationError(), entity.getCreateTime());
    }

    public String serializePayload(Object payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception exception) {
            throw MessageCenterException.internal("Unable to serialize message payload: "
                    + exception.getMessage());
        }
    }

    public String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(headers);
        } catch (Exception exception) {
            throw MessageCenterException.internal("Unable to serialize message headers: "
                    + exception.getMessage());
        }
    }

    public Object parsePayload(String payload) {
        if (MessageTexts.isBlank(payload)) {
            return null;
        }
        try {
            return JSON.readValue(payload, Object.class);
        } catch (Exception exception) {
            throw MessageCenterException.internal("Unable to parse message payload: "
                    + exception.getMessage());
        }
    }

    public Map<String, String> parseHeaders(String headers) {
        if (MessageTexts.isBlank(headers)) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = JSON.readValue(headers, HEADER_TYPE);
            return parsed == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        } catch (Exception exception) {
            throw MessageCenterException.internal("Unable to parse message headers: "
                    + exception.getMessage());
        }
    }
}