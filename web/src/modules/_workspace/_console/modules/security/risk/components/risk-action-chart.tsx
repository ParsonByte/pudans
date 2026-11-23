"use client";

import type { RiskSceneStat } from "@pudans-dev/api-client";

import { DonutBreakdown } from "../../../../components/donut-breakdown";
import { useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { RISK_ACTION_LABELS } from "../composables/_risk";

type RiskActionChartProps = {
    scenes?: RiskSceneStat[];
};

const ACTION_ORDER = ["BLOCK", "ALERT"];

export function RiskActionChart({ scenes }: RiskActionChartProps) {
    const { tone } = useDashboardTheme();
    const counts = new Map<string, number>();
    for (const scene of scenes ?? []) {
        if (scene.action) {
            counts.set(scene.action, (counts.get(scene.action) ?? 0) + 1);
        }
    }

    const slices = ACTION_ORDER.filter((action) => (counts.get(action) ?? 0) > 0).map((action) => ({
        color: tone(action === "BLOCK" ? "red" : "warning").base,
        key: action,
        label: RISK_ACTION_LABELS[action] ?? action,
        value: counts.get(action) ?? 0,
    }));

    return (
        <DonutBreakdown
            centerLabel="条规则"
            emptyText="暂无规则动作分布"
            layout="horizontal"
            size="compact"
            slices={slices}
            valueUnit="条规则"
        />
    );
}
