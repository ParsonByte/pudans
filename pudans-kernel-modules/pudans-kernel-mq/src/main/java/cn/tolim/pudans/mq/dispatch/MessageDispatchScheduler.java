package cn.tolim.pudans.mq.dispatch;

import cn.tolim.pudans.mq.service.MessageDispatchCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pudans.mq", name = "scheduler-enabled", matchIfMissing = true)
public class MessageDispatchScheduler {

    private final MessageDispatchCoordinator dispatchCoordinator;

    public MessageDispatchScheduler(MessageDispatchCoordinator dispatchCoordinator) {
        this.dispatchCoordinator = dispatchCoordinator;
    }

    @Scheduled(fixedDelayString = "${pudans.mq.dispatch-interval-ms:2000}",
            initialDelayString = "${pudans.mq.dispatch-initial-delay-ms:3000}")
    public void dispatchDueMessages() {
        dispatchCoordinator.dispatchDue();
    }
}