package cn.tolim.pudans.contract.creation.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkReportResult(UUID reportId, String status, Instant recordedAt) {}
