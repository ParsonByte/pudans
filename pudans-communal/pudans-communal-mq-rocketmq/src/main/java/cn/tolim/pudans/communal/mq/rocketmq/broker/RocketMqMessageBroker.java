package cn.tolim.pudans.communal.mq.rocketmq.broker;

import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import cn.tolim.pudans.communal.mq.consumer.MessageSubscription;
import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.communal.mq.message.SendResult;
import cn.tolim.pudans.communal.mq.rocketmq.configuration.RocketMqMessagingProperties;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class RocketMqMessageBroker implements MessageBroker, SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketMqMessageBroker.class);

    private static final String HEADER_PREFIX = "pudans_";

    private static final Pattern HEADER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final RocketMqMessagingProperties properties;

    private final DefaultMQProducer producer;

    private final RPCHook rpcHook;

    private final Map<String, DefaultMQPushConsumer> consumers = new ConcurrentHashMap<>();

    private volatile boolean running;

    public RocketMqMessageBroker(RocketMqMessagingProperties properties) {
        this.properties = properties;
        if (properties.getConsumerThreadMax() < properties.getConsumerThreadMin()) {
            throw new IllegalArgumentException(
                    "consumerThreadMax must be greater than or equal to consumerThreadMin");
        }
        // ACL is optional; both credentials must be present before requests are signed
        boolean hasAccessKey = properties.getAccessKey() != null && !properties.getAccessKey().isBlank();
        boolean hasSecretKey = properties.getSecretKey() != null && !properties.getSecretKey().isBlank();
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalArgumentException("accessKey and secretKey must be configured together");
        }
        this.rpcHook = hasAccessKey
                ? new AclClientRPCHook(new SessionCredentials(properties.getAccessKey(),
                        properties.getSecretKey()))
                : null;
        this.producer = rpcHook == null
                ? new DefaultMQProducer(properties.getProducerGroup())
                : new DefaultMQProducer(properties.getProducerGroup(), rpcHook);
        this.producer.setNamesrvAddr(properties.getNameServer());
        this.producer.setSendMsgTimeout(properties.getSendTimeoutMs());
    }

    @Override
    public String name() {
        return "rocketmq";
    }

    @Override
    public int subscriptionCount() {
        return consumers.size();
    }

    @Override
    public SendResult send(Message message) {
        if (!running) {
            throw new IllegalStateException("RocketMQ producer is not running");
        }
        try {
            org.apache.rocketmq.client.producer.SendResult result =
                    producer.send(toRocketMessage(message));
            return new SendResult(result.getMsgId(), message.topic(), Instant.now());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "RocketMQ send interrupted for topic " + message.topic(), exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "RocketMQ send failed for topic " + message.topic(), exception);
        }
    }

    @Override
    public SubscriptionHandle subscribe(MessageSubscription subscription) {
        DefaultMQPushConsumer consumer = rpcHook == null
                ? new DefaultMQPushConsumer(subscription.consumerGroup())
                : new DefaultMQPushConsumer(subscription.consumerGroup(), rpcHook);
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setConsumeThreadMin(properties.getConsumerThreadMin());
        consumer.setConsumeThreadMax(properties.getConsumerThreadMax());
        try {
            consumer.subscribe(subscription.topic(), subscription.tagFilter());
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (MessageExt messageExt : messages) {
                    try {
                        subscription.listener().onMessage(toMessage(messageExt));
                    } catch (Exception exception) {
                        LOGGER.warn("RocketMQ delivery failed: topic={}, group={}, msgId={}",
                                messageExt.getTopic(), subscription.consumerGroup(),
                                messageExt.getMsgId(), exception);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
        } catch (Exception exception) {
            consumer.shutdown();
            throw new IllegalStateException(
                    "Failed to subscribe to RocketMQ topic " + subscription.topic(), exception);
        }
        String key = subscription.topic() + "|" + subscription.consumerGroup() + "|"
                + subscription.tagFilter();
        DefaultMQPushConsumer previous = consumers.putIfAbsent(key, consumer);
        if (previous != null) {
            consumer.shutdown();
            throw new IllegalStateException("Duplicate RocketMQ subscription: " + key);
        }
        return () -> {
            DefaultMQPushConsumer removed = consumers.remove(key);
            if (removed != null) {
                removed.shutdown();
            }
        };
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            producer.start();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to start RocketMQ producer", exception);
        }
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (running) {
            consumers.values().forEach(DefaultMQPushConsumer::shutdown);
            consumers.clear();
            producer.shutdown();
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    static org.apache.rocketmq.common.message.Message toRocketMessage(Message message) {
        org.apache.rocketmq.common.message.Message rocketMessage =
                new org.apache.rocketmq.common.message.Message(message.topic(), message.tag(),
                        message.key(), message.payload().getBytes(StandardCharsets.UTF_8));
        message.headers().forEach((name, value) -> {
            if (!HEADER_NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid message header name: " + name);
            }
            if (value != null) {
                rocketMessage.putUserProperty(HEADER_PREFIX + name, value);
            }
        });
        return rocketMessage;
    }

    static Message toMessage(MessageExt messageExt) {
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> properties = messageExt.getProperties();
        if (properties != null) {
            properties.forEach((name, value) -> {
                if (name.startsWith(HEADER_PREFIX)) {
                    headers.put(name.substring(HEADER_PREFIX.length()), value);
                }
            });
        }
        byte[] body = messageExt.getBody();
        return new Message(messageExt.getTopic(), messageExt.getTags(), messageExt.getKeys(),
                body == null ? "" : new String(body, StandardCharsets.UTF_8), headers,
                Instant.ofEpochMilli(messageExt.getBornTimestamp()));
    }
}