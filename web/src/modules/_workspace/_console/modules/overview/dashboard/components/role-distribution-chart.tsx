"use client";

import { useMemo } from "react";

import type { RoleUserCount } from "@pudans-dev/api-client";
import { Empty } from "tdesign-react";

import { EChart, type EChartsCoreOption } from "@/components/ui";

import { formatCount } from "../../../../composables/_format";
import { escapeHtml } from "../../../../composables/_html";
import { useDashboardTheme } from "../composables/_theme";

type RoleDistributionChartProps = {
    roles?: RoleUserCount[];
};

type RoleShare = {
    color: string;
    key: string;
    label: string;
    value: number;
};

export function RoleDistributionChart({ roles }: RoleDistributionChartProps) {
    const { dark, tone } = useDashboardTheme();
    const shares = useMemo<RoleShare[]>(() => {
        const sorted = [...(roles ?? [])]
            .filter((role) => (role.users ?? 0) > 0)
            .sort((left, right) => (right.users ?? 0) - (left.users ?? 0));
        const primary = sorted[0];

        if (!primary) {
            return [];
        }

        const rest = sorted.slice(1).reduce((sum, role) => sum + (role.users ?? 0), 0);
        const primaryShare: RoleShare = {
            color: tone("blue").base,
            key: primary.role ?? primary.displayName ?? "primary",
            label: primary.displayName ?? primary.role ?? "未命名角色",
            value: primary.users ?? 0,
        };

        return rest > 0
            ? [
                  primaryShare,
                  {
                      color: tone("geekblue").base,
                      key: "other",
                      label: "其他角色",
                      value: rest,
                  },
              ]
            : [primaryShare];
    }, [roles, tone]);
    const total = shares.reduce((sum, share) => sum + share.value, 0);
    const primary = shares[0];
    const primaryPercent = total > 0 && primary ? (primary.value / total) * 100 : 0;
    const option = useMemo<EChartsCoreOption>(
        () => ({
            animationDuration: 480,
            series: [
                {
                    avoidLabelOverlap: true,
                    data: shares.map((share) => ({
                        itemStyle: { color: share.color },
                        name: share.label,
                        value: share.value,
                    })),
                    emphasis: { scale: true, scaleSize: 4 },
                    itemStyle: {
                        borderColor: dark ? "#242424" : "#fff",
                        borderWidth: 2,
                    },
                    label: { show: false },
                    labelLine: { show: false },
                    radius: ["70%", "92%"],
                    startAngle: 90,
                    type: "pie",
                },
            ],
            textStyle: { fontFamily: "Noto Sans Variable, sans-serif" },
            tooltip: {
                backgroundColor: "transparent",
                borderWidth: 0,
                confine: true,
                formatter: (parameters: { dataIndex?: number }[]) => {
                    const share = shares[parameters[0]?.dataIndex ?? 0];

                    if (!share) {
                        return "";
                    }

                    return `<div style="min-width:156px;padding:10px 12px;border:1px solid var(--td-component-stroke);border-radius:6px;background:var(--td-bg-color-container);box-shadow:var(--td-shadow-2);font-size:12px;color:var(--td-text-color-secondary)"><div style="display:flex;align-items:center;justify-content:space-between;gap:20px"><span style="display:flex;align-items:center;gap:8px"><span style="width:8px;height:8px;border-radius:50%;background:${share.color}"></span>${escapeHtml(share.label)}</span><strong style="color:var(--td-text-color-primary)">${formatCount(share.value)} 名成员</strong></div><div style="margin-top:6px;text-align:right">${((share.value / total) * 100).toFixed(2)}%</div></div>`;
                },
                padding: 0,
                trigger: "item",
            },
        }),
        [dark, shares, total],
    );

    if (!primary) {
        return <Empty description="暂无角色数据" size="small" />;
    }

    return (
        <div className="flex min-h-[340px] flex-col items-center justify-center gap-5">
            <div className="relative h-[250px] w-full">
                <EChart height={250} option={option} />
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <div
                        className="text-[30px] leading-none font-semibold tabular-nums"
                        style={{ color: "var(--td-text-color-primary)" }}
                    >
                        {primaryPercent.toFixed(2)}%
                    </div>
                    <div
                        className="mt-2 max-w-[148px] truncate text-xs"
                        style={{ color: "var(--td-text-color-secondary)" }}
                        title={`${primary.label}占比`}
                    >
                        {primary.label}占比
                    </div>
                </div>
            </div>
            <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs">
                {shares.map((share) => (
                    <span
                        className="flex items-center gap-2"
                        key={share.key}
                        style={{ color: "var(--td-text-color-secondary)" }}
                    >
                        <span className="h-0.5 w-3 rounded-full" style={{ background: share.color }} />
                        {share.label}
                    </span>
                ))}
            </div>
        </div>
    );
}
