package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository.KeyUsage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGatewayKeyServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiChatRequestRepository chatRequestRepository;

    @Mock
    AiGatewayRateLimiter rateLimiter;

    private AiGatewayKeyService service;

    @BeforeEach
    void setUp() {
        service = new AiGatewayKeyService(client, chatRequestRepository, rateLimiter);
        service.setClock(Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void keepsNonPatCallersUnrestricted() {
        StepVerifier.create(service.authorize(invocation(null), model()))
                .assertNext(access -> {
                    assertThat(access.callerKey()).isNull();
                    assertThat(access.allows("anything")).isTrue();
                })
                .verifyComplete();

        verifyNoInteractions(client);
        verifyNoInteractions(chatRequestRepository);
    }

    @Test
    void rejectsDisabledGatewayAccess() {
        when(client.get(PersonalAccessToken.class, "pat-1"))
                .thenReturn(Mono.just(pat(gateway(false, List.of(), null, List.of()))));

        StepVerifier.create(service.authorize(invocation("pat-1"), model()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .extracting(value -> ((ApiException) value).getCode())
                        .isEqualTo(AiGatewayKeyService.CODE_KEY_DENIED))
                .verify();
    }

    @Test
    void rejectsModelsOutsideTheWhitelist() {
        when(client.get(PersonalAccessToken.class, "pat-1"))
                .thenReturn(Mono.just(pat(gateway(true, List.of("other-model"), null, List.of()))));

        StepVerifier.create(service.authorize(invocation("pat-1"), model()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .extracting(value -> ((ApiException) value).getCode())
                        .isEqualTo(AiGatewayKeyService.CODE_KEY_DENIED))
                .verify();
    }

    @Test
    void allowsWhitelistedModelCodesAndExposesConcurrency() {
        when(client.get(PersonalAccessToken.class, "pat-1"))
                .thenReturn(Mono.just(pat(gateway(true, List.of("gpt-test"), 4, List.of()))));

        StepVerifier.create(service.authorize(invocation("pat-1"), model()))
                .assertNext(access -> {
                    assertThat(access.callerKey()).isEqualTo("pat-1");
                    assertThat(access.maxConcurrency()).isEqualTo(4);
                    assertThat(access.allows("gpt-test")).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void rejectsCallersThatExceedTheConfiguredBudget() {
        var limit = new PersonalAccessToken.Limit();
        limit.setPeriod(PersonalAccessToken.LimitPeriod.DAY);
        limit.setTokens(100L);
        when(client.get(PersonalAccessToken.class, "pat-1"))
                .thenReturn(Mono.just(pat(gateway(true, List.of(), null, List.of(limit)))));
        when(chatRequestRepository.aggregateKeyUsage(
                        isNull(), eq("pat-1"), any(), any(), any()))
                .thenReturn(Mono.just(new KeyUsage(1, 100, BigDecimal.ZERO)));

        StepVerifier.create(service.authorize(invocation("pat-1"), model()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .satisfies(value -> {
                            var api = (ApiException) value;
                            assertThat(api.getCode()).isEqualTo(AiGatewayRateLimiter.CODE_KEY_LIMITED);
                            assertThat(api.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        }))
                .verify();
    }

    @Test
    void budgetWindowsStartAtBeijingDayWeekAndMonthBoundaries() {
        service.setClock(Clock.fixed(Instant.parse("2026-11-19T16:00:00Z"), ZoneOffset.UTC));
        var limits = List.of(
                limit(PersonalAccessToken.LimitPeriod.DAY),
                limit(PersonalAccessToken.LimitPeriod.WEEK),
                limit(PersonalAccessToken.LimitPeriod.MONTH),
                limit(PersonalAccessToken.LimitPeriod.TOTAL));
        when(client.get(PersonalAccessToken.class, "pat-1"))
                .thenReturn(Mono.just(pat(gateway(true, List.of(), null, limits))));
        when(chatRequestRepository.aggregateKeyUsage(any(), eq("pat-1"), any(), any(), any()))
                .thenReturn(Mono.just(new KeyUsage(0, 0, BigDecimal.ZERO)));

        StepVerifier.create(service.authorize(invocation("pat-1"), model()))
                .expectNextCount(1)
                .verifyComplete();

        var since = ArgumentCaptor.forClass(Instant.class);
        verify(chatRequestRepository, times(4))
                .aggregateKeyUsage(any(), eq("pat-1"), since.capture(), any(), any());
        assertThat(since.getAllValues())
                .containsExactly(
                        Instant.parse("2026-11-19T16:00:00Z"),
                        Instant.parse("2026-11-15T16:00:00Z"),
                        Instant.parse("2026-10-31T16:00:00Z"),
                        null);
    }

    @Test
    void acquiresTheKeyConcurrencyPermitFromTheRateLimiter() {
        var permit = new AiGatewayRateLimiter.Permit(List.of(
                new AiGatewayRateLimiter.Slot("key", true)));
        when(rateLimiter.acquireKey("pat-1", 2)).thenReturn(Mono.just(permit));

        StepVerifier.create(service.acquire(new AiGatewayKeyService.Access("pat-1", List.of(), 2)))
                .assertNext(value -> assertThat(value).isSameAs(permit))
                .verifyComplete();
    }

    private static PersonalAccessToken.Limit limit(PersonalAccessToken.LimitPeriod period) {
        var limit = new PersonalAccessToken.Limit();
        limit.setPeriod(period);
        limit.setTokens(100L);
        return limit;
    }

    private static PersonalAccessToken pat(PersonalAccessToken.Gateway gateway) {
        var pat = new PersonalAccessToken();
        var metadata = new Metadata();
        metadata.setName("pat-1");
        pat.setMetadata(metadata);
        var spec = new PersonalAccessToken.Spec();
        spec.setUsername("alice");
        spec.setTokenId("token-id");
        spec.setGateway(gateway);
        pat.setSpec(spec);
        return pat;
    }

    private static PersonalAccessToken.Gateway gateway(
            boolean enabled,
            List<String> models,
            Integer maxConcurrency,
            List<PersonalAccessToken.Limit> limits) {
        var gateway = new PersonalAccessToken.Gateway();
        gateway.setEnabled(enabled);
        gateway.setModels(models);
        gateway.setMaxConcurrency(maxConcurrency);
        gateway.setLimits(limits);
        return gateway;
    }

    private static AiGatewayInvocation invocation(String callerKey) {
        var ir = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hi", null)))),
                List.of(),
                new AiRequestParams(null, null, 128, null, null, null, null, null),
                false);
        return new AiGatewayInvocation(
                AiProvider.Protocol.OPENAI_CHAT,
                ir,
                "user-1",
                "pudans-chat",
                "session-1",
                null,
                "req-1",
                "idem-1",
                null,
                null,
                null,
                null,
                null,
                callerKey);
    }

    private static AiModel model() {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName("model-a");
        model.setMetadata(metadata);
        model.getSpec().setProvider("provider-a");
        model.getSpec().setModelCode("gpt-test");
        return model;
    }
}