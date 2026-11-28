package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.communal.mq.producer.MessageProducer;
import cn.tolim.pudans.mq.model.MessageEnvelope;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class RocketMqMessageTransport implements MessageTransport {

    public static final String ANNOUNCEMENT_TAG = "ANNOUNCEMENT";

    private final MessageProducer producer;

    private final String announcementTopic;

    private final ObjectMapper objectMapper;

    public RocketMqMessageTransport(MessageProducer producer, String announcementTopic,
                                    ObjectMapper objectMapper) {
        this.producer = producer;
        this.announcementTopic = announcementTopic;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "rocketmq";
    }

    @Override
    public void announce(MessageEnvelope envelope) {
        Map<String, String> headers = new LinkedHashMap<>();
        putHeader(headers, "topic", envelope.topic());
        putHeader(headers, "eventType", envelope.eventType());
        putHeader(headers, "producer", envelope.producer());
        putHeader(headers, "bizKey", envelope.bizKey());
        // RocketMQ tags reject dots and colons, so the business topic travels in a header
        Message message = new Message(announcementTopic, ANNOUNCEMENT_TAG, envelope.id(),
                objectMapper.writeValueAsString(envelope), headers, Instant.now());
        producer.send(message);
    }

    private static void putHeader(Map<String, String> headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
    }
}