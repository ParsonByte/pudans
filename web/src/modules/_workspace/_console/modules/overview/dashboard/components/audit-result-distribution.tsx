"use client";

import { Empty, Progress } from "tdesign-react";

import { formatCount, formatPercent } from "../../../../composables/_format";
import { type DashboardResultCount, resultTone } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type AuditResultDistributionProps = {
    results?: DashboardResultCount[];
};

export function AuditResultDistribution({ results }: AuditResultDistributionProps) {
    const { token, tone } = useDashboardTheme();
    const items = [...(results ?? [])]
        .filter((item) => (item.count ?? 0) > 0)
        .sort((left, right) => (right.count ?? 0) - (left.count ?? 0));
    const total = items.reduce((sum, item) => sum + (item.count ?? 0), 0);

    if (items.length === 0) {
        return <Empty description="暂无审计结果" size="small" />;
    }

    return (
        <div className="space-y-5 pt-1">
            {items.map((item) => {
                const result = resultTone(item.result ?? "");
                const colors = tone(result.tone);
                const count = item.count ?? 0;
                const percentage = total > 0 ? (count / total) * 100 : 0;

                return (
                    <div key={item.result}>
                        <div className="mb-2 flex items-center gap-2.5">
                            <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: colors.base }} />
                            <span className="flex-1 text-sm" style={{ color: token.colorTextSecondary }}>
                                {result.label}
                            </span>
                            <span className="text-sm font-semibold tabular-nums" style={{ color: token.colorText }}>
                                {formatCount(count)}
                            </span>
                            <span
                                className="w-12 text-right text-xs tabular-nums"
                                style={{ color: token.colorTextTertiary }}
                            >
                                {formatPercent(count, total)}
                            </span>
                        </div>
                        <Progress
                            color={colors.base}
                            label={false}
                            percentage={percentage}
                            size="small"
                            trackColor={token.colorFillSecondary}
                        />
                    </div>
                );
            })}
        </div>
    );
}
