package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.KnowledgeBaseStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.MemoryStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.PointStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RoutingStatsRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiStatsService {

    private final AiStatsRepository repository;

    public Mono<RoutingStats> routing(int days) {
        return repository.routing(days(days)).collectList().map(RoutingStats::new);
    }

    public Mono<PointStats> points(int days) {
        return repository.points(days(days)).collectList().map(PointStats::new);
    }

    public Mono<MemoryStatsRow> memory() {
        return repository.memory();
    }

    public Mono<KnowledgeBaseStatsRow> knowledgeBase() {
        return repository.knowledgeBase();
    }

    private static int days(int days) {
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("days 必须在 1 到 90 之间。");
        }
        return days;
    }

    public record RoutingStats(List<RoutingStatsRow> rows) {}

    public record PointStats(List<PointStatsRow> rows) {}
}
