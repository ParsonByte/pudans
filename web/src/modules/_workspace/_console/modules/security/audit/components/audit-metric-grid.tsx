"use client";

import type { ReactNode } from "react";

import type { AuditOverviewSummary } from "@pudans-dev/api-client";
import {
    CheckCircleIcon,
    ErrorTriangleIcon,
    FileTxtIcon,
    FileZipIcon,
    TimeIcon,
    UsergroupIcon,
} from "tdesign-icons-react";

import { MetricCard } from "@/components/ui";

import { formatCount, formatCountOrDash, formatDuration, formatPercent } from "../../../../composables/_format";
import { type DashboardTone, useDashboardTheme } from "../../../overview/dashboard/composables/_theme";

type AuditMetricGridProps = {
    loading: boolean;
    summary?: AuditOverviewSummary;
};

type AuditMetric = {
    footer?: string;
    icon: ReactNode;
    key: string;
    label: string;
    tone: DashboardTone;
    value: string;
};

export function AuditMetricGrid({ loading, summary }: AuditMetricGridProps) {
    const { token, tone } = useDashboardTheme();
    const records = summary?.records ?? 0;
    const success = summary?.success ?? 0;
    const failure = summary?.failure ?? 0;
    const denied = summary?.denied ?? 0;
    const anomalies = failure + denied;
    const metrics: AuditMetric[] = [
        {
            footer: summary ? `成功 ${formatCount(success)} · 异常 ${formatCount(anomalies)}` : undefined,
            icon: <FileTxtIcon size={20} />,
            key: "records",
            label: "审计记录",
            tone: "blue",
            value: formatCountOrDash(summary?.records),
        },
        {
            footer: summary ? `成功 ${formatCount(success)} 条` : undefined,
            icon: <CheckCircleIcon size={20} />,
            key: "successRate",
            label: "成功比例",
            tone: "green",
            value: summary ? formatPercent(success, records) : "—",
        },
        {
            footer: summary ? `失败 ${formatCount(failure)} · 拒绝 ${formatCount(denied)}` : undefined,
            icon: <ErrorTriangleIcon size={20} />,
            key: "anomalies",
            label: "异常请求",
            tone: "red",
            value: formatCountOrDash(summary ? anomalies : undefined),
        },
        {
            footer: summary ? "按 UID 去重统计" : undefined,
            icon: <UsergroupIcon size={20} />,
            key: "activeUsers",
            label: "活跃用户",
            tone: "purple",
            value: formatCountOrDash(summary?.activeUsers),
        },
        {
            footer: summary ? `P95 ${formatDuration(summary.p95DurationMs)}` : undefined,
            icon: <TimeIcon size={20} />,
            key: "latency",
            label: "平均耗时",
            tone: "cyan",
            value: summary ? formatDuration(summary.averageDurationMs) : "—",
        },
        {
            footer: summary ? `归档 ${formatCount(summary.archivedRecords)} 条` : undefined,
            icon: <FileZipIcon size={20} />,
            key: "storage",
            label: "活跃记录",
            tone: "orange",
            value: formatCountOrDash(summary?.activeRecords),
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
