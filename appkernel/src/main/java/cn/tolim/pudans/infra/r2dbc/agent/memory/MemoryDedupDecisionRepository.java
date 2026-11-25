package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemoryDedupDecisionRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> insert(
            UUID id,
            UUID candidateMemoryId,
            UUID matchedMemoryId,
            String decision,
            Double exactScore,
            Double simhashScore,
            Double vectorScore,
            Double lexicalScore,
            Map<String, Object> llmVerdict,
            UUID modelId,
            Instant decidedAt) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO memory_dedup_decisions (
                            id, candidate_memory_id, matched_memory_id, decision, exact_score,
                            simhash_score, vector_score, lexical_score, llm_verdict_jsonb,
                            model_id, decided_at)
                        VALUES (
                            :id, :candidateMemoryId, :matchedMemoryId, :decision, :exactScore,
                            :simhashScore, :vectorScore, :lexicalScore,
                            CAST(:llmVerdict AS jsonb), :modelId, :decidedAt)
                        """)
                .bind("id", id)
                .bind("candidateMemoryId", candidateMemoryId);
        spec = bindNullable(
                spec, "matchedMemoryId", matchedMemoryId, UUID.class);
        spec = bindNullable(spec, "exactScore", exactScore, Double.class);
        spec = bindNullable(spec, "simhashScore", simhashScore, Double.class);
        spec = bindNullable(spec, "vectorScore", vectorScore, Double.class);
        spec = bindNullable(spec, "lexicalScore", lexicalScore, Double.class);
        spec = bindNullable(spec, "modelId", modelId, UUID.class);
        return spec.bind("decision", decision)
                .bind("llmVerdict", MemoryJsonCodec.write(llmVerdict))
                .bind("decidedAt", utc(decidedAt))
                .then();
    }
}
