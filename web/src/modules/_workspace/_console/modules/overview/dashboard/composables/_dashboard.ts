import type {
    DashboardOverview as GeneratedDashboardOverview,
    LoginTrendPoint,
    RoleUserCount,
} from "@pudans-dev/api-client";
import {
    Activity,
    LogIn,
    type LucideIcon,
    MousePointerClick,
    ShieldCheck,
    TrendingUp,
    TriangleAlert,
    UserPlus,
    Users,
} from "lucide-react";

import type { DashboardTone } from "./_theme";

export type DashboardRange = 7 | 30 | 90;

type ResultCount = {
    count: number;
    result: string;
};

type OperationCount = {
    count: number;
    operation: string;
};

export type DashboardMetric = {
    key: string;
    previousValue: number;
    value: number;
};

export type DashboardInsight = {
    description: string;
    severity: string;
    title: string;
};

export type DashboardData = GeneratedDashboardOverview & {
    failureTrend: LoginTrendPoint[];
    generatedAt: string;
    insights: DashboardInsight[];
    metrics: DashboardMetric[];
    rangeDays: number;
    resultDistribution: ResultCount[];
    topOperations: OperationCount[];
};

export type MetricFormat = "number" | "percent";

export type MetricDefinition = {
    format: MetricFormat;
    icon: LucideIcon;
    label: string;
    lowerIsBetter?: boolean;
    tone: DashboardTone;
};

export const DASHBOARD_RANGES: { label: string; value: DashboardRange }[] = [
    { label: "近7天", value: 7 },
    { label: "近30天", value: 30 },
    { label: "近90天", value: 90 },
];

export const DASHBOARD_METRIC_ORDER = [
    "users",
    "activeUsers",
    "operations",
    "loginSuccess",
    "loginSuccessRate",
    "newUsers",
    "abnormalOperations",
    "riskRules",
];

export const DASHBOARD_METRICS: Record<string, MetricDefinition> = {
    activeUsers: {
        format: "number",
        icon: Activity,
        label: "活跃用户",
        tone: "blue",
    },
    abnormalOperations: {
        format: "number",
        icon: TriangleAlert,
        label: "异常操作",
        lowerIsBetter: true,
        tone: "red",
    },
    loginSuccess: {
        format: "number",
        icon: LogIn,
        label: "登录成功",
        tone: "green",
    },
    loginSuccessRate: {
        format: "percent",
        icon: TrendingUp,
        label: "登录成功率",
        tone: "purple",
    },
    newUsers: {
        format: "number",
        icon: UserPlus,
        label: "新增用户",
        tone: "magenta",
    },
    operations: {
        format: "number",
        icon: MousePointerClick,
        label: "操作总数",
        tone: "orange",
    },
    riskRules: {
        format: "number",
        icon: ShieldCheck,
        label: "风险规则",
        tone: "geekblue",
    },
    users: {
        format: "number",
        icon: Users,
        label: "用户总数",
        tone: "gold",
    },
};

const COUNT_FORMATTER = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 0 });

export function formatCount(value?: number | null): string {
    return COUNT_FORMATTER.format(value ?? 0);
}

export function formatMetricValue(value: number, format: MetricFormat): string {
    return format === "percent" ? `${value.toFixed(1)}%` : formatCount(value);
}

type MetricChange = {
    direction: "down" | "flat" | "up";
    positive: boolean;
    text: string;
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

export type ActivityTrendPoint = {
    date: string;
    failures: number;
    label: string;
    logins: number;
    operations: number;
};

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

export function formatTrendDate(date?: string): string {
    if (!date) {
        return "—";
    }

    const [, month, day] = date.split("-");

    return month && day ? `${Number(month)}月${Number(day)}日` : date;
}

export type RoleSlice = Omit<RoleUserCount, "displayName" | "role" | "users"> & {
    color: string;
    displayName: string;
    role: string;
    users: number;
};

export function roleSlices(roles: RoleUserCount[] | undefined, colors: string[]): RoleSlice[] {
    const sorted = [...(roles ?? [])].sort((left, right) => (right.users ?? 0) - (left.users ?? 0));
    const visible = sorted.slice(0, 5);
    const rest = sorted.slice(5);

    if (rest.length > 0) {
        visible.push({
            displayName: "其他角色",
            role: "other",
            users: rest.reduce((sum, item) => sum + (item.users ?? 0), 0),
        } as RoleUserCount);
    }

    return visible.map((item, index) => ({
        ...item,
        color: colors[index % colors.length],
        displayName: item.displayName ?? item.role ?? "未命名角色",
        role: item.role ?? item.displayName ?? "unknown",
        users: item.users ?? 0,
    }));
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

    if (hours > 0) {
        return `${hours} 小时 ${minutes} 分`;
    }

    return `${minutes} 分钟`;
}

export function formatJourneyDuration(durationMs?: number | null): string {
    if (!durationMs || durationMs <= 0) {
        return "—";
    }

    const seconds = Math.ceil(durationMs / 1000);

    return seconds < 60 ? "不足 1 分钟" : formatUptime(seconds);
}

export function resultTone(result: string): { label: string; tone: DashboardTone } {
    if (result === "SUCCESS") {
        return { label: "成功", tone: "green" };
    }

    if (result === "DENIED") {
        return { label: "拒绝", tone: "gold" };
    }

    return { label: "失败", tone: "red" };
}

export function userLabel(name?: string | null): string {
    if (!name || name === "anonymous") {
        return "匿名用户";
    }

    return name === "unknown" ? "未知用户" : name;
}

export function operationLabel(operation: string): string {
    const labels: Record<string, string> = {
        AUDIT_QUERY: "审计查询",
        BIND: "绑定",
        CREATE: "创建",
        DELETE: "删除",
        LOGIN: "登录",
        LOGOUT: "退出登录",
        NAVIGATION: "导航访问",
        PASSKEY: "通行密钥",
        PAT_CREATE: "创建访问令牌",
        PAT_DENIED: "访问令牌拒绝",
        PAT_REVOKE: "吊销访问令牌",
        READ: "读取",
        RISK_BLOCKED: "风险拦截",
        RISK_UNBAN: "解除封禁",
        TEMP_ROLE_APPLY: "申请临时角色",
        TEMP_ROLE_EXPIRE: "临时角色过期",
        TEMP_ROLE_GRANT: "授予临时角色",
        TEMP_ROLE_REVOKE: "收回临时角色",
        TOTP_BIND: "绑定动态口令",
        TOTP_LOGIN_FAILED: "动态口令登录失败",
        TOTP_LOGIN_SUCCESS: "动态口令登录成功",
        TOTP_UNBIND: "解绑动态口令",
        UPDATE: "更新",
        USER_CREATE: "创建用户",
        WEBHOOK_DELIVER: "投递 Webhook",
    };

    return labels[operation] ?? operation;
}
