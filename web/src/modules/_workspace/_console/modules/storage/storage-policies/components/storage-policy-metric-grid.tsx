"use client";

import type { ReactNode } from "react";

import { CheckCircleIcon, CloudIcon, DataBaseIcon, StarIcon } from "tdesign-icons-react";

import { MetricCard } from "@/components/ui";

import { formatCount, formatPercent } from "../../../../composables/_format";
import type { StoragePolicy } from "../../../../composables/_storage";
import { type DashboardTone, useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { storagePolicyTypeMeta } from "../composables/_storage-policies";

type StoragePolicyMetricGridProps = {
    loading: boolean;
    policies?: StoragePolicy[];
    total?: number;
};

type StoragePolicyMetric = {
    footer?: string;
    icon: ReactNode;
    key: string;
    label: string;
    tone: DashboardTone;
    value: string;
};

export function StoragePolicyMetricGrid({ loading, policies, total }: StoragePolicyMetricGridProps) {
    const { token, tone } = useDashboardTheme();
    const all = policies ?? [];
    const totalCount = total ?? all.length;
    const local = all.filter((policy) => storagePolicyTypeMeta(policy.spec.type).group === "local").length;
    const object = all.length - local;
    const ready = all.filter((policy) => policy.status?.ready).length;
    const defaults = all.filter((policy) => policy.spec.defaultPolicy).length;
    const types = new Set(all.map((policy) => policy.spec.type).filter(Boolean)).size;
    const metrics: StoragePolicyMetric[] = [
        {
            footer: policies ? `本地 ${formatCount(local)} · 对象存储 ${formatCount(object)}` : undefined,
            icon: <DataBaseIcon size={20} />,
            key: "total",
            label: "策略总数",
            tone: "blue",
            value: policies ? formatCount(totalCount) : "—",
        },
        {
            footer: policies ? `未就绪 ${formatCount(all.length - ready)}` : undefined,
            icon: <CheckCircleIcon size={20} />,
            key: "ready",
            label: "就绪策略",
            tone: "green",
            value: policies ? formatCount(ready) : "—",
        },
        {
            footer: policies
                ? totalCount > 0
                    ? `占全部策略 ${formatPercent(defaults, totalCount)}`
                    : "暂无策略"
                : undefined,
            icon: <StarIcon size={20} />,
            key: "defaults",
            label: "默认策略",
            tone: "gold",
            value: policies ? formatCount(defaults) : "—",
        },
        {
            footer: policies ? "按策略类型去重统计" : undefined,
            icon: <CloudIcon size={20} />,
            key: "types",
            label: "存储类型",
            tone: "purple",
            value: policies ? formatCount(types) : "—",
        },
    ];

    return (
        <>
            {metrics.map((metric) => {
                const colors = tone(metric.tone);

                return (
                    <div className="md:col-span-1 xl:col-span-3" key={metric.key}>
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
