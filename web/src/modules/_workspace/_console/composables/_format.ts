import { format } from "date-fns";

const KILOBYTE = 1024;
const BYTE_UNITS = ["KB", "MB", "GB", "TB"];
const IDENTIFIER_MASK_HEAD_LENGTH = 10;
const IDENTIFIER_MASK_TAIL_LENGTH = 5;
const IDENTIFIER_MASK_MIN_LENGTH = IDENTIFIER_MASK_HEAD_LENGTH + IDENTIFIER_MASK_TAIL_LENGTH + 3;
const MILLISECONDS_PER_SECOND = 1000;
const SECONDS_PER_MINUTE = 60;
const SECONDS_PER_HOUR = 3600;
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

// 录制时长等长跨度按小时、分钟展示。
export function formatElapsedTime(value?: number | null): string {
    if (!value || value <= 0) {
        return "—";
    }

    const totalSeconds = Math.floor(value / MILLISECONDS_PER_SECOND);
    const hours = Math.floor(totalSeconds / SECONDS_PER_HOUR);
    const minutes = Math.floor((totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
    const seconds = totalSeconds % SECONDS_PER_MINUTE;
    const pad = (part: number) => String(part).padStart(2, "0");

    if (hours > 0) {
        return `${hours} 小时 ${pad(minutes)} 分`;
    }
    if (minutes > 0) {
        return `${minutes} 分 ${pad(seconds)} 秒`;
    }

    return `${seconds} 秒`;
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

// 过长的标识只显示首尾，复制时仍使用完整值。
export function maskIdentifier(value?: string | null): string {
    if (!value || value.length <= IDENTIFIER_MASK_MIN_LENGTH) {
        return value ?? "";
    }

    return `${value.slice(0, IDENTIFIER_MASK_HEAD_LENGTH)}***${value.slice(-IDENTIFIER_MASK_TAIL_LENGTH)}`;
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
