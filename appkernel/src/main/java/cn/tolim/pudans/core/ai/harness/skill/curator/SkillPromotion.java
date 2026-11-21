package cn.tolim.pudans.core.ai.harness.skill.curator;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.ai.resource.AiSkill;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// Curator 负责技能晋升、归档、自学习候选与使用审计的持久化编排
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillPromotion {

    public static final String CODE_NOT_FOUND = "ai-skill-not-found";

    public static final String CODE_INVALID = "ai-skill-curator-invalid";

    private final ReactiveExtensionClient client;

    private final SkillUsageStore usageStore;

    private final SkillAuditLog auditLog;

    private final SkillPromotionGate promotionGate;

    public Mono<Result> promote(String name, String actor) {
        if (!StringUtils.hasText(name)) {
            return Mono.just(invalid("promote", "curator", name, "技能名称不能为空。"));
        }
        return client.get(AiSkill.class, name)
                .filter(skill -> skill.getSpec() != null)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, CODE_NOT_FOUND, "技能不存在：" + name + "。")))
                .flatMap(skill -> publish(skill, reviewer(actor), List.of()))
                .map(ignored -> new Result(Status.APPROVED, name, "技能已发布。"));
    }

    public Mono<Result> archive(String name, String actor) {
        if (!StringUtils.hasText(name)) {
            return Mono.just(invalid("archive", "curator", name, "技能名称不能为空。"));
        }
        return client.get(AiSkill.class, name)
                .filter(skill -> skill.getSpec() != null)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, CODE_NOT_FOUND, "技能不存在：" + name + "。")))
                .flatMap(skill -> {
                    skill.getSpec().setEnabled(false);
                    skill.getSpec().setStatus(AiSkill.Status.ARCHIVED);
                    return client.update(skill)
                            .doOnNext(updated -> {
                                usageStore.setState(name, SkillUsageRecord.State.ARCHIVED);
                                audit("archive", reviewer(actor), name, "archived", null);
                            })
                            .map(ignored -> new Result(Status.ARCHIVED, name, "技能已归档。"));
                });
    }

    // 定期评估草稿技能：使用次数达到门槛后自动晋升
    public Mono<Long> autoPromote() {
        return client.listAll(AiSkill.class, ListOptions.builder().build(), defaultSort())
                .filter(skill -> skill.getSpec() != null
                        && skill.getSpec().getStatus() == AiSkill.Status.DRAFT)
                .filter(skill -> skill.getMetadata() != null
                        && StringUtils.hasText(skill.getMetadata().getName()))
                .concatMap(skill -> {
                    var candidate = new SkillCandidate(
                            skill.getMetadata().getName(),
                            skill.getSpec().getDescription(),
                            content(skill),
                            List.of());
                    return promotionGate
                            .review(candidate)
                            .flatMap(decision -> applyDecision(skill, decision))
                            .onErrorResume(error -> {
                                log.warn("技能 {} 自动晋升审核失败：{}", skill.getMetadata().getName(), error.getMessage());
                                return Mono.just(0L);
                            });
                })
                .reduce(0L, Long::sum);
    }

    private Mono<Long> applyDecision(AiSkill skill, SkillPromotionGate.Decision decision) {
        var name = skill.getMetadata().getName();
        if (decision instanceof SkillPromotionGate.Decision.Approve approve) {
            return publish(skill, approve.reviewerId(), approve.environments()).thenReturn(1L);
        }
        if (decision instanceof SkillPromotionGate.Decision.Reject reject) {
            audit("promote", reject.reviewerId(), name, "rejected", reject.reason());
        } else if (decision instanceof SkillPromotionGate.Decision.Defer defer) {
            audit("promote", "curator", name, "deferred", defer.reason());
        }
        return Mono.just(0L);
    }

    private Mono<AiSkill> publish(AiSkill skill, String reviewer, List<String> environments) {
        var name = skill.getMetadata().getName();
        skill.getSpec().setEnabled(true);
        skill.getSpec().setStatus(AiSkill.Status.PUBLISHED);
        return client.update(skill).doOnNext(updated -> {
            usageStore.markPromoted(name, reviewer, environments);
            audit(
                    "promote",
                    reviewer,
                    name,
                    "approved",
                    environments.isEmpty() ? null : "environments=" + String.join(",", environments));
        });
    }

    public Mono<List<SkillUsageRecord>> usage() {
        return Mono.fromSupplier(usageStore::all);
    }

    public Mono<List<SkillAuditLog.Entry>> audit(int limit) {
        var entries = auditLog.entries();
        var bounded = Math.max(1, limit);
        return Mono.just(entries.size() <= bounded
                ? entries
                : entries.subList(entries.size() - bounded, entries.size()));
    }

    private void audit(String operation, String actor, String target, String decision, String detail) {
        auditLog.append(new SkillAuditLog.Entry(Instant.now(), actor, operation, target, decision, detail));
    }

    private static String content(AiSkill skill) {
        var content = skill.getSpec().getContent();
        return StringUtils.hasText(content) ? content : skill.getSpec().getDisplayName();
    }

    private static String reviewer(String actor) {
        return StringUtils.hasText(actor) ? actor : "curator";
    }

    private Result invalid(String operation, String actor, String name, String message) {
        var target = StringUtils.hasText(name) ? name : "unknown";
        audit(operation, reviewer(actor), target, "invalid", message);
        return new Result(Status.INVALID, target, message);
    }

    public record Result(Status status, String name, String message) {}

    public enum Status {
        APPROVED,
        ARCHIVED,
        INVALID
    }
}