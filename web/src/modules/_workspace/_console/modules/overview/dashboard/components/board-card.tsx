import { ArrowDownIcon, ArrowUpIcon, MinusIcon } from "tdesign-icons-react";
import { Card } from "tdesign-react";

import { formatCount } from "../../../../composables/_format";
import { type DashboardMetric, type MetricDefinition, metricChange } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type BoardCardProps = {
    caption: string;
    dark?: boolean;
    definition: MetricDefinition;
    metric: DashboardMetric;
};

export function BoardCard({ caption, dark = false, definition, metric }: BoardCardProps) {
    const { token, tone } = useDashboardTheme();
    const Icon = definition.icon;
    const change = metricChange(metric.value, metric.previousValue, definition);
    const ChangeIcon =
        change.direction === "up" ? ArrowUpIcon : change.direction === "down" ? ArrowDownIcon : MinusIcon;
    const changeColor = dark
        ? "var(--td-text-color-anti)"
        : change.direction === "flat"
          ? token.colorTextTertiary
          : change.positive
            ? tone("green").base
            : tone("red").base;
    const changeBackground = dark
        ? "rgb(255 255 255 / 16%)"
        : change.direction === "flat"
          ? token.colorFillSecondary
          : change.positive
            ? token.colorSuccessBg
            : token.colorErrorBg;
    const value = definition.format === "percent" ? `${metric.value.toFixed(1)}%` : formatCount(metric.value);

    return (
        <Card
            bordered={false}
            className="h-full"
            style={dark ? { backgroundColor: "var(--td-brand-color)" } : undefined}
            bodyStyle={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
                minHeight: 142,
                padding: 24,
            }}
        >
            <div className="flex items-start justify-between gap-3">
                <span className="text-sm" style={{ color: dark ? "rgb(255 255 255 / 75%)" : token.colorTextSecondary }}>
                    {definition.label}
                </span>
                <span
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full"
                    style={{
                        backgroundColor: dark ? "rgb(255 255 255 / 16%)" : definition.iconBackground,
                        color: dark ? "var(--td-text-color-anti)" : definition.iconColor,
                    }}
                >
                    <Icon size={22} />
                </span>
            </div>
            <div
                className="py-3 text-[28px] leading-none font-semibold tabular-nums"
                style={{ color: dark ? "var(--td-text-color-anti)" : token.colorText }}
            >
                {value}
            </div>
            <div className="flex items-center justify-between gap-2 text-xs">
                <span
                    className="min-w-0 truncate"
                    style={{ color: dark ? "rgb(255 255 255 / 68%)" : token.colorTextTertiary }}
                >
                    {caption}
                </span>
                <span className="flex shrink-0 items-center gap-1" style={{ color: changeColor }}>
                    <span
                        className="flex h-4 w-4 items-center justify-center rounded-full"
                        style={{ backgroundColor: changeBackground }}
                    >
                        <ChangeIcon size={11} />
                    </span>
                    <span className="font-medium whitespace-nowrap tabular-nums">{change.text}</span>
                </span>
            </div>
        </Card>
    );
}
