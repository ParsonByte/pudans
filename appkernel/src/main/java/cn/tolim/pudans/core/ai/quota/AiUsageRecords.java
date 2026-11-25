package cn.tolim.pudans.core.ai.quota;

import cn.tolim.pudans.contract.ai.AiUsageRecord;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiUsageRecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiUsageRecords {

    private final AiUsageRecordRepository records;

    private final AiPricing pricing;

    private Clock clock = Clock.systemUTC();

    public Mono<AiUsageRecord> recordTokens(
            UsageContext context, AiPricing.TokenUsage usage) {
        return idempotent(context, () -> pricing
                .quoteTokens(context.modelId(), usage, context.occurredAt())
                .map(quote -> record(
                        context,
                        BigDecimal.ZERO,
                        quote.credits(),
                        0,
                        0,
                        quote.pricingRuleId(),
                        "token")));
    }

    public Mono<AiUsageRecord> recordUnits(
            UsageContext context, BigDecimal units) {
        var resolvedUnits = units == null ? BigDecimal.ZERO : units;
        return idempotent(context, () -> pricing
                .quoteUnits(context.modelId(), resolvedUnits, context.occurredAt())
                .map(quote -> record(
                        context,
                        resolvedUnits,
                        quote.credits(),
                        0,
                        0,
                        quote.pricingRuleId(),
                        "unit")));
    }

    public Mono<AiUsageRecord> recordMcp(
            UsageContext context, int calls) {
        return idempotent(context, () -> pricing
                .quoteMcp(context.mcpToolKey(), calls, context.occurredAt())
                .map(quote -> record(
                        context,
                        BigDecimal.ZERO,
                        quote.credits(),
                        0,
                        calls,
                        quote.pricingRuleId(),
                        "mcp")));
    }

    private Mono<AiUsageRecord> idempotent(
            UsageContext context, java.util.function.Supplier<Mono<AiUsageRecord>> action) {
        return records
                .findByIdempotencyKey(context.idempotencyKey())
                .switchIfEmpty(Mono.defer(action));
    }

    private AiUsageRecord record(
            UsageContext context,
            BigDecimal units,
            BigDecimal credits,
            int toolCalls,
            int mcpCalls,
            UUID pricingRuleId,
            String windowKind) {
        var now = Instant.now(clock);
        return new AiUsageRecord(
                UUID.randomUUID(),
                AiSupport.requiredText(
                        context.ownerId(),
                        "ai-usage-owner-required",
                        "用量归属用户不能为空。"),
                AiSupport.requiredText(
                        context.workspaceId(),
                        "ai-usage-workspace-required",
                        "用量归属工作区不能为空。"),
                context.runId(),
                context.taskId(),
                context.toolCallId(),
                context.modelId(),
                context.channelId(),
                context.mcpToolKey(),
                units,
                credits,
                toolCalls,
                mcpCalls,
                pricingRuleId,
                windowKind,
                context.occurredAt() == null ? now : context.occurredAt(),
                context.status() == null ? AiSupport.SETTLED : context.status(),
                AiSupport.requiredText(
                        context.idempotencyKey(),
                        "ai-usage-idempotency-required",
                        "用量流水幂等键不能为空。"),
                now);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public record UsageContext(
            String ownerId,
            String workspaceId,
            UUID runId,
            UUID taskId,
            UUID toolCallId,
            UUID modelId,
            UUID channelId,
            String mcpToolKey,
            Instant occurredAt,
            String status,
            String idempotencyKey) {}
}
