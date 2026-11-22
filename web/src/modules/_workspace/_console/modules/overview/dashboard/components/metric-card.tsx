import { Card } from "antd";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";

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
        <Card
            className="relative h-full overflow-hidden !rounded-3xl transition-transform duration-300 hover:-translate-y-0.5"
            style={{
                background: token.colorBgContainer,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowTertiary,
            }}
            styles={{ body: { padding: 20 } }}
        >
            <div className="flex flex-col gap-5">
                <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3">
                        <span
                            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl"
                            style={{ backgroundColor: colors.soft, color: colors.base }}
                        >
                            <Icon size={20} strokeWidth={2.1} />
                        </span>
                        <span className="text-sm font-medium" style={{ color: token.colorTextSecondary }}>
                            {definition.label}
                        </span>
                    </div>
                    <span
                        className="rounded-full px-2 py-0.5 text-[11px] font-medium"
                        style={{ background: token.colorFillTertiary, color: token.colorTextSecondary }}
                    >
                        实时
                    </span>
                </div>
                <div>
                    <div className="text-3xl font-semibold tracking-tight" style={{ color: token.colorText }}>
                        {formatMetricValue(metric.value, definition.format)}
                    </div>
                    <div className="mt-2 flex items-center gap-1 text-xs font-medium" style={{ color: changeColor }}>
                        <ChangeIcon size={13} strokeWidth={2.4} />
                        <span>{change.text}</span>
                    </div>
                </div>
            </div>
        </Card>
    );
}
