import type { RoleUserCount } from "@pudans-dev/api-client";
import { Empty } from "antd";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";

import { formatCount, roleSlices } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type RoleDistributionChartProps = {
    roles?: RoleUserCount[];
};

type DonutTooltipProps = {
    active?: boolean;
    payload?: {
        payload?: {
            color?: string;
            displayName?: string;
            users?: number;
        };
    }[];
};

function DonutTooltip({ active, payload }: DonutTooltipProps) {
    const { token } = useDashboardTheme();
    const item = payload?.[0]?.payload;

    if (!active || !item) {
        return null;
    }

    return (
        <div
            className="rounded-xl border px-3 py-2 text-xs"
            style={{
                background: token.colorBgElevated,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowSecondary,
            }}
        >
            <div className="flex items-center gap-2 font-medium" style={{ color: token.colorText }}>
                <span className="h-2 w-2 rounded-full" style={{ backgroundColor: item.color }} />
                {item.displayName}
            </div>
            <div className="mt-1" style={{ color: token.colorTextSecondary }}>
                {formatCount(item.users)} 名成员
            </div>
        </div>
    );
}

export function RoleDistributionChart({ roles }: RoleDistributionChartProps) {
    const { chartColors, token } = useDashboardTheme();
    const slices = roleSlices(roles, chartColors);
    const total = slices.reduce((sum, item) => sum + item.users, 0);

    if (slices.length === 0) {
        return <Empty description="暂无角色数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    }

    return (
        <div className="flex flex-col items-center gap-5">
            <div className="relative h-48 w-48 shrink-0">
                <ResponsiveContainer height="100%" width="100%">
                    <PieChart>
                        <Pie
                            cornerRadius={8}
                            data={slices}
                            dataKey="users"
                            innerRadius={62}
                            nameKey="displayName"
                            outerRadius={88}
                            paddingAngle={3}
                            stroke="none"
                        >
                            {slices.map((item) => (
                                <Cell fill={item.color} key={item.role ?? item.displayName} />
                            ))}
                        </Pie>
                        <Tooltip content={<DonutTooltip />} />
                    </PieChart>
                </ResponsiveContainer>
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <span className="text-2xl font-semibold tracking-tight" style={{ color: token.colorText }}>
                        {formatCount(total)}
                    </span>
                    <span className="mt-1 text-xs" style={{ color: token.colorTextSecondary }}>
                        角色成员
                    </span>
                </div>
            </div>
            <div className="w-full min-w-0 space-y-3">
                {slices.map((item) => {
                    const percent = total === 0 ? 0 : (item.users / total) * 100;

                    return (
                        <div className="flex items-center gap-3" key={item.role ?? item.displayName}>
                            <span
                                className="h-2.5 w-2.5 shrink-0 rounded-full"
                                style={{ backgroundColor: item.color }}
                            />
                            <span
                                className="min-w-0 flex-1 truncate text-sm"
                                style={{ color: token.colorTextSecondary }}
                            >
                                {item.displayName}
                            </span>
                            <span className="text-xs tabular-nums" style={{ color: token.colorTextTertiary }}>
                                {formatCount(item.users)}
                            </span>
                            <span
                                className="w-12 text-right text-xs font-medium tabular-nums"
                                style={{ color: token.colorTextSecondary }}
                            >
                                {percent.toFixed(1)}%
                            </span>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
