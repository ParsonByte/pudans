package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.mq.model.MessageEnvelope;
import org.springframework.context.ApplicationEventPublisher;

public class LocalMessageTransport implements MessageTransport {

    private final ApplicationEventPublisher publisher;

    public LocalMessageTransport(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public void announce(MessageEnvelope message) {
        publisher.publishEvent(new MessageAnnouncementEvent(message));
    }
}