package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

// 默认学习门：使用次数达到阈值才允许自动晋升
@Service
public class UsageSkillPromotionGate implements SkillPromotionGate {

    private final SkillUsageStore usageStore;

    private final long minUses;

    public UsageSkillPromotionGate(
            SkillUsageStore usageStore, @Value("${pudans.ai.skill.promotion-min-uses:5}") long minUses) {
        this.usageStore = usageStore;
        this.minUses = Math.max(1, minUses);
    }

    @Override
    public Mono<Decision> review(SkillCandidate candidate) {
        return Mono.fromSupplier(() -> usageStore
                .get(candidate.name())
                .filter(record -> record.state() == SkillUsageRecord.State.ACTIVE
                        && record.useCount() >= minUses)
                .<Decision>map(record -> new Decision.Approve("curator", List.of()))
                .orElseGet(() -> new Decision.Defer("技能使用次数未达到晋升门槛。", Duration.ofHours(1))));
    }
}