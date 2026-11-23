export type DashboardRange = 7 | 30 | 90;

export const DASHBOARD_RANGES: { label: string; value: DashboardRange }[] = [
    { label: "近 7 天", value: 7 },
    { label: "近 30 天", value: 30 },
    { label: "近 90 天", value: 90 },
];
