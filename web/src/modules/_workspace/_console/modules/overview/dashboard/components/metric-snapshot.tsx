"use client";

import { StatTile } from "@/components/ui";

import { formatCount } from "../../../../composables/_format";
import {
    DASHBOARD_METRICS,
    type DashboardMetric,
    type MetricDefinition,
    SNAPSHOT_METRIC_KEYS,
    metricChange,
} from "../composables/_dashboard";

type MetricSnapshotProps = {
    metrics: Map<string, DashboardMetric>;
};

function metricValue(metric: DashboardMetric, definition: MetricDefinition): string {
    return definition.format === "percent" ? `${metric.value.toFixed(1)}%` : formatCount(metric.value);
}

export function MetricSnapshot({ metrics }: MetricSnapshotProps) {
    return (
        <div className="grid grid-cols-2 gap-3">
            {SNAPSHOT_METRIC_KEYS.map((key) => {
                const definition = DASHBOARD_METRICS[key];
                const metric = metrics.get(key);

                if (!definition || !metric) {
                    return null;
                }

                const Icon = definition.icon;
                const change = metricChange(metric.value, metric.previousValue, definition);
                const changeColor =
                    change.direction === "flat"
                        ? "var(--td-text-color-placeholder)"
                        : change.positive
                          ? "var(--td-success-color)"
                          : "var(--td-error-color)";

                return (
                    <StatTile
                        key={key}
                        label={
                            <span className="flex items-center gap-2">
                                <span
                                    className="flex h-7 w-7 items-center justify-center rounded-full"
                                    style={{
                                        background: definition.iconBackground,
                                        color: definition.iconColor,
                                    }}
                                >
                                    <Icon size={15} />
                                </span>
                                {definition.label}
                            </span>
                        }
                        value={
                            <div>
                                <div
                                    className="text-xl leading-tight font-semibold tabular-nums"
                                    style={{ color: "var(--td-text-color-primary)" }}
                                >
                                    {metricValue(metric, definition)}
                                </div>
                                <div className="mt-1 text-[11px] font-normal" style={{ color: changeColor }}>
                                    {change.text}
                                </div>
                            </div>
                        }
                    />
                );
            })}
        </div>
    );
}
