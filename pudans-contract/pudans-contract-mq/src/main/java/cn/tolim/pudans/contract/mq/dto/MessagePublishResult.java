package cn.tolim.pudans.contract.mq.dto;

import cn.tolim.pudans.contract.mq.enums.MessageStatus;

import java.time.LocalDateTime;

// Result of a publish request
public record MessagePublishResult(String id,
                                   MessageStatus status,
                                   boolean duplicate,
                                   LocalDateTime createTime) {
}