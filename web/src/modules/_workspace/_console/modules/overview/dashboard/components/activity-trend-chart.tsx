import { TrendChart } from "../../../../components/trend-chart";
import type { ActivityTrendPoint } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type ActivityTrendChartProps = {
    data: ActivityTrendPoint[];
};

export function ActivityTrendChart({ data }: ActivityTrendChartProps) {
    const { tone } = useDashboardTheme();

    return (
        <TrendChart
            data={data}
            emptyText="暂无趋势数据"
            height={320}
            series={[
                { color: tone("blue").base, key: "operations", label: "操作总量", type: "area" },
                { color: tone("green").base, key: "logins", label: "登录成功", type: "line" },
                {
                    color: tone("red").base,
                    key: "failures",
                    label: "失败 / 拒绝",
                    lowerIsBetter: true,
                    type: "line",
                },
            ]}
        />
    );
}
