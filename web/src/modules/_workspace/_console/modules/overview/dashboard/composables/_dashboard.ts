import type { ComponentType } from "react";

import type {
    ActiveUser,
    GlobalInfoSummary,
    LoginTrendPoint,
    RecentOperation,
    RoleUserCount,
} from "@pudans-dev/api-client";
import {
    ActivityIcon,
    CursorIcon,
    ErrorTriangleIcon,
    LoginIcon,
    SecuredIcon,
    TrendingUpIcon,
    UserAddIcon,
    UsergroupIcon,
} from "tdesign-icons-react";
import type { IconProps } from "tdesign-icons-react";

import { formatCount } from "../../../../composables/_format";
import type { DashboardTone } from "./_theme";

export type DashboardDateRange = {
    days: number;
    end: string;
    start: string;
};

export type DashboardMetric = {
    key: string;
    previousValue: number;
    value: number;
};

type OperationCount = {
    count: number;
    operation: string;
};

export type DashboardInsight = {
    description?: string;
    severity?: string;
    title?: string;
};

export type DashboardResultCount = {
    count?: number;
    result?: string;
};

export type DashboardData = {
    activeUsers?: ActiveUser[];
    failureTrend?: LoginTrendPoint[];
    generatedAt?: string;
    globalInfo?: GlobalInfoSummary;
    insights?: DashboardInsight[];
    loginTrend?: LoginTrendPoint[];
    metrics?: DashboardMetric[];
    operationTrend?: LoginTrendPoint[];
    rangeDays?: number;
    recentAuditLogs?: RecentOperation[];
    resultDistribution?: DashboardResultCount[];
    roleDistribution?: RoleUserCount[];
    riskRuleTrend?: LoginTrendPoint[];
    todayOperations?: number;
    topOperations?: OperationCount[];
    totalRiskRules?: number;
    totalRoles?: number;
    totalUserGroups?: number;
    totalUsers?: number;
    totalWebhooks?: number;
    webhookTrend?: LoginTrendPoint[];
};

type MetricFormat = "number" | "percent";

export type MetricDefinition = {
    format: MetricFormat;
    icon: ComponentType<IconProps>;
    iconBackground: string;
    iconColor: string;
    label: string;
    lowerIsBetter?: boolean;
};

export type MetricChange = {
    direction: "down" | "flat" | "up";
    positive: boolean;
    text: string;
};

export type ActivityTrendPoint = {
    date: string;
    failures: number;
    label: string;
    logins: number;
    operations: number;
};

export type CreationTrendPoint = {
    date: string;
    label: string;
    riskRules: number;
    webhooks: number;
};

export const PRIMARY_METRIC_KEYS = ["users", "activeUsers", "operations", "loginSuccessRate"] as const;

export const SNAPSHOT_METRIC_KEYS = ["newUsers", "abnormalOperations", "riskRules", "loginSuccess"] as const;

export const DASHBOARD_METRICS: Record<string, MetricDefinition> = {
    activeUsers: {
        format: "number",
        icon: ActivityIcon,
        iconBackground: "var(--td-brand-color-light)",
        iconColor: "var(--td-brand-color)",
        label: "活跃用户",
    },
    abnormalOperations: {
        format: "number",
        icon: ErrorTriangleIcon,
        iconBackground: "var(--td-error-color-light)",
        iconColor: "var(--td-error-color)",
        label: "异常操作",
        lowerIsBetter: true,
    },
    loginSuccessRate: {
        format: "percent",
        icon: TrendingUpIcon,
        iconBackground: "var(--td-success-color-light)",
        iconColor: "var(--td-success-color)",
        label: "登录成功率",
    },
    loginSuccess: {
        format: "number",
        icon: LoginIcon,
        iconBackground: "var(--td-success-color-light)",
        iconColor: "var(--td-success-color)",
        label: "登录成功",
    },
    newUsers: {
        format: "number",
        icon: UserAddIcon,
        iconBackground: "var(--td-brand-color-light)",
        iconColor: "var(--td-brand-color)",
        label: "新增用户",
    },
    operations: {
        format: "number",
        icon: CursorIcon,
        iconBackground: "var(--td-warning-color-light)",
        iconColor: "var(--td-warning-color)",
        label: "操作总数",
    },
    riskRules: {
        format: "number",
        icon: SecuredIcon,
        iconBackground: "var(--td-brand-color-light)",
        iconColor: "var(--td-brand-color)",
        label: "风险规则",
    },
    users: {
        format: "number",
        icon: UsergroupIcon,
        iconBackground: "var(--td-brand-color-light)",
        iconColor: "var(--td-brand-color)",
        label: "用户总数",
    },
};

export function metricChange(value: number, previousValue: number, definition: MetricDefinition): MetricChange {
    const difference = value - previousValue;

    if (Math.abs(difference) < 0.05) {
        return { direction: "flat", positive: true, text: "与上期持平" };
    }

    const direction = difference > 0 ? "up" : "down";
    const positive = definition.lowerIsBetter ? difference < 0 : difference > 0;
    let text: string;

    if (definition.format === "percent") {
        text = `${difference > 0 ? "+" : ""}${difference.toFixed(1)}pp 较上期`;
    } else if (previousValue === 0) {
        text = `${difference > 0 ? "+" : ""}${formatCount(difference)} 较上期`;
    } else {
        text = `${difference > 0 ? "+" : ""}${((difference / previousValue) * 100).toFixed(1)}% 较上期`;
    }

    return { direction, positive, text };
}

function countByDate(points?: LoginTrendPoint[]): Map<string, number> {
    const counts = new Map<string, number>();

    for (const point of points ?? []) {
        if (point.date) {
            counts.set(point.date, point.count ?? 0);
        }
    }

    return counts;
}

export function mergeActivityTrend(overview: DashboardData): ActivityTrendPoint[] {
    const operations = countByDate(overview.operationTrend);
    const logins = countByDate(overview.loginTrend);
    const failures = countByDate(overview.failureTrend);
    const dates = Array.from(new Set([...operations.keys(), ...logins.keys(), ...failures.keys()])).sort();

    return dates.map((date) => ({
        date,
        failures: failures.get(date) ?? 0,
        label: date.slice(5),
        logins: logins.get(date) ?? 0,
        operations: operations.get(date) ?? 0,
    }));
}

export function mergeCreationTrend(overview: DashboardData): CreationTrendPoint[] {
    const riskRules = countByDate(overview.riskRuleTrend);
    const webhooks = countByDate(overview.webhookTrend);
    const dates = Array.from(new Set([...riskRules.keys(), ...webhooks.keys()])).sort();

    return dates.map((date) => ({
        date,
        label: date.slice(5),
        riskRules: riskRules.get(date) ?? 0,
        webhooks: webhooks.get(date) ?? 0,
    }));
}

export function metricCaption(key: string, overview: DashboardData, metrics: Map<string, DashboardMetric>): string {
    const metricValue = (metricKey: string) => formatCount(metrics.get(metricKey)?.value ?? 0);

    if (key === "users") {
        return `风险规则 ${formatCount(overview.totalRiskRules)} 条 · 新增 ${metricValue("newUsers")} 人`;
    }

    if (key === "activeUsers") {
        return `用户总数 ${formatCount(overview.totalUsers)} 人`;
    }

    if (key === "operations") {
        return `今日 ${formatCount(overview.todayOperations)} 次 · 异常 ${metricValue("abnormalOperations")} 次`;
    }

    if (key === "loginSuccessRate") {
        return `登录成功 ${metricValue("loginSuccess")} 次`;
    }

    return "";
}

export function userLabel(name?: string | null): string {
    if (!name || name === "anonymous") {
        return "匿名用户";
    }

    return name === "unknown" ? "未知用户" : name;
}

function dateValue(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

export function dashboardDateValue(value: Date | string | number): string {
    if (value instanceof Date) {
        return dateValue(value);
    }

    if (typeof value === "number") {
        return dateValue(new Date(value));
    }

    return value.slice(0, 10);
}

export function dashboardRangeForDays(days: number, end = new Date()): DashboardDateRange {
    const start = new Date(end);
    start.setDate(start.getDate() - days + 1);

    return {
        days,
        end: dateValue(end),
        start: dateValue(start),
    };
}

export function dashboardRangeDays(start: string, end: string): number {
    const startTime = Date.parse(`${start}T00:00:00Z`);
    const endTime = Date.parse(`${end}T00:00:00Z`);

    return Math.floor((endTime - startTime) / 86_400_000) + 1;
}

export function dashboardRangeBounds(range: DashboardDateRange): { from: string; to: string } {
    return {
        from: new Date(`${range.start}T00:00:00.000`).toISOString(),
        to: new Date(`${range.end}T23:59:59.999`).toISOString(),
    };
}

export function resultTone(result: string): { label: string; tone: DashboardTone } {
    if (result === "SUCCESS") {
        return { label: "成功", tone: "green" };
    }

    if (result === "DENIED") {
        return { label: "拒绝", tone: "warning" };
    }

    return { label: "失败", tone: "red" };
}

export function formatJourneyDuration(durationMs?: number | null): string {
    if (!durationMs || durationMs <= 0) {
        return "—";
    }

    const seconds = Math.ceil(durationMs / 1000);

    return seconds < 60 ? "不足 1 分钟" : formatUptime(seconds);
}

export function formatUptime(seconds?: number | null): string {
    if (!seconds || seconds <= 0) {
        return "刚刚启动";
    }

    const days = Math.floor(seconds / 86_400);
    const hours = Math.floor((seconds % 86_400) / 3_600);
    const minutes = Math.floor((seconds % 3_600) / 60);

    if (days > 0) {
        return `${days} 天 ${hours} 小时`;
    }

    return hours > 0 ? `${hours} 小时 ${minutes} 分` : `${minutes} 分钟`;
}
