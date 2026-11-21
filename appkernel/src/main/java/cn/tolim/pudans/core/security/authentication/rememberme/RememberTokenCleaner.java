package cn.tolim.pudans.core.security.authentication.rememberme;

import static cn.tolim.pudans.contract.extension.index.query.Queries.and;
import static cn.tolim.pudans.contract.extension.index.query.Queries.isNull;
import static cn.tolim.pudans.contract.extension.index.query.Queries.lessThan;

import cn.tolim.pudans.contract.security.RememberMeToken;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.router.selector.FieldSelector;
import cn.tolim.pudans.core.infra.ReactiveExtensionPaginatedOperator;
import cn.tolim.pudans.core.infra.utils.ReactiveUtils;
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


    public void cleanUpExpiredTokens() {
        paginatedOperator
                .deleteInitialBatch(RememberMeToken.class, getExpiredTokensListOptions())
                .then()
                .block(BLOCKING_TIMEOUT);
        log.info("已清理过期的记住我令牌。");
    }

    ListOptions getExpiredTokensListOptions() {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(and(
                isNull("metadata.deletionTimestamp"),
                lessThan("spec.lastUsed", getExpirationThreshold().toString()))));
        return listOptions;
    }

    protected Instant getExpirationThreshold() {
        return Instant.now().minus(getTokenValidity());
    }

    protected Duration getTokenValidity() {
        return rememberMeCookieResolver.getCookieMaxAge();
    }
}
