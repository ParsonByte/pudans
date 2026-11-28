package cn.tolim.pudans.mq.dispatch;

import cn.tolim.pudans.communal.mq.consumer.MessageConsumer;
import cn.tolim.pudans.communal.mq.consumer.MessageSubscription;
import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.mq.model.MessageEnvelope;
import cn.tolim.pudans.mq.service.MessageDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import tools.jackson.databind.ObjectMapper;

public class RocketMqAnnouncementSubscriber implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketMqAnnouncementSubscriber.class);

    private final MessageConsumer consumer;

    private final MessageDeliveryService deliveryService;

    private final ObjectMapper objectMapper;

    private final String announcementTopic;

    private final String consumerGroup;

    private MessageConsumer.SubscriptionHandle handle;

    private volatile boolean running;

    public RocketMqAnnouncementSubscriber(MessageConsumer consumer,
                                          MessageDeliveryService deliveryService,
                                          ObjectMapper objectMapper,
                                          String announcementTopic,
                                          String consumerGroup) {
        this.consumer = consumer;
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
        this.announcementTopic = announcementTopic;
        this.consumerGroup = consumerGroup;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        handle = consumer.subscribe(new MessageSubscription(announcementTopic,
                MessageSubscription.ALL_TAGS, consumerGroup, this::onMessage));
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        if (handle != null) {
            handle.close();
            handle = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Stop before the broker shuts down its consumers
        return SmartLifecycle.DEFAULT_PHASE + 10;
    }

    private void onMessage(Message message) {
        try {
            MessageEnvelope envelope = objectMapper.readValue(message.payload(), MessageEnvelope.class);
            if (envelope.id() != null) {
                deliveryService.dispatch(envelope.id());
            }
        } catch (Exception exception) {
            // The dispatch scheduler reconciles persisted messages, so failed announcements are dropped
            LOGGER.warn("Failed to process announcement {}: {}", message.key(), exception.getMessage());
        }
    }
}