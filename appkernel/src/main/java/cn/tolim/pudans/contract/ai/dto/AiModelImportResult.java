package cn.tolim.pudans.contract.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiModelImportResult(
        UUID channelId,
        int discovered,
        int created,
        int updated,
        int skipped,
        Instant importedAt) {}
