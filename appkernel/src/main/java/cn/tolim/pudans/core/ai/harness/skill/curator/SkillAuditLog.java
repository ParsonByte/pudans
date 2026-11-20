package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public interface SkillAuditLog {

    void append(Entry entry);

    List<Entry> entries();

    record Entry(
            Instant at,
            String actor,
            String operation,
            String target,
            String decision,
            @Nullable String detail) {

        public Entry {
            at = at == null ? Instant.now() : at;
            if (!StringUtils.hasText(actor)) {
                throw new IllegalArgumentException("审计操作人不能为空。");
            }
            if (!StringUtils.hasText(operation)) {
                throw new IllegalArgumentException("审计操作不能为空。");
            }
            if (!StringUtils.hasText(target)) {
                throw new IllegalArgumentException("审计目标不能为空。");
            }
            if (!StringUtils.hasText(decision)) {
                throw new IllegalArgumentException("审计决定不能为空。");
            }
            detail = StringUtils.hasText(detail) ? detail : null;
        }
    }
}