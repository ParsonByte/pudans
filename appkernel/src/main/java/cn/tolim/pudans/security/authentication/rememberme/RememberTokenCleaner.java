package cn.tolim.pudans.security.authentication.rememberme;

import static cn.tolim.pudans.extension.contract.index.query.Queries.and;
import static cn.tolim.pudans.extension.contract.index.query.Queries.isNull;
import static cn.tolim.pudans.extension.contract.index.query.Queries.lessThan;

import cn.tolim.pudans.security.contract.RememberMeToken;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.router.selector.FieldSelector;
import cn.tolim.pudans.infra.ReactiveExtensionPaginatedOperator;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class RememberTokenCleaner {
    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;
    private final ReactiveExtensionPaginatedOperator paginatedOperator;
    private final RememberMeCookieResolver rememberMeCookieResolver;


    // 批量删除超过有效期的令牌，防止历史数据无限增长。
    public void cleanUpExpiredTokens() {
        paginatedOperator
                .deleteInitialBatch(RememberMeToken.class, getExpiredTokensListOptions())
                .then()
                .block(BLOCKING_TIMEOUT);
        log.info("已清理过期的记住我令牌。");
    }

    // 只选择未删除且最后使用时间早于阈值的记录。
    ListOptions getExpiredTokensListOptions() {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(and(
                isNull("metadata.deletionTimestamp"),
                lessThan("spec.lastUsed", getExpirationThreshold().toString()))));
        return listOptions;
    }

    // 以当前时间倒推有效期，作为清理边界。
    protected Instant getExpirationThreshold() {
        return Instant.now().minus(getTokenValidity());
    }

    // 清理有效期与 Cookie 有效期保持一致，避免提前失效。
    protected Duration getTokenValidity() {
        return rememberMeCookieResolver.getCookieMaxAge();
    }
}
