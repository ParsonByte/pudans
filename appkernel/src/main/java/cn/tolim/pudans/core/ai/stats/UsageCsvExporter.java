package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.core.ai.export.CsvSupport;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiUsageRepository.RequestRow;
import org.jspecify.annotations.Nullable;

final class UsageCsvExporter {

    static final String HEADER =
            "time,requestId,agent,provider,model,account,mode,routeGroup,status,"
                    + "inputTokens,outputTokens,cacheTokens,totalTokens,units,latencyMs,ttftMs,"
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
                number(row.units()),
                integer(row.latencyMs()),
                integer(row.ttftMs()),
                AiUsage.cacheHitRate(row.inputTokens(), row.cacheTokens()).toPlainString(),
                Boolean.toString(row.retried()),
                nullToEmpty(AiUsage.servedEffort(row.servedEffort(), row.tier())));
    }

    private static String integer(@Nullable Integer value) {
        return value == null ? "" : value.toString();
    }

    private static String number(@Nullable Long value) {
        return value == null ? "" : value.toString();
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}