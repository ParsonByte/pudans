"use client";

import type { SecretTypeCount } from "@pudans-dev/api-client";

import { DonutBreakdown } from "../../../../components/donut-breakdown";
import { useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { secretTypeLabel } from "../composables/_secrets";

type SecretTypeChartProps = {
    types?: SecretTypeCount[];
};

export function SecretTypeChart({ types }: SecretTypeChartProps) {
    const { chartColors } = useDashboardTheme();
    const slices = (types ?? []).map((type, index) => ({
        color: chartColors[index % chartColors.length],
        key: type.type ?? "Opaque",
        label: secretTypeLabel(type.type),
        value: type.count ?? 0,
    }));

    return (
        <DonutBreakdown
            centerLabel="个密钥"
            emptyText="暂无密钥类型数据"
            layout="horizontal"
            size="compact"
            slices={slices}
            valueUnit="个密钥"
        />
    );
}
