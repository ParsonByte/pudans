package cn.tolim.pudans.contract.mq.dto;

import cn.tolim.pudans.contract.mq.enums.CompensationStatus;

// Result of compensating a dead letter
public record CompensationResult(String deadLetterId, CompensationStatus status, String message) {
}