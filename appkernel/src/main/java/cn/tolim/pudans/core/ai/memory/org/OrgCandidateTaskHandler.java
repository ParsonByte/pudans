package cn.tolim.pudans.core.ai.memory.org;

import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.memory.L1DedupService;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryHashes;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.core.ai.task.AiTaskHandler;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiOrgMemoryRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiOrgMemoryRepository.NewOrgMemory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OrgCandidateTaskHandler implements AiTaskHandler {

    public static final String TASK_TYPE = PipelineTaskType.ORG_CANDIDATE.name();

    private static final int CANDIDATE_BATCH = 100;

    private final AiMemoryL1Repository l1Repository;

    private final AiOrgMemoryRepository orgMemoryRepository;

    private final L1DedupService dedupService;

    private final AiSettingService settingService;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Mono<Void> handle(AiTask task) {
        var teamId = task.teamId();
        var agentId = task.agentId();
        if (!hasText(teamId) || !hasText(agentId)) {
            return Mono.error(new IllegalArgumentException("组织记忆候选任务缺少团队或 Agent 标识"));
        }
        return settingService
                .spec()
                .flatMap(spec -> l1Repository
                        .findOrgCandidates(teamId, agentId, afterL1Id(task), CANDIDATE_BATCH)
                        .collectList()
                        .flatMapMany(records -> Flux.fromIterable(candidates(records)))
                        .concatMap(candidate -> voteAndPromote(candidate, promotionThreshold(spec)))
                        .then());
    }

    private Mono<Void> voteAndPromote(NewOrgMemory candidate, int threshold) {
        return orgMemoryRepository
                .recordVote(candidate)
                .flatMap(voted -> orgMemoryRepository.promoteIfEligible(voted.id(), threshold))
                .then();
    }

    private static int promotionThreshold(AiSetting.Spec spec) {
        return spec.getMemory() == null
                ? new AiSetting.Memory().getOrgPromotionThreshold()
                : spec.getMemory().getOrgPromotionThreshold();
    }

    private List<NewOrgMemory> candidates(List<L1MemoryRecord> records) {
        var grouped = new LinkedHashMap<String, List<L1MemoryRecord>>();
        for (var record : records) {
            grouped.computeIfAbsent(dedupHash(record.content()), hash -> new ArrayList<>()).add(record);
        }
        var candidates = new ArrayList<NewOrgMemory>(grouped.size());
        grouped.forEach((hash, group) -> candidates.add(merge(hash, group)));
        return List.copyOf(candidates);
    }

    private NewOrgMemory merge(String dedupHash, List<L1MemoryRecord> group) {
        var teamIds = new LinkedHashSet<String>();
        var userIds = new LinkedHashSet<String>();
        var memoryIds = new LinkedHashSet<Long>();
        var votes = new HashSet<CrossVote>();
        for (var record : group) {
            var context = record.context();
            teamIds.add(context.teamId());
            userIds.add(context.userId());
            votes.add(new CrossVote(context.teamId(), context.userId()));
            if (record.id() != null) {
                memoryIds.add(record.id());
            }
        }
        var first = group.getFirst();
        return new NewOrgMemory(
                first.content(),
                first.type().name(),
                dedupHash,
                List.copyOf(teamIds),
                List.copyOf(userIds),
                List.copyOf(memoryIds),
                votes.size());
    }

    private String dedupHash(String content) {
        return MemoryHashes.sha256Hex(dedupService.normalize(content));
    }

    private static long afterL1Id(AiTask task) {
        var value = task.payload().get(OrgCandidateTaskPayload.AFTER_L1_ID);
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return Math.max(0L, Long.parseLong(text));
        }
        return 0L;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CrossVote(String teamId, String userId) {}
}
