import { TrendChart } from "../../../../components/trend-chart";
import type { ActivityTrendPoint } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type ActivityTrendChartProps = {
    data: ActivityTrendPoint[];
};

export function ActivityTrendChart({ data }: ActivityTrendChartProps) {
    const { token } = useDashboardTheme();

    return (
        <TrendChart
            data={data}
            emptyText="暂无趋势数据"
            series={[
                { color: token.colorPrimary, key: "operations", label: "操作总量", type: "area" },
                { color: token.colorSuccess, key: "logins", label: "登录成功", type: "line" },
                { color: token.colorError, key: "failures", label: "失败 / 拒绝", type: "line" },
            ]}
        />
    );
}
