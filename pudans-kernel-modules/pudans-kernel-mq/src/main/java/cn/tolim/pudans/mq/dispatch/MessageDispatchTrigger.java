package cn.tolim.pudans.mq.dispatch;

import cn.tolim.pudans.mq.producer.MessageAnnouncementEvent;
import cn.tolim.pudans.mq.service.MessageDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class MessageDispatchTrigger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatchTrigger.class);

    private final MessageDeliveryService messageDeliveryService;

    private final ObjectProvider<TaskExecutor> taskExecutorProvider;

    public MessageDispatchTrigger(MessageDeliveryService messageDeliveryService,
                                  ObjectProvider<TaskExecutor> taskExecutorProvider) {
        this.messageDeliveryService = messageDeliveryService;
        this.taskExecutorProvider = taskExecutorProvider;
    }

    // Immediate dispatch attempt for a freshly announced message
    @EventListener
    public void onAnnouncement(MessageAnnouncementEvent event) {
        String messageId = event.envelope().id();
        if (messageId == null) {
            return;
        }
        Runnable task = () -> {
            try {
                messageDeliveryService.dispatch(messageId);
            } catch (Exception exception) {
                LOGGER.warn("Failed to dispatch announced message {}: {}", messageId,
                        exception.getMessage());
            }
        };
        TaskExecutor executor = taskExecutorProvider.getIfUnique();
        if (executor != null) {
            try {
                executor.execute(task);
                return;
            } catch (RuntimeException exception) {
                LOGGER.warn("Task executor rejected dispatch for message {}: {}", messageId,
                        exception.getMessage());
            }
        }
        task.run();
    }
}