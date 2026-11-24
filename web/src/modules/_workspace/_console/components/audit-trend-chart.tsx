"use client";

import type { AuditTimelinePoint } from "@pudans-dev/api-client";

import { formatDateTick } from "../composables/_format";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";
import { TrendChart } from "./trend-chart";

type AuditTrendChartProps = {
    points?: AuditTimelinePoint[];
};

export function AuditTrendChart({ points }: AuditTrendChartProps) {
    const { tone } = useDashboardTheme();
    const data = (points ?? []).map((point) => ({
        date: point.date,
        denied: point.denied ?? 0,
        failure: point.failure ?? 0,
        label: formatDateTick(point.date),
        records: point.records ?? 0,
        success: point.success ?? 0,
    }));

    return (
        <TrendChart
            data={data}
            emptyText="暂无审计趋势数据"
            series={[
                { color: tone("blue").base, key: "records", label: "记录总量", type: "area" },
                { color: tone("green").base, key: "success", label: "成功", type: "line" },
                { color: tone("red").base, key: "failure", label: "失败", type: "line" },
                { color: tone("warning").base, key: "denied", label: "拒绝", type: "line" },
            ]}
        />
    );
}
