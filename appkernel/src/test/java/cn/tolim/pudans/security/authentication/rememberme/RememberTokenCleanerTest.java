package cn.tolim.pudans.security.authentication.rememberme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.security.resource.RememberMeToken;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.index.query.AndCondition;
import cn.tolim.pudans.contract.extension.index.query.IsNullCondition;
import cn.tolim.pudans.contract.extension.index.query.LessThanCondition;
import cn.tolim.pudans.infra.ReactiveExtensionPaginatedOperator;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class RememberTokenCleanerTest {

    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(7);

    @Mock
    ReactiveExtensionPaginatedOperator paginatedOperator;

    @Mock
    RememberMeCookieResolver rememberMeCookieResolver;

    @Test
    void expiresTokensWhoseLastUseIsOlderThanTheCookieMaxAge() {
        when(rememberMeCookieResolver.getCookieMaxAge()).thenReturn(COOKIE_MAX_AGE);
        var cleaner = new RememberTokenCleaner(paginatedOperator, rememberMeCookieResolver);

        var condition = cleaner.getExpiredTokensListOptions().getFieldSelector().query();

        assertThat(condition).isInstanceOf(AndCondition.class);
        var and = (AndCondition) condition;
        assertThat(and.left()).isEqualTo(new IsNullCondition("metadata.deletionTimestamp"));
        assertThat(and.right()).isInstanceOf(LessThanCondition.class);
        var lastUsed = (LessThanCondition) and.right();
        assertThat(lastUsed.indexName()).isEqualTo("spec.lastUsed");
        assertThat(Duration.between(Instant.parse((String) lastUsed.upperBound()), Instant.now().minus(COOKIE_MAX_AGE))
                        .abs())
                .isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void cleanupDeletesTheExpiredTokens() {
        when(rememberMeCookieResolver.getCookieMaxAge()).thenReturn(COOKIE_MAX_AGE);
        when(paginatedOperator.deleteInitialBatch(eq(RememberMeToken.class), any(ListOptions.class)))
                .thenReturn(Flux.empty());
        var cleaner = new RememberTokenCleaner(paginatedOperator, rememberMeCookieResolver);

        cleaner.cleanUpExpiredTokens();

        verify(paginatedOperator).deleteInitialBatch(eq(RememberMeToken.class), any(ListOptions.class));
    }
}
