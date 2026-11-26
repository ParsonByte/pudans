"use client";

import type { ReactNode } from "react";

import type { PersonalAccessToken } from "@pudans-dev/api-client";
import { CheckCircleIcon, CloseCircleIcon, HistoryIcon, KeyIcon, MinusCircleIcon, TimeIcon } from "tdesign-icons-react";

import { MetricCard } from "@/components/ui";

import { formatCountOrDash, formatPercent } from "../../../../composables/_format";
import { type DashboardTone, useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { PAT_EXPIRING_DAYS, patStatus, patUsedRecently } from "../composables/_pat";

type PatMetricGridProps = {
    items: PersonalAccessToken[];
    loading: boolean;
};

type PatMetric = {
    footer?: string;
    icon: ReactNode;
    key: string;
    label: string;
    tone: DashboardTone;
    value: string;
};

export function PatMetricGrid({ items, loading }: PatMetricGridProps) {
    const { token, tone } = useDashboardTheme();
    const total = items.length;
    const statuses = items.map((pat) => patStatus(pat));
    const active = statuses.filter((status) => status === "active").length;
    const expiring = statuses.filter((status) => status === "expiring").length;
    const expired = statuses.filter((status) => status === "expired").length;
    const revoked = statuses.filter((status) => status === "revoked").length;
    const recentlyUsed = items.filter((pat) => patUsedRecently(pat)).length;
    const metrics: PatMetric[] = [
        {
            footer: "当前用户的全部令牌",
            icon: <KeyIcon size={20} />,
            key: "total",
            label: "令牌总数",
            tone: "blue",
            value: formatCountOrDash(loading ? undefined : total),
        },
        {
            footer: `占全部令牌 ${formatPercent(active, total)}`,
            icon: <CheckCircleIcon size={20} />,
            key: "active",
            label: "有效令牌",
            tone: "green",
            value: formatCountOrDash(loading ? undefined : active),
        },
        {
            footer: `未来 ${PAT_EXPIRING_DAYS} 天内到期`,
            icon: <TimeIcon size={20} />,
            key: "expiring",
            label: "即将过期",
            tone: "gold",
            value: formatCountOrDash(loading ? undefined : expiring),
        },
        {
            footer: "过期后自动失效",
            icon: <MinusCircleIcon size={20} />,
            key: "expired",
            label: "已过期",
            tone: "warning",
            value: formatCountOrDash(loading ? undefined : expired),
        },
        {
            footer: "撤销后可恢复",
            icon: <CloseCircleIcon size={20} />,
            key: "revoked",
            label: "已撤销",
            tone: "red",
            value: formatCountOrDash(loading ? undefined : revoked),
        },
        {
            footer: `最近 ${PAT_EXPIRING_DAYS} 天有调用`,
            icon: <HistoryIcon size={20} />,
            key: "recent",
            label: "近 7 天使用",
            tone: "cyan",
            value: formatCountOrDash(loading ? undefined : recentlyUsed),
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
