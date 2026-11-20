package cn.tolim.pudans.core.ai.harness.skill.curator;

import cn.tolim.pudans.core.ai.harness.skill.SkillEntry;
import cn.tolim.pudans.core.ai.harness.skill.SkillRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

public final class SkillCurator {

    private static final Logger log = LoggerFactory.getLogger(SkillCurator.class);

    private final SkillRepository drafts;

    private final SkillRepository published;

    private final SkillUsageStore usageStore;

    private final SkillAuditLog auditLog;

    private final SkillPromotionGate gate;

    public SkillCurator(
            SkillRepository drafts,
            SkillRepository published,
            SkillUsageStore usageStore,
            SkillAuditLog auditLog,
            SkillPromotionGate gate) {
        this.drafts = Objects.requireNonNull(drafts, "草稿技能仓库不能为空。");
        this.published = Objects.requireNonNull(published, "发布技能仓库不能为空。");
        this.usageStore = Objects.requireNonNull(usageStore, "技能使用记录不能为空。");
        this.auditLog = Objects.requireNonNull(auditLog, "技能审计日志不能为空。");
        this.gate = Objects.requireNonNull(gate, "技能审核门不能为空。");
    }

    public Mono<Result> promote(String name) {
        if (!StringUtils.hasText(name)) {
            return Mono.just(invalid("promote", "curator", name, "技能名称不能为空。"));
        }
        Optional<SkillEntry> draft;
        try {
            draft = drafts.find(name);
        } catch (RuntimeException error) {
            return Mono.just(invalid("promote", "curator", name, "读取草稿失败：" + error.getMessage()));
        }
        if (draft.isEmpty()) {
            return Mono.just(invalid("promote", "curator", name, "草稿技能不存在。"));
        }
        var entry = draft.get();
        var candidate = new SkillCandidate(
                entry.name(), entry.description(), entry.markdown(), entry.resources().list());
        Mono<SkillPromotionGate.Decision> decision;
        try {
            decision = gate.review(candidate);
        } catch (RuntimeException error) {
            return Mono.just(invalid("promote", "curator", name, "审核失败：" + error.getMessage()));
        }
        if (decision == null) {
            return Mono.just(invalid("promote", "curator", name, "审核未返回决定。"));
        }
        return decision.map(value -> apply(entry, value));
    }

    public Result archive(String name, String actor) {
        if (!StringUtils.hasText(name)) {
            return invalid("archive", "curator", name, "技能名称不能为空。");
        }
        if (!StringUtils.hasText(actor)) {
            return invalid("archive", "curator", name, "操作人不能为空。");
        }
        try {
            if (!published.archive(name)) {
                return invalid("archive", actor, name, "技能不存在。");
            }
        } catch (RuntimeException error) {
            return invalid("archive", actor, name, "归档技能失败：" + error.getMessage());
        }
        usageStore.setState(name, SkillUsageRecord.State.ARCHIVED);
        auditLog.append(new SkillAuditLog.Entry(Instant.now(), actor, "archive", name, "archived", null));
        return new Result(Status.ARCHIVED, name, "技能已归档。");
    }

    private Result apply(SkillEntry entry, SkillPromotionGate.Decision decision) {
        if (decision instanceof SkillPromotionGate.Decision.Approve approve) {
            return approve(entry, approve);
        }
        if (decision instanceof SkillPromotionGate.Decision.Reject reject) {
            auditLog.append(new SkillAuditLog.Entry(
                    Instant.now(), reject.reviewerId(), "promote", entry.name(), "rejected", reject.reason()));
            return new Result(Status.REJECTED, entry.name(), reject.reason());
        }
        if (decision instanceof SkillPromotionGate.Decision.Defer defer) {
            auditLog.append(new SkillAuditLog.Entry(
                    Instant.now(), "curator", "promote", entry.name(), "deferred", defer.reason()));
            return new Result(Status.DEFERRED, entry.name(), defer.reason());
        }
        return invalid("promote", "curator", entry.name(), "未知的审核决定。");
    }

    private Result approve(SkillEntry entry, SkillPromotionGate.Decision.Approve approve) {
        try {
            published.save(entry);
        } catch (RuntimeException error) {
            return invalid(
                    "promote", approve.reviewerId(), entry.name(), "发布技能失败：" + error.getMessage());
        }
        try {
            drafts.archive(entry.name());
        } catch (RuntimeException error) {
            log.warn("归档技能草稿 {} 失败：{}", entry.name(), error.getMessage());
        }
        usageStore.markPromoted(entry.name(), approve.reviewerId(), approve.environments());
        auditLog.append(new SkillAuditLog.Entry(
                Instant.now(),
                approve.reviewerId(),
                "promote",
                entry.name(),
                "approved",
                detail(approve.environments())));
        return new Result(Status.APPROVED, entry.name(), "技能已发布。");
    }

    private Result invalid(String operation, String actor, @Nullable String name, String message) {
        var target = target(name);
        auditLog.append(new SkillAuditLog.Entry(
                Instant.now(),
                StringUtils.hasText(actor) ? actor : "curator",
                operation,
                target,
                "invalid",
                message));
        return new Result(Status.INVALID, target, message);
    }

    private static String target(@Nullable String name) {
        return StringUtils.hasText(name) ? name : "unknown";
    }

    private static @Nullable String detail(List<String> environments) {
        return environments == null || environments.isEmpty()
                ? null
                : "environments=" + String.join(",", environments);
    }

    public record Result(Status status, String name, String message) {}

    public enum Status {
        APPROVED,
        REJECTED,
        DEFERRED,
        ARCHIVED,
        INVALID
    }
}