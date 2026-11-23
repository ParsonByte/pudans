"use client";

import type { AuditResultStat } from "@pudans-dev/api-client";

import { RESULT_ORDER, resultLabel } from "../composables/_results";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";
import { DonutBreakdown } from "./donut-breakdown";

type ResultBreakdownProps = {
    centerLabel?: string;
    emptyText?: string;
    results?: AuditResultStat[];
    valueUnit?: string;
};

const RESULT_TONES = {
    DENIED: "warning",
    FAILURE: "red",
    SUCCESS: "green",
} as const;

export function ResultBreakdown({
    centerLabel = "条记录",
    emptyText = "暂无结果分布",
    results,
    valueUnit = "条记录",
}: ResultBreakdownProps) {
    const { tone } = useDashboardTheme();
    const counts = new Map((results ?? []).map((item) => [item.result ?? "", item.records ?? 0]));
    const order = [...RESULT_ORDER, ...[...counts.keys()].filter((key) => !RESULT_ORDER.includes(key))];
    const slices = order
        .filter((result) => (counts.get(result) ?? 0) > 0)
        .map((result) => ({
            color: tone(RESULT_TONES[result as keyof typeof RESULT_TONES] ?? "blue").base,
            key: result,
            label: resultLabel(result),
            value: counts.get(result) ?? 0,
        }));

    return <DonutBreakdown centerLabel={centerLabel} emptyText={emptyText} slices={slices} valueUnit={valueUnit} />;
}
