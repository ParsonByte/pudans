package cn.tolim.pudans.core.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiPeakWindow;
import cn.tolim.pudans.contract.ai.AiPriceRule;
import cn.tolim.pudans.contract.ai.dto.AiPriceRuleCreateRequest;
import cn.tolim.pudans.core.ai.catalog.AiCapabilityRegistry;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiPriceRuleRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiUsageRecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiPriceRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");

    @Mock
    AiPriceRuleRepository rules;

    @Mock
    AiModelRepository models;

    @Mock
    AiUsageRecordRepository usageRecords;

    @Mock
    ObjectProvider<AiCapabilityRegistry> capabilityRegistry;

    private AiPriceRules service;

    @BeforeEach
    void setUp() {
        service = new AiPriceRules(rules, models, usageRecords, capabilityRegistry);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsAnEffectiveRangeThatOverlapsTheSameModel() {
        var model = model();
        when(models.findById(model.id())).thenReturn(Mono.just(model));
        when(rules.findByModelId(model.id())).thenReturn(Flux.just(rule(
                model,
                Instant.parse("2026-11-25T00:00:00Z"),
                Instant.parse("2026-11-25T03:00:00Z"))));

        StepVerifier.create(service.create(request(model.id(), NOW, NOW.plusSeconds(3600))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-price-rule-overlap")))
                .verify();

        verify(rules, never()).insert(any());
    }

    @Test
    void createsANonOverlappingRuleWithTheModelLogicalNameAsTarget() {
        var model = model();
        var from = Instant.parse("2026-11-25T05:00:00Z");
        when(models.findById(model.id())).thenReturn(Mono.just(model));
        when(rules.findByModelId(model.id())).thenReturn(Flux.just(rule(
                model,
                Instant.parse("2026-11-25T00:00:00Z"),
                Instant.parse("2026-11-25T04:00:00Z"))));
        when(rules.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.create(request(model.id(), from, from.plusSeconds(3600))).block();

        assertThat(result).isNotNull();
        assertThat(result.targetKey()).isEqualTo(model.logicalName());
        assertThat(result.effectiveFrom()).isEqualTo(from);
    }

    @Test
    void rejectsUnsupportedPeakWindowDays() {
        var model = model();
        var request = request(model.id(), NOW, null, List.of(
                new AiPeakWindow(List.of("holiday"), "14:00", "18:00", "Asia/Shanghai")));

        StepVerifier.create(service.create(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-peak-window-invalid")))
                .verify();
    }

    @Test
    void rejectsMcpPricingForAnUnavailableTool() {
        var registry = new AiCapabilityRegistry() {
            @Override
            public Flux<cn.tolim.pudans.contract.ai.dto.AiToolDescriptor> tools() {
                return Flux.just(new cn.tolim.pudans.contract.ai.dto.AiToolDescriptor(
                        "test-tool", "Test", null, false));
            }
        };
        when(capabilityRegistry.getIfAvailable()).thenReturn(registry);
        var request = new AiPriceRuleCreateRequest(
                "mcp_tool",
                null,
                "test-tool",
                "mcp_call",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                NOW,
                null);

        StepVerifier.create(service.create(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-mcp-tool-not-found")))
                .verify();
    }

    private static AiModel model() {
        return new AiModel(
                UUID.randomUUID(),
                "test-model",
                "Test Model",
                null,
                Map.of("chat", Map.of()),
                null,
                null,
                Map.of(),
                "active",
                NOW,
                NOW);
    }

    private static AiPriceRule rule(AiModel model, Instant from, Instant to) {
        return new AiPriceRule(
                UUID.randomUUID(),
                "model",
                model.logicalName(),
                model.id(),
                null,
                "token",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                from,
                to,
                NOW,
                NOW);
    }

    private static AiPriceRuleCreateRequest request(
            UUID modelId, Instant from, Instant to) {
        return request(modelId, from, to, List.of());
    }

    private static AiPriceRuleCreateRequest request(
            UUID modelId, Instant from, Instant to, List<AiPeakWindow> peakWindows) {
        return new AiPriceRuleCreateRequest(
                "model",
                modelId,
                null,
                "token",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                peakWindows,
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                from,
                to);
    }
}
