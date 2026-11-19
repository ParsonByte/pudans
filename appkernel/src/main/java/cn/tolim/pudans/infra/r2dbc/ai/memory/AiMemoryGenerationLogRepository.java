package cn.tolim.pudans.infra.r2dbc.ai.memory;

import cn.tolim.pudans.core.ai.memory.MemoryGenerationLog;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryGenerationLogRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> insert(MemoryGenerationLog log) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_generation_log (
                            memory_level, memory_id, team_id, agent_id, user_id, session_id,
                            prompt_id, prompt_version, prompt_source, prompt_hash, model_code,
                            input_ref, output_ref
                        ) VALUES (
                            :memoryLevel, :memoryId, :teamId, :agentId, :userId, :sessionId,
                            :promptId, :promptVersion, :promptSource, :promptHash, :modelCode,
                            :inputRef, :outputRef
                        )
                        """)
                .bind("memoryLevel", log.memoryLevel().name())
                .bind("memoryId", log.memoryId())
                .bind("promptSource", log.promptSource().name().toLowerCase(Locale.ROOT))
                .bind("promptHash", log.promptHash());
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", log.teamId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", log.agentId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "userId", log.userId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "sessionId", log.sessionId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "promptId", log.promptId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "promptVersion", log.promptVersion(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "modelCode", log.modelCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "inputRef", log.inputRef(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "outputRef", log.outputRef(), String.class);
        return spec.then();
    }
}
