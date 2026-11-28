package cn.tolim.pudans.mq.model;

import cn.tolim.pudans.mq.enums.CompensationStatus;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;

public record DeadLetterQuery(String topic,
                              String messageId,
                              String consumerGroup,
                              DeadLetterStatus status,
                              CompensationStatus compensationStatus,
                              int page,
                              int size) {

    public DeadLetterQuery {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 200);
    }
}