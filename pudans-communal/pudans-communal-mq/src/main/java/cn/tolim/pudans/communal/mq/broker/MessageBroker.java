package cn.tolim.pudans.communal.mq.broker;

import cn.tolim.pudans.communal.mq.consumer.MessageConsumer;
import cn.tolim.pudans.communal.mq.producer.MessageProducer;

public interface MessageBroker extends MessageProducer, MessageConsumer {

    String name();

    int subscriptionCount();
}