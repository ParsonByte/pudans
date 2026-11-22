import { Empty } from "antd";
import { Area, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { useMobile } from "@/hooks/ui/use-mobile";

import { type ActivityTrendPoint, formatCount, formatTrendDate } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type ActivityTrendChartProps = {
    data: ActivityTrendPoint[];
};

type TrendTooltipProps = {
    active?: boolean;
    label?: string;
    payload?: {
        color?: string;
        dataKey?: string | number;
        name?: string;
        payload?: {
            date?: string;
        };
        value?: number;
    }[];
};

function TrendTooltip({ active, label, payload }: TrendTooltipProps) {
    const { token } = useDashboardTheme();

    if (!active || !payload?.length) {
        return null;
    }

    const date = payload[0]?.payload?.date;
    const title = date ? formatTrendDate(date) : label;

    return (
        <div
            className="rounded-2xl border px-4 py-3 backdrop-blur"
            style={{
                background: token.colorBgElevated,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowSecondary,
            }}
        >
            <div className="mb-2 text-xs font-medium" style={{ color: token.colorTextSecondary }}>
                {title}
            </div>
            <div className="space-y-1.5">
                {[...payload].reverse().map((item) => (
                    <div className="flex min-w-36 items-center justify-between gap-6" key={String(item.dataKey)}>
                        <span className="flex items-center gap-2 text-xs" style={{ color: token.colorTextSecondary }}>
                            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: item.color }} />
                            {item.name}
                        </span>
                        <span className="text-xs font-semibold" style={{ color: token.colorText }}>
                            {formatCount(item.value)}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}

export function ActivityTrendChart({ data }: ActivityTrendChartProps) {
    const isMobile = useMobile();
    const { token } = useDashboardTheme();

    if (data.length === 0) {
        return <Empty description="暂无趋势数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    }

    return (
        <div className="h-72 w-full sm:h-80">
            <ResponsiveContainer height="100%" width="100%">
                <ComposedChart data={data} margin={{ bottom: 0, left: -12, right: 8, top: 8 }}>
                    <defs>
                        <linearGradient id="dashboard-operations" x1="0" x2="0" y1="0" y2="1">
                            <stop offset="0%" stopColor={token.colorPrimary} stopOpacity={0.24} />
                            <stop offset="100%" stopColor={token.colorPrimary} stopOpacity={0.01} />
                        </linearGradient>
                    </defs>
                    <CartesianGrid stroke={token.colorBorderSecondary} strokeDasharray="4 6" vertical={false} />
                    <XAxis
                        axisLine={false}
                        dataKey="label"
                        interval="preserveStartEnd"
                        minTickGap={isMobile ? 26 : 10}
                        tick={{ fill: token.colorTextTertiary, fontSize: 11 }}
                        tickLine={false}
                    />
                    <YAxis
                        allowDecimals={false}
                        axisLine={false}
                        tick={{ fill: token.colorTextTertiary, fontSize: 11 }}
                        tickLine={false}
                        width={44}
                    />
                    <Tooltip
                        content={<TrendTooltip />}
                        cursor={{ stroke: token.colorBorder, strokeDasharray: "4 4" }}
                    />
                    <Legend
                        align={isMobile ? "center" : "right"}
                        iconSize={8}
                        iconType="circle"
                        verticalAlign="top"
                        wrapperStyle={{
                            color: token.colorTextSecondary,
                            fontSize: 12,
                            paddingBottom: isMobile ? 8 : 12,
                        }}
                    />
                    <Area
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        dataKey="operations"
                        dot={false}
                        fill="url(#dashboard-operations)"
                        name="操作总量"
                        stroke={token.colorPrimary}
                        strokeWidth={2.4}
                        type="monotone"
                    />
                    <Line
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        dataKey="logins"
                        dot={false}
                        name="登录成功"
                        stroke={token.colorSuccess}
                        strokeWidth={2.1}
                        type="monotone"
                    />
                    <Line
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        dataKey="failures"
                        dot={false}
                        name="失败 / 拒绝"
                        stroke={token.colorError}
                        strokeWidth={2.1}
                        type="monotone"
                    />
                </ComposedChart>
            </ResponsiveContainer>
        </div>
    );
}
