import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";

import { MetricCard as BaseMetricCard } from "@/components/ui";

import {
    type DashboardMetric,
    type MetricDefinition,
    formatMetricValue,
    metricChange,
} from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type MetricCardProps = {
    definition: MetricDefinition;
    metric: DashboardMetric;
};

export function MetricCard({ definition, metric }: MetricCardProps) {
    const { token, tone } = useDashboardTheme();
    const Icon = definition.icon;
    const change = metricChange(metric.value, metric.previousValue, definition);
    const ChangeIcon = change.direction === "up" ? ArrowUpRight : change.direction === "down" ? ArrowDownRight : Minus;
    const changeColor =
        change.direction === "flat" ? token.colorTextTertiary : change.positive ? tone("green").base : tone("red").base;
    const colors = tone(definition.tone);

    return (
        <BaseMetricCard
            badge={
                <span
                    className="rounded-full px-2 py-0.5 text-[11px] font-medium"
                    style={{ background: token.colorFillTertiary, color: token.colorTextSecondary }}
                >
                    实时
                </span>
            }
            footer={
                <span className="flex items-center gap-1" style={{ color: changeColor }}>
                    <ChangeIcon size={13} strokeWidth={2.4} />
                    <span>{change.text}</span>
                </span>
            }
            icon={<Icon size={20} strokeWidth={2.1} />}
            label={definition.label}
            tone={colors}
            value={formatMetricValue(metric.value, definition.format)}
        />
    );
}
