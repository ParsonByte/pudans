package cn.tolim.pudans.contract.risk.dto;

import java.time.Instant;

public record RiskAlert(String scene, String subject, long count, String path, Instant occurredAt) {}
