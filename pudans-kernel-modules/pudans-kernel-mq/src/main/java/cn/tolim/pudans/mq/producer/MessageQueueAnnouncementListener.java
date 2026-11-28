package cn.tolim.pudans.mq.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MessageQueueAnnouncementListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageQueueAnnouncementListener.class);

    private final MessageTransport messageTransport;

    public MessageQueueAnnouncementListener(MessageTransport messageTransport) {
        this.messageTransport = messageTransport;
    }

    // Announces only after the outbox row is committed
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageQueued(MessageQueuedEvent event) {
        try {
            messageTransport.announce(event.envelope());
        } catch (Exception exception) {
            LOGGER.warn("Failed to announce message {}: {}", event.envelope().id(),
                    exception.getMessage());
        }
    }
}