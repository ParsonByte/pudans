package cn.tolim.pudans.core.ai.plan;

import java.time.Instant;

public record PlanWindow(String windowKey, Instant startsAt, Instant endsAt) {}