package cn.tolim.pudans.mq.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageDispatchCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatchCoordinator.class);

    private final MessageDeliveryService messageDeliveryService;

    private final Clock clock;

    public MessageDispatchCoordinator(MessageDeliveryService messageDeliveryService, Clock clock) {
        this.messageDeliveryService = messageDeliveryService;
        this.clock = clock;
    }

    // One dispatch tick: recover stuck rows, then claim and deliver due messages
    public int dispatchDue() {
        try {
            messageDeliveryService.recoverStuckMessages();
        } catch (Exception exception) {
            LOGGER.error("Failed to recover stuck messages", exception);
        }
        List<String> messageIds;
        try {
            messageIds = messageDeliveryService.findDispatchableIds(LocalDateTime.now(clock));
        } catch (Exception exception) {
            LOGGER.error("Failed to load dispatchable messages", exception);
            return 0;
        }
        int dispatched = 0;
        for (String messageId : messageIds) {
            try {
                DispatchOutcome outcome = messageDeliveryService.dispatch(messageId);
                if (outcome != DispatchOutcome.SKIPPED) {
                    dispatched++;
                }
            } catch (Exception exception) {
                LOGGER.warn("Failed to dispatch message {}: {}", messageId, exception.getMessage());
            }
        }
        return dispatched;
    }
}