"use client";

import type { ReactNode } from "react";

import type { RiskOverviewSummary } from "@pudans-dev/api-client";
import {
    ActivityIcon,
    NotificationIcon,
    ShieldErrorIcon,
    TaskCheckedIcon,
    UserBlockedIcon,
    UsergroupIcon,
} from "tdesign-icons-react";

import { MetricCard } from "@/components/ui";

import { formatCount, formatCountOrDash, formatPercent } from "../../../../composables/_format";
import { type DashboardTone, useDashboardTheme } from "../../../overview/dashboard/composables/_theme";

type RiskMetricGridProps = {
    loading: boolean;
    summary?: RiskOverviewSummary;
};

type RiskMetric = {
    footer?: string;
    icon: ReactNode;
    key: string;
    label: string;
    tone: DashboardTone;
    value: string;
};

export function RiskMetricGrid({ loading, summary }: RiskMetricGridProps) {
    const { token, tone } = useDashboardTheme();
    const events = summary?.events ?? 0;
    const blocked = summary?.blocked ?? 0;
    const alerts = summary?.alerts ?? 0;
    const activeBans = summary?.activeBans ?? 0;
    const metrics: RiskMetric[] = [
        {
            footer: summary ? `阻断 ${formatCount(blocked)} · 告警 ${formatCount(alerts)}` : undefined,
            icon: <ActivityIcon size={20} />,
            key: "events",
            label: "风险事件",
            tone: "blue",
            value: formatCountOrDash(summary?.events),
        },
        {
            footer: summary ? `占全部事件 ${formatPercent(blocked, events)}` : undefined,
            icon: <ShieldErrorIcon size={20} />,
            key: "blocked",
            label: "阻断处置",
            tone: "red",
            value: formatCountOrDash(summary?.blocked),
        },
        {
            footer: summary ? `占全部事件 ${formatPercent(alerts, events)}` : undefined,
            icon: <NotificationIcon size={20} />,
            key: "alerts",
            label: "告警提示",
            tone: "warning",
            value: formatCountOrDash(summary?.alerts),
        },
        {
            footer: summary ? (activeBans > 0 ? "实时生效中" : "当前无生效封禁") : undefined,
            icon: <UserBlockedIcon size={20} />,
            key: "bans",
            label: "生效封禁",
            tone: "volcano",
            value: formatCountOrDash(summary?.activeBans),
        },
        {
            footer: summary ? "按主体去重统计" : undefined,
            icon: <UsergroupIcon size={20} />,
            key: "subjects",
            label: "受影响主体",
            tone: "purple",
            value: formatCountOrDash(summary?.affectedSubjects),
        },
        {
            footer: summary ? `规则总数 ${formatCount(summary.totalRules)}` : undefined,
            icon: <TaskCheckedIcon size={20} />,
            key: "rules",
            label: "启用规则",
            tone: "green",
            value: formatCountOrDash(summary?.enabledRules),
        },
    ];

    return (
        <>
            {metrics.map((metric) => {
                const colors = tone(metric.tone);

                return (
                    <div className="md:col-span-1 xl:col-span-4" key={metric.key}>
                        <MetricCard
                            footer={
                                metric.footer && !loading ? (
                                    <span style={{ color: token.colorTextSecondary }}>{metric.footer}</span>
                                ) : undefined
                            }
                            icon={metric.icon}
                            label={metric.label}
                            loading={loading}
                            tone={colors}
                            value={metric.value}
                        />
                    </div>
                );
            })}
        </>
    );
}
