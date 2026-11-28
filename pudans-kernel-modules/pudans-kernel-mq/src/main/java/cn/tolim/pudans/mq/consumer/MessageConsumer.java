package cn.tolim.pudans.mq.consumer;

import cn.tolim.pudans.mq.model.MessageEnvelope;

import java.util.Set;

public interface MessageConsumer {

    // Unique consumer group used for delivery tracking
    String consumerGroup();

    // Topics this consumer subscribes to, use "*" for every topic
    Set<String> topics();

    // Invoked once per delivery attempt, throwing signals a retryable failure
    void onMessage(MessageEnvelope message) throws Exception;
}