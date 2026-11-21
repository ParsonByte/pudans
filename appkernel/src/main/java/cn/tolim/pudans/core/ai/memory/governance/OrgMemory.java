package cn.tolim.pudans.core.ai.memory.governance;

import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrgMemory {

    public static final String CODE_ORG_MEMORY_NOT_FOUND = "ai-org-memory-not-found";

    private static final String TRUST_PENDING = "PENDING";

    private static final String TRUST_VERIFIED = "VERIFIED";

    private static final String TRUST_REJECTED = "REJECTED";

    private static final String TRUST_REVOKED = "REVOKED";

    private final AiMemoryGovernanceRepository repository;

    public Mono<List<OrgMemoryRecord>> list(long beforeId, int limit) {
        return repository.listOrgMemories(TRUST_VERIFIED, beforeId, limit).collectList();
    }

    public Mono<List<OrgMemoryRecord>> pending(long beforeId, int limit) {
        return repository.listOrgMemories(TRUST_PENDING, beforeId, limit).collectList();
    }

    public Mono<OrgMemoryRecord> approve(long id, String reviewer) {
        return transition(id, TRUST_PENDING, TRUST_VERIFIED, true, reviewer, true);
    }

    public Mono<OrgMemoryRecord> reject(long id, String reviewer) {
        return transition(id, TRUST_PENDING, TRUST_REJECTED, false, reviewer, false);
    }

    public Mono<OrgMemoryRecord> revoke(long id, String reviewer) {
        return transition(id, TRUST_VERIFIED, TRUST_REVOKED, false, reviewer, false);
    }

    private Mono<OrgMemoryRecord> transition(
            long id,
            String from,
            String to,
            boolean active,
            String reviewer,
            boolean markApprovedAt) {
        return repository
                .findOrgMemory(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, CODE_ORG_MEMORY_NOT_FOUND, "组织记忆不存在。")))
                .flatMap(memory -> from.equals(memory.trustLevel())
                        ? repository.updateOrgMemoryStatus(id, to, active, reviewer, markApprovedAt)
                        : Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST,
                                MemoryGovernance.CODE_REQUEST_INVALID,
                                "组织记忆当前状态不允许该操作。")));
    }
}
