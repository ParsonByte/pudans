package cn.tolim.pudans.mq.model;

import cn.tolim.pudans.mq.enums.MessageStatus;

import java.time.LocalDateTime;

public record MessageQuery(String topic,
                           String eventType,
                           String producer,
                           String bizKey,
                           MessageStatus status,
                           String keyword,
                           LocalDateTime createdFrom,
                           LocalDateTime createdTo,
                           int page,
                           int size) {

    public MessageQuery {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 200);
    }
}