"use client";

import type { ComponentType } from "react";

import { CheckCircleIcon, ErrorTriangleIcon, InfoCircleIcon, ShieldErrorIcon } from "tdesign-icons-react";
import type { IconProps } from "tdesign-icons-react";
import { Empty } from "tdesign-react";

import type { DashboardInsight } from "../composables/_dashboard";

type InsightMeta = {
    background: string;
    color: string;
    icon: ComponentType<IconProps>;
    label: string;
};

const INSIGHT_META: Record<string, InsightMeta> = {
    CRITICAL: {
        background: "var(--td-error-color-light)",
        color: "var(--td-error-color)",
        icon: ShieldErrorIcon,
        label: "高危",
    },
    INFO: {
        background: "var(--td-brand-color-light)",
        color: "var(--td-brand-color)",
        icon: InfoCircleIcon,
        label: "提示",
    },
    SUCCESS: {
        background: "var(--td-success-color-light)",
        color: "var(--td-success-color)",
        icon: CheckCircleIcon,
        label: "正常",
    },
    WARNING: {
        background: "var(--td-warning-color-light)",
        color: "var(--td-warning-color)",
        icon: ErrorTriangleIcon,
        label: "预警",
    },
};

type SecurityInsightsProps = {
    insights?: DashboardInsight[];
};

export function SecurityInsights({ insights }: SecurityInsightsProps) {
    if (!insights || insights.length === 0) {
        return <Empty description="暂无诊断项" size="small" />;
    }

    return (
        <div className="space-y-3">
            {insights.map((insight, index) => {
                const meta = INSIGHT_META[insight.severity ?? "INFO"] ?? INSIGHT_META.INFO;
                const Icon = meta.icon;

                return (
                    <div
                        className="flex gap-3 rounded-xl p-3"
                        key={`${insight.title ?? "insight"}-${index}`}
                        style={{ background: "var(--td-bg-color-secondarycontainer)" }}
                    >
                        <span
                            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
                            style={{ background: meta.background, color: meta.color }}
                        >
                            <Icon size={18} />
                        </span>
                        <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                                <span
                                    className="text-sm font-semibold"
                                    style={{ color: "var(--td-text-color-primary)" }}
                                >
                                    {insight.title ?? "未命名诊断"}
                                </span>
                                <span className="text-[11px] font-medium" style={{ color: meta.color }}>
                                    {meta.label}
                                </span>
                            </div>
                            <p className="mt-1 text-xs leading-5" style={{ color: "var(--td-text-color-secondary)" }}>
                                {insight.description ?? "暂无诊断说明"}
                            </p>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
