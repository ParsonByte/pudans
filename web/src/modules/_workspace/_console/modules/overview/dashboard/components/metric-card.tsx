import { ArrowDownIcon, ArrowUpIcon, MinusIcon } from "tdesign-icons-react";

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
    const ChangeIcon =
        change.direction === "up" ? ArrowUpIcon : change.direction === "down" ? ArrowDownIcon : MinusIcon;
    const changeColor =
        change.direction === "flat" ? token.colorTextTertiary : change.positive ? tone("green").base : tone("red").base;
    const colors = tone(definition.tone);

    return (
        <BaseMetricCard
            footer={
                <span className="flex items-center gap-1" style={{ color: changeColor }}>
                    <ChangeIcon size={13} />
                    <span>{change.text}</span>
                </span>
            }
            icon={<Icon size={20} />}
            label={definition.label}
            tone={colors}
            value={formatMetricValue(metric.value, definition.format)}
        />
    );
}
