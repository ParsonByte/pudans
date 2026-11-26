import type { PersonalAccessToken, PersonalAccessTokenList } from "@pudans-dev/api-client";
import { addDays, format } from "date-fns";

import { SECRET_QUERY_ROOT } from "./_secrets";

export const PAT_QUERY_ROOT = [...SECRET_QUERY_ROOT, "personal-access-tokens"] as const;
const PAT_API_VERSION = "security.pudans.tolim.cn/v1alpha1";
const PAT_KIND = "PersonalAccessToken";
const PAT_TOKEN_ANNOTATION = "security.pudans.tolim.cn/access-token";
export const PAT_NAME_MAX_LENGTH = 128;
export const PAT_DESCRIPTION_MAX_LENGTH = 256;
const PAT_DEFAULT_LIFETIME_DAYS = 30;
export const PAT_EXPIRING_DAYS = 7;
const PAT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

const DAY_MILLISECONDS = 24 * 60 * 60 * 1000;

export type PatStatus = "active" | "expired" | "expiring" | "revoked";

type PatStatusMeta = {
    label: string;
    theme: "default" | "danger" | "success" | "warning";
};

export const PAT_STATUS_META: Record<PatStatus, PatStatusMeta> = {
    active: { label: "有效", theme: "success" },
    expired: { label: "已过期", theme: "default" },
    expiring: { label: "即将过期", theme: "warning" },
    revoked: { label: "已撤销", theme: "danger" },
};

export const PAT_STATUS_OPTIONS = (Object.keys(PAT_STATUS_META) as PatStatus[]).map((status) => ({
    label: PAT_STATUS_META[status].label,
    value: status,
}));

export type PatCreateValues = {
    description?: string;
    expiresAt: string;
    name: string;
    roles?: string[];
};

export function patDisplayName(pat?: PersonalAccessToken | null): string {
    return pat?.spec?.name?.trim() || pat?.metadata?.name || "未命名令牌";
}

export function patDescription(pat?: PersonalAccessToken | null): string {
    return pat?.spec?.description?.trim() || "";
}

export function patRoles(pat?: PersonalAccessToken | null): string[] {
    return pat?.spec?.roles ?? [];
}

export function patToken(pat?: PersonalAccessToken | null): string | undefined {
    return pat?.metadata?.annotations?.[PAT_TOKEN_ANNOTATION];
}

function patExpiryTime(pat: PersonalAccessToken): number | undefined {
    if (!pat.spec?.expiresAt) {
        return undefined;
    }

    const time = new Date(pat.spec.expiresAt).getTime();

    return Number.isFinite(time) ? time : undefined;
}

// 状态按撤销优先、过期次之、临近过期最后判定。
export function patStatus(pat: PersonalAccessToken, now = Date.now()): PatStatus {
    if (pat.spec?.revoked) {
        return "revoked";
    }

    const expiresAt = patExpiryTime(pat);
    if (expiresAt === undefined) {
        return "active";
    }
    if (expiresAt <= now) {
        return "expired";
    }
    if (expiresAt <= now + PAT_EXPIRING_DAYS * DAY_MILLISECONDS) {
        return "expiring";
    }

    return "active";
}

export function patUsedRecently(pat: PersonalAccessToken, days = PAT_EXPIRING_DAYS, now = Date.now()): boolean {
    const lastUsed = pat.spec?.lastUsed;
    if (!lastUsed) {
        return false;
    }

    const time = new Date(lastUsed).getTime();

    return Number.isFinite(time) && now - time <= days * DAY_MILLISECONDS;
}

// 列表接口返回数组，生成客户端按分页对象声明，两种形态都归一为数组。
export function normalizePatList(
    value: PersonalAccessTokenList | PersonalAccessToken[] | null | undefined,
): PersonalAccessToken[] {
    if (!value) {
        return [];
    }

    return Array.isArray(value) ? value : (value.items ?? []);
}

export function defaultPatExpiryValue(): string {
    return format(addDays(new Date(), PAT_DEFAULT_LIFETIME_DAYS), PAT_DATETIME_FORMAT);
}

export function buildCreatePatPayload(values: PatCreateValues, username: string): PersonalAccessToken {
    return {
        apiVersion: PAT_API_VERSION,
        kind: PAT_KIND,
        metadata: { name: "" },
        spec: {
            description: values.description?.trim() || undefined,
            expiresAt: new Date(values.expiresAt.replace(" ", "T")).toISOString(),
            name: values.name.trim(),
            roles: values.roles ?? [],
            tokenId: "",
            username,
        },
    };
}
