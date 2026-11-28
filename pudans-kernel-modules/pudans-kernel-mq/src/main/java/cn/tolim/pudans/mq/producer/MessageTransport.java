package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.mq.model.MessageEnvelope;

public interface MessageTransport {

    // Transport name exposed for diagnostics
    String name();

    // Announces a persisted message to the delivery layer
    void announce(MessageEnvelope message);
}