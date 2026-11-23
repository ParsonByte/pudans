"use client";

import type { ReactNode } from "react";

import type { SecretCatalogSummary } from "@pudans-dev/api-client";
import {
    CalendarEventIcon,
    KeyIcon,
    LayersIcon,
    MinusCircleIcon,
    TreeListIcon,
    ViewModuleIcon,
} from "tdesign-icons-react";

import { MetricCard } from "@/components/ui";

import { formatCount, formatCountOrDash, formatPercent } from "../../../../composables/_format";
import { type DashboardTone, useDashboardTheme } from "../../../overview/dashboard/composables/_theme";

type SecretMetricGridProps = {
    loading: boolean;
    summary?: SecretCatalogSummary;
};

type SecretMetric = {
    footer?: string;
    icon: ReactNode;
    key: string;
    label: string;
    tone: DashboardTone;
    value: string;
};

export function SecretMetricGrid({ loading, summary }: SecretMetricGridProps) {
    const { token, tone } = useDashboardTheme();
    const total = summary?.total ?? 0;
    const entries = summary?.entries ?? 0;
    const empty = summary?.empty ?? 0;
    const types = summary?.types?.length ?? 0;
    const metrics: SecretMetric[] = [
        {
            footer: summary ? `覆盖 ${formatCount(types)} 种密钥类型` : undefined,
            icon: <KeyIcon size={20} />,
            key: "total",
            label: "密钥总数",
            tone: "blue",
            value: formatCountOrDash(summary?.total),
        },
        {
            footer: summary ? `文本与二进制键名去重统计` : undefined,
            icon: <TreeListIcon size={20} />,
            key: "entries",
            label: "键值条目",
            tone: "cyan",
            value: formatCountOrDash(summary?.entries),
        },
        {
            footer: summary ? `所有密钥的平均规模` : undefined,
            icon: <LayersIcon size={20} />,
            key: "average",
            label: "平均密钥规模",
            tone: "purple",
            value: summary ? (total > 0 ? (entries / total).toFixed(1) : "0.0") : "—",
        },
        {
            footer: summary ? `占全部密钥 ${formatPercent(empty, total)}` : undefined,
            icon: <MinusCircleIcon size={20} />,
            key: "empty",
            label: "空密钥",
            tone: "volcano",
            value: formatCountOrDash(summary?.empty),
        },
        {
            footer: summary ? "按创建时间滚动统计" : undefined,
            icon: <CalendarEventIcon size={20} />,
            key: "recent",
            label: "近 7 天创建",
            tone: "gold",
            value: formatCountOrDash(summary?.createdLastSevenDays),
        },
        {
            footer: summary ? `类型分布基于全部密钥` : undefined,
            icon: <ViewModuleIcon size={20} />,
            key: "types",
            label: "密钥类型",
            tone: "green",
            value: formatCountOrDash(types),
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
