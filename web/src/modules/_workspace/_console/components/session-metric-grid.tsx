"use client";

import type { ReactNode } from "react";

import type { AuditSessionPage } from "@pudans-dev/api-client";
import { DataBaseIcon, ErrorTriangleIcon, FolderZipIcon, GitBranchIcon, LinkIcon, TimeIcon } from "tdesign-icons-react";

import { MetricCard } from "@/components/ui";

import {
    formatAverageSteps,
    formatCount,
    formatCountOrDash,
    formatDuration,
    formatPercent,
} from "../composables/_format";
import { type DashboardTone, useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";

type SessionMetricGridProps = {
    loading: boolean;
    page?: AuditSessionPage;
};

type SessionMetric = {
    footer?: string;
    icon: ReactNode;
    key: string;
    label: string;
    tone: DashboardTone;
    value: string;
};

export function SessionMetricGrid({ loading, page }: SessionMetricGridProps) {
    const { token, tone } = useDashboardTheme();
    const total = page?.total ?? 0;
    const pageViews = page?.pageViews ?? 0;
    const failedSessions = page?.failedSessions ?? 0;
    const averagePages = total > 0 ? (pageViews / total).toFixed(1) : "—";
    const metrics: SessionMetric[] = [
        {
            footer: page ? `审计记录 ${formatCount(page.records)} 条` : undefined,
            icon: <LinkIcon size={20} />,
            key: "sessions",
            label: "用户会话",
            tone: "blue",
            value: formatCountOrDash(page?.total),
        },
        {
            footer: page ? `平均 ${averagePages} 页/会话` : undefined,
            icon: <DataBaseIcon size={20} />,
            key: "pages",
            label: "页面访问",
            tone: "cyan",
            value: formatCountOrDash(page?.pageViews),
        },
        {
            footer: page ? `平均 ${formatAverageSteps(page.averageSteps)} 步/会话` : undefined,
            icon: <GitBranchIcon size={20} />,
            key: "actions",
            label: "业务操作",
            tone: "purple",
            value: formatCountOrDash(page?.actionCount),
        },
        {
            footer: page ? `占 ${formatPercent(failedSessions, total)}` : undefined,
            icon: <ErrorTriangleIcon size={20} />,
            key: "failed",
            label: "异常会话",
            tone: "red",
            value: formatCountOrDash(page ? failedSessions : undefined),
        },
        {
            footer: page ? `平均会话 ${formatDuration(page.averageDurationMs)}` : undefined,
            icon: <TimeIcon size={20} />,
            key: "p95",
            label: "P95 会话耗时",
            tone: "orange",
            value: page ? formatDuration(page.p95DurationMs) : "—",
        },
        {
            footer: page?.truncated ? "已达到 20,000 条扫描上限" : page ? "完整扫描当前范围" : undefined,
            icon: <FolderZipIcon size={20} />,
            key: "records",
            label: "审计记录",
            tone: "gold",
            value: formatCountOrDash(page?.records),
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
