import { format } from "date-fns";

const KILOBYTE = 1024;
const BYTE_UNITS = ["KB", "MB", "GB", "TB"];
const COUNT_FORMATTER = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 0 });

export function formatDateTime(value?: string | null): string {
    if (!value) {
        return "—";
    }

    const date = new Date(value);

    return Number.isNaN(date.getTime()) ? value : format(date, "yyyy-MM-dd HH:mm:ss");
}

export function formatCount(value?: number | null): string {
    return COUNT_FORMATTER.format(value ?? 0);
}

export function formatCountOrDash(value?: number | null): string {
    return value === undefined || value === null ? "—" : formatCount(value);
}

export function formatPercent(part?: number | null, total?: number | null): string {
    if (!part || !total || total <= 0) {
        return "0.0%";
    }

    return `${((part / total) * 100).toFixed(1)}%`;
}

export function formatDateLabel(value?: string | null): string {
    if (!value) {
        return "—";
    }

    const [, month, day] = value.split("-");

    return month && day ? `${Number(month)}月${Number(day)}日` : value;
}

export function formatDateTick(value?: string | null): string {
    if (!value) {
        return "";
    }

    const [, month, day] = value.split("-");

    return month && day ? `${Number(month)}/${Number(day)}` : value;
}

export function formatDuration(value?: number | null): string {
    if (value === undefined || value === null) {
        return "—";
    }

    return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`;
}

export function formatAverageSteps(value?: number | null): string {
    return value === undefined || value === null ? "—" : value.toFixed(1);
}

export function formatPath(value?: string | null): string {
    const path = value?.trim();

    if (!path) {
        return "—";
    }

    try {
        return decodeURIComponent(path);
    } catch {
        return path;
    }
}

const OPERATION_LABELS: Record<string, string> = {
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
    RISK_ALERT: "风险告警",
    RISK_BLOCKED: "风险拦截",
    RISK_UNBAN: "解除封禁",
    SESSION: "会话",
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

export function operationLabel(operation?: string | null): string {
    if (!operation) {
        return "—";
    }

    return OPERATION_LABELS[operation] ?? operation;
}

export function formatBytes(value?: number | null): string {
    if (value === undefined || value === null) {
        return "—";
    }

    if (value < KILOBYTE) {
        return `${value} B`;
    }

    let size = value / KILOBYTE;
    let unitIndex = 0;

    while (size >= KILOBYTE && unitIndex < BYTE_UNITS.length - 1) {
        size /= KILOBYTE;
        unitIndex += 1;
    }

    return `${size.toFixed(1)} ${BYTE_UNITS[unitIndex]}`;
}
