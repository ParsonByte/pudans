package cn.tolim.pudans.communal.mq.producer;

import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.communal.mq.message.SendResult;

public interface MessageProducer {

    SendResult send(Message message);

    default SendResult send(String topic, String payload) {
        return send(Message.of(topic, payload));
    }
}