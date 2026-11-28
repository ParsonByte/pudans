package cn.tolim.pudans.mq.model;

import cn.tolim.pudans.mq.enums.MessageStatus;

import java.time.LocalDateTime;

public record MessagePublishResult(String id,
                                   MessageStatus status,
                                   boolean duplicate,
                                   LocalDateTime createTime) {
}