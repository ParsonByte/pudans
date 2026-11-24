"use client";

import { Link } from "tdesign-react";

import { StatTile } from "@/components/ui";

import { formatCount, formatDateTime } from "../../../../composables/_format";
import { type DashboardData, formatUptime } from "../composables/_dashboard";

type SiteInfoGridProps = {
    overview: DashboardData;
};

export function SiteInfoGrid({ overview }: SiteInfoGridProps) {
    const globalInfo = overview.globalInfo;
    const items = [
        {
            label: "站点名称",
            value: (
                <div>
                    <div>{globalInfo?.siteTitle ?? "Pudans"}</div>
                    <div
                        className="mt-0.5 text-[11px] font-normal"
                        style={{ color: "var(--td-text-color-placeholder)" }}
                    >
                        {globalInfo?.siteSubtitle || "未填写"}
                    </div>
                </div>
            ),
        },
        { label: "版本", value: globalInfo?.version ?? "—" },
        {
            label: "站点地址",
            value: globalInfo?.externalUrl ? (
                <Link className="break-all" href={globalInfo.externalUrl} target="_blank">
                    {globalInfo.externalUrl}
                </Link>
            ) : (
                "—"
            ),
        },
        { label: "时区", value: globalInfo?.timezone ?? "—" },
        { label: "运行时长", value: formatUptime(globalInfo?.uptimeSeconds) },
        { label: "用户组总数", value: formatCount(overview.totalUserGroups) },
        { label: "Webhook 总数", value: formatCount(overview.totalWebhooks) },
        { label: "数据时间", value: formatDateTime(overview.generatedAt) },
    ];

    return (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {items.map((item) => (
                <StatTile compact key={item.label} label={item.label} value={item.value} />
            ))}
        </div>
    );
}
