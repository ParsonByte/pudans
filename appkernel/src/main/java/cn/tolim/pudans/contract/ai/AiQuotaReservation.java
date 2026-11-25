package cn.tolim.pudans.contract.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiQuotaReservation(
        UUID id,
        String subjectType,
        String subjectId,
        UUID planId,
        UUID runId,
        UUID taskId,
        BigDecimal reservedCredits,
        BigDecimal actualCredits,
        Integer reservedToolCalls,
        Integer actualToolCalls,
        Integer reservedMcpCalls,
        Integer actualMcpCalls,
        Instant fiveHourBucketAt,
        Instant weeklyPeriodStart,
        String status,
        String idempotencyKey,
        Instant expiresAt,
        Instant createdAt,
        Instant settledAt) {}
