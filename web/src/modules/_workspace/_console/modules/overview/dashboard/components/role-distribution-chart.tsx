import type { RoleUserCount } from "@pudans-dev/api-client";

import { DonutBreakdown } from "../../../../components/donut-breakdown";
import { roleSlices } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type RoleDistributionChartProps = {
    roles?: RoleUserCount[];
};

export function RoleDistributionChart({ roles }: RoleDistributionChartProps) {
    const { chartColors } = useDashboardTheme();
    const slices = roleSlices(roles, chartColors);

    return (
        <DonutBreakdown
            centerLabel="角色成员"
            emptyText="暂无角色数据"
            slices={slices.map((item) => ({
                color: item.color,
                key: item.role ?? item.displayName,
                label: item.displayName,
                value: item.users ?? 0,
            }))}
            valueUnit="名成员"
        />
    );
}
