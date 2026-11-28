package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.mq.model.MessageEnvelope;

public record MessageAnnouncementEvent(MessageEnvelope envelope) {
}