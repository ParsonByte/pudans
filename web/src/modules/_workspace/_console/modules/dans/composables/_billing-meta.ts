import { format } from "date-fns";

import type { AiPriceRule } from "./_dans";

const TARGET_TYPE_FORM_OPTIONS = [
    { label: "模型", value: "model" },
    { label: "MCP 工具", value: "mcp_tool" },
] as const;

export const TARGET_TYPE_OPTIONS = [{ label: "全部目标", value: "all" }, ...TARGET_TYPE_FORM_OPTIONS] as const;

const PRICING_MODE_FORM_OPTIONS = [
    { label: "按 Token", value: "token" },
    { label: "按单位", value: "unit_credit" },
    { label: "按调用", value: "mcp_call" },
] as const;

export const PRICING_MODE_OPTIONS = [{ label: "全部计价方式", value: "all" }, ...PRICING_MODE_FORM_OPTIONS] as const;

export const PRICE_RULE_STATE_OPTIONS = [
    { label: "全部时态", value: "all" },
    { label: "生效中", value: "active" },
    { label: "待生效", value: "pending" },
    { label: "已失效", value: "expired" },
] as const;

export const BILLING_STATUS_OPTIONS = [
    { label: "启用", value: "active" },
    { label: "停用", value: "disabled" },
] as const;

export const STATUS_OPTIONS = [{ label: "全部状态", value: "all" }, ...BILLING_STATUS_OPTIONS] as const;

export const UNIT_OPTIONS = [
    { label: "每次请求", value: "request" },
    { label: "每张图片", value: "image" },
    { label: "每秒视频", value: "video_second" },
    { label: "每秒音频", value: "audio_second" },
    { label: "每百万像素", value: "megapixel" },
] as const;

export const WEEKDAY_OPTIONS = [
    { label: "周一", value: "mon" },
    { label: "周二", value: "tue" },
    { label: "周三", value: "wed" },
    { label: "周四", value: "thu" },
    { label: "周五", value: "fri" },
    { label: "周六", value: "sat" },
    { label: "周日", value: "sun" },
] as const;

const TARGET_TYPE_LABELS: Record<string, string> = {
    model: "模型",
    mcp_tool: "MCP 工具",
};

const PRICING_MODE_LABELS: Record<string, string> = {
    mcp_call: "按调用",
    token: "按 Token",
    unit_credit: "按单位",
};

const UNIT_LABELS: Record<string, string> = {
    audio_second: "每秒音频",
    image: "每张图片",
    megapixel: "每百万像素",
    request: "每次请求",
    video_second: "每秒视频",
};

const SUBJECT_TYPE_LABELS: Record<string, string> = {
    role: "角色",
    user: "用户",
    user_group: "用户组",
    workspace: "工作区",
};

const CREDIT_FORMATTER = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 4 });

export type PriceRuleState = "active" | "expired" | "pending" | "unknown";

export function targetTypeLabel(value?: string): string {
    return value ? (TARGET_TYPE_LABELS[value] ?? value) : "未设置";
}

export function pricingModeLabel(value?: string): string {
    return value ? (PRICING_MODE_LABELS[value] ?? value) : "未设置";
}

export function unitLabel(value?: string): string {
    return value ? (UNIT_LABELS[value] ?? value) : "—";
}

export function subjectTypeLabel(value?: string): string {
    return value ? (SUBJECT_TYPE_LABELS[value] ?? value) : "未设置";
}

export function planStatusLabel(value?: string): string {
    if (value === "active") {
        return "启用";
    }
    if (value === "disabled") {
        return "停用";
    }

    return value ?? "未设置";
}

export function assignmentStatusLabel(value?: string): string {
    if (value === "active") {
        return "生效";
    }
    if (value === "disabled") {
        return "停用";
    }

    return value ?? "未设置";
}

export function priceRuleState(rule: AiPriceRule, now = Date.now()): PriceRuleState {
    const start = rule.effectiveFrom ? new Date(rule.effectiveFrom).getTime() : Number.NaN;
    if (!Number.isFinite(start)) {
        return "unknown";
    }
    if (start > now) {
        return "pending";
    }
    const end = rule.effectiveTo ? new Date(rule.effectiveTo).getTime() : Number.NaN;
    if (Number.isFinite(end) && end <= now) {
        return "expired";
    }

    return "active";
}

export function priceRuleStateLabel(state: PriceRuleState): string {
    if (state === "active") {
        return "生效中";
    }
    if (state === "pending") {
        return "待生效";
    }
    if (state === "expired") {
        return "已失效";
    }

    return "时态未知";
}

export function formatCredits(value?: number | null): string {
    return value === undefined || value === null ? "—" : CREDIT_FORMATTER.format(value);
}

export function formatWindowDuration(seconds?: number | null): string {
    if (!seconds || seconds <= 0) {
        return "—";
    }
    if (seconds % 3_600 === 0) {
        return `${seconds / 3_600} 小时`;
    }
    if (seconds % 60 === 0) {
        return `${seconds / 60} 分钟`;
    }

    return `${seconds} 秒`;
}

export function formatWindowDays(days?: number | null): string {
    return days && days > 0 ? `${days} 天` : "—";
}

export function weeklyAnchorLabel(value?: string): string {
    return value === "assignment_start" ? "从分配生效时间起算" : (value ?? "未设置");
}

export function dateTimeInputValue(value?: string | number | Date | null): string | undefined {
    if (!value) {
        return undefined;
    }
    const date = value instanceof Date ? value : new Date(value);

    return Number.isNaN(date.getTime()) ? undefined : format(date, "yyyy-MM-dd HH:mm:ss");
}

export function instantValue(value?: string | number | Date | null): string | undefined {
    if (!value) {
        return undefined;
    }
    const date = value instanceof Date ? value : new Date(value);

    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}
