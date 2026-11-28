package cn.tolim.pudans.mq.model;

import cn.tolim.pudans.mq.enums.CompensationStatus;

public record CompensationResult(String deadLetterId, CompensationStatus status, String message) {
}