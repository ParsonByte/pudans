package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.core.ai.export.CsvSupport;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestRow;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

final class UsageCsvExporter {

    static final String HEADER =
            "time,requestId,agent,provider,model,account,mode,routeGroup,status,"
                    + "inputTokens,outputTokens,cacheTokens,totalTokens,points,latencyMs,ttftMs,"
                    + "cacheHitRate,retried,servedEffort\n";

    private UsageCsvExporter() {}

    static String line(RequestRow row) {
        return CsvSupport.line(
                row.createdAt().toString(),
                row.requestId(),
                row.agent(),
                row.provider(),
                row.model(),
                row.account(),
                row.mode(),
                row.routeGroup(),
                row.status(),
                integer(row.inputTokens()),
                integer(row.outputTokens()),
                integer(row.cacheTokens()),
                integer(row.totalTokens()),
                decimal(row.points()),
                integer(row.latencyMs()),
                integer(row.ttftMs()),
                AiUsageService.cacheHitRate(row.inputTokens(), row.cacheTokens()).toPlainString(),
                Boolean.toString(row.retried()),
                nullToEmpty(AiUsageService.servedEffort(row.servedEffort(), row.tier())));
    }

    private static String integer(@Nullable Integer value) {
        return value == null ? "" : value.toString();
    }

    private static String decimal(@Nullable BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}