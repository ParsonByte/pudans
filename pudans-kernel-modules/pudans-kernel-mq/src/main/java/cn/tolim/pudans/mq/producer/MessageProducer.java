package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.mq.model.MessagePublishRequest;
import cn.tolim.pudans.mq.model.MessagePublishResult;

public interface MessageProducer {

    MessagePublishResult publish(MessagePublishRequest request);

    default MessagePublishResult send(MessagePublishRequest request) {
        return publish(request);
    }
}