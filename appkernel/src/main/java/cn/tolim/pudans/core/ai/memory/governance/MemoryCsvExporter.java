package cn.tolim.pudans.core.ai.memory.governance;

import cn.tolim.pudans.core.ai.export.CsvSupport;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.MemoryExportRow;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

final class MemoryCsvExporter {

    static final String HEADER =
            "id,record_id,team_id,agent_id,user_id,scene_name,memory_type,priority,status,scope,"
                    + "version,decay_score,last_accessed_at,created_at,content\n";

    private MemoryCsvExporter() {}

    static String line(MemoryExportRow row) {
        return CsvSupport.line(
                String.valueOf(row.id()),
                row.recordId(),
                row.teamId(),
                row.agentId(),
                row.userId(),
                row.sceneName(),
                row.memoryType(),
                String.valueOf(row.priority()),
                row.status(),
                row.scope(),
                String.valueOf(row.version()),
                decimal(row.decayScore()),
                instant(row.lastAccessedAt()),
                instant(row.createdAt()),
                row.content());
    }

    private static String decimal(@Nullable BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String instant(@Nullable Instant value) {
        return value == null ? "" : value.toString();
    }
}
