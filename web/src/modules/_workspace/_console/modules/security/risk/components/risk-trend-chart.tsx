"use client";

import type { RiskTimelinePoint } from "@pudans-dev/api-client";

import { TrendChart } from "../../../../components/trend-chart";
import { formatDateTick } from "../../../../composables/_format";
import { useDashboardTheme } from "../../../overview/dashboard/composables/_theme";

type RiskTrendChartProps = {
    points?: RiskTimelinePoint[];
};

export function RiskTrendChart({ points }: RiskTrendChartProps) {
    const { tone } = useDashboardTheme();
    const data = (points ?? []).map((point) => ({
        alerts: point.alerts ?? 0,
        blocked: point.blocked ?? 0,
        date: point.date,
        events: point.events ?? 0,
        label: formatDateTick(point.date),
    }));

    return (
        <TrendChart
            data={data}
            emptyText="暂无风险趋势数据"
            series={[
                { color: tone("blue").base, key: "events", label: "事件总量", type: "area" },
                { color: tone("red").base, key: "blocked", label: "阻断", type: "line" },
                { color: tone("warning").base, key: "alerts", label: "告警", type: "line" },
            ]}
        />
    );
}
