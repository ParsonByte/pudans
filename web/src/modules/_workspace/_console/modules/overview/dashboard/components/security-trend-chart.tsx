"use client";

import { useMemo } from "react";

import { TrendChart } from "../../../../components/trend-chart";
import { type DashboardData, mergeCreationTrend } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type SecurityTrendChartProps = {
    overview: DashboardData;
};

export function SecurityTrendChart({ overview }: SecurityTrendChartProps) {
    const { tone } = useDashboardTheme();
    const data = useMemo(() => mergeCreationTrend(overview), [overview]);

    return (
        <TrendChart
            data={data}
            emptyText="暂无风险规则或 Webhook 趋势"
            height={248}
            series={[
                { color: tone("blue").base, key: "riskRules", label: "风险规则", type: "area" },
                { color: tone("orange").base, key: "webhooks", label: "Webhook", type: "line" },
            ]}
        />
    );
}
