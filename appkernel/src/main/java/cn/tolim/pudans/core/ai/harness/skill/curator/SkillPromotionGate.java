package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.time.Duration;
import java.util.List;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface SkillPromotionGate {

    Mono<Decision> review(SkillCandidate candidate);

    sealed interface Decision {

        record Approve(String reviewerId, List<String> environments) implements Decision {

            public Approve {
                if (!StringUtils.hasText(reviewerId)) {
                    throw new IllegalArgumentException("审核人不能为空。");
                }
                environments = environments == null ? List.of() : List.copyOf(environments);
            }
        }

        record Reject(String reviewerId, String reason) implements Decision {

            public Reject {
                if (!StringUtils.hasText(reviewerId)) {
                    throw new IllegalArgumentException("审核人不能为空。");
                }
                if (!StringUtils.hasText(reason)) {
                    throw new IllegalArgumentException("拒绝原因不能为空。");
                }
            }
        }

        record Defer(String reason, Duration retryAfter) implements Decision {

            public Defer {
                if (!StringUtils.hasText(reason)) {
                    throw new IllegalArgumentException("延迟原因不能为空。");
                }
                if (retryAfter == null || retryAfter.isNegative()) {
                    throw new IllegalArgumentException("重试间隔不能为负数。");
                }
            }
        }
    }
}