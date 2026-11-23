import type { BatchCreateFailure, FeishuUserItem, ListedUser } from "@pudans-dev/api-client";

import { USERNAME_PATTERN } from "@/constants/validation";

export const MAX_USER_BATCH_SIZE = 200;
const MAX_USER_IMPORT_FILE_SIZE = 2 * 1024 * 1024;
export const FEISHU_USER_ID_PATTERN = /^(on|ou)_[A-Za-z0-9_-]+$/;

export type FeishuUserDraft = {
    providerUserId: string;
    userGroup?: string;
    username?: string;
};

export type UserDraftIssue = {
    index: number;
    message: string;
    providerUserId?: string;
};

export type UserCsvParseResult = {
    fileName: string;
    issues: UserDraftIssue[];
    rows: FeishuUserDraft[];
};

type UserDraftField = "providerUserId" | "userGroup" | "username";

const HEADER_ALIASES: Record<string, UserDraftField> = {
    feishuid: "providerUserId",
    feishuuserid: "providerUserId",
    openid: "providerUserId",
    unionid: "providerUserId",
    usergroup: "userGroup",
    username: "username",
    飞书id: "providerUserId",
    飞书用户id: "providerUserId",
    登录名: "username",
    用户名: "username",
    用户组: "userGroup",
};

function normalizeHeader(value: string): string {
    return value
        .replace(/^\uFEFF/, "")
        .trim()
        .toLowerCase()
        .replace(/[\s_-]/g, "");
}

export function normalizeFeishuUserDraft(draft: Partial<FeishuUserDraft>): FeishuUserDraft {
    return {
        providerUserId: draft.providerUserId?.trim() ?? "",
        userGroup: draft.userGroup?.trim() || undefined,
        username: draft.username?.trim().toLowerCase() || undefined,
    };
}

export function feishuUsernameIssue(username?: string): string | undefined {
    const normalized = username?.trim() ?? "";

    if (normalized && (normalized.length > 63 || !USERNAME_PATTERN.test(normalized))) {
        return "登录名只能包含小写字母、数字、- 和 .，且以字母或数字开头和结尾。";
    }

    return undefined;
}

export function validateFeishuUserDrafts(users: FeishuUserDraft[]): UserDraftIssue[] {
    const issues: UserDraftIssue[] = [];
    const providerUserIds = new Map<string, number>();

    if (users.length === 0) {
        issues.push({ index: 0, message: "请至少填写一个飞书用户 ID。" });
        return issues;
    }

    if (users.length > MAX_USER_BATCH_SIZE) {
        issues.push({ index: 0, message: `单次最多处理 ${MAX_USER_BATCH_SIZE} 个用户。` });
    }

    users.forEach((user, offset) => {
        const index = offset + 1;
        const providerUserId = user.providerUserId.trim();
        const username = user.username?.trim() ?? "";
        const userGroup = user.userGroup?.trim() ?? "";

        if (!providerUserId) {
            issues.push({ index, message: "飞书用户 ID 不能为空。" });
        } else if (!FEISHU_USER_ID_PATTERN.test(providerUserId)) {
            issues.push({
                index,
                message: "飞书用户 ID 需要以 on_ 或 ou_ 开头。",
                providerUserId,
            });
        } else if (providerUserIds.has(providerUserId)) {
            issues.push({
                index,
                message: `飞书用户 ID 与第 ${providerUserIds.get(providerUserId)} 条重复。`,
                providerUserId,
            });
        } else {
            providerUserIds.set(providerUserId, index);
        }

        const usernameIssue = feishuUsernameIssue(username);

        if (usernameIssue) {
            issues.push({
                index,
                message: usernameIssue,
                providerUserId,
            });
        }

        if (userGroup.length > 63) {
            issues.push({ index, message: "用户组名称不能超过 63 个字符。", providerUserId });
        }
    });

    return issues;
}

export function parseFeishuUserIds(value: string): {
    issues: UserDraftIssue[];
    rows: FeishuUserDraft[];
} {
    const rows = value
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
        .map((providerUserId) => normalizeFeishuUserDraft({ providerUserId }));

    return { issues: validateFeishuUserDrafts(rows), rows };
}

export function selectFailedFeishuUserDrafts(
    users: FeishuUserDraft[],
    failures: BatchCreateFailure[] = [],
): FeishuUserDraft[] {
    const failedIndexes = new Set(failures.map((failure) => failure.index));
    return users.filter((_, index) => failedIndexes.has(index + 1));
}

export function toFeishuUserItem(user: FeishuUserDraft): FeishuUserItem {
    const normalized = normalizeFeishuUserDraft(user);
    const isUnionId = normalized.providerUserId.startsWith("on_");

    return {
        openId: isUnionId ? undefined : normalized.providerUserId,
        unionId: isUnionId ? normalized.providerUserId : undefined,
        userGroup: normalized.userGroup,
        username: normalized.username,
    };
}

export async function parseFeishuUserCsv(file: File): Promise<UserCsvParseResult> {
    if (file.size > MAX_USER_IMPORT_FILE_SIZE) {
        throw new Error("CSV 文件不能超过 2 MB。");
    }

    const XLSX = await import("xlsx");
    const workbook = XLSX.read(await file.arrayBuffer(), { type: "array" });
    const sheetName = workbook.SheetNames[0];

    if (!sheetName) {
        throw new Error("CSV 文件没有工作表。");
    }

    const sheet = workbook.Sheets[sheetName];
    if (!sheet) {
        throw new Error("无法读取 CSV 工作表。");
    }

    const table = XLSX.utils.sheet_to_json<unknown[]>(sheet, {
        blankrows: false,
        defval: "",
        header: 1,
        raw: false,
    });
    const headerRow = table[0] ?? [];
    const columns = new Map<UserDraftField, number>();

    headerRow.forEach((value, index) => {
        const field = HEADER_ALIASES[normalizeHeader(String(value ?? ""))];
        if (field && !columns.has(field)) {
            columns.set(field, index);
        }
    });

    if (!columns.has("providerUserId")) {
        return {
            fileName: file.name,
            issues: [{ index: 0, message: "CSV 必须包含 feishuId、unionId 或 openId 列。" }],
            rows: [],
        };
    }

    const valueOf = (row: unknown[], field: UserDraftField) => {
        const index = columns.get(field);
        return index === undefined ? "" : String(row[index] ?? "").trim();
    };
    const dataRows = table.slice(1).filter((row) => row.some((value) => String(value ?? "").trim().length > 0));

    if (dataRows.length === 0) {
        return {
            fileName: file.name,
            issues: [{ index: 0, message: "CSV 中没有可导入的用户数据。" }],
            rows: [],
        };
    }

    const rows = dataRows.map((row) =>
        normalizeFeishuUserDraft({
            providerUserId: valueOf(row, "providerUserId"),
            userGroup: valueOf(row, "userGroup"),
            username: valueOf(row, "username"),
        }),
    );

    return {
        fileName: file.name,
        issues: validateFeishuUserDrafts(rows),
        rows,
    };
}

function csvField(value: unknown): string {
    const text = String(value ?? "");
    return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function usersToCsv(users: ListedUser[]): string {
    const rows = [
        ["username", "displayName", "email", "phone", "userGroup", "status", "registeredAt"],
        ...users.map((user) => [
            user.loginName || user.name,
            user.displayName,
            user.email,
            user.phone,
            user.userGroup,
            user.disabled ? "disabled" : "active",
            user.registeredAt,
        ]),
    ];

    return `\uFEFF${rows.map((row) => row.map(csvField).join(",")).join("\r\n")}\r\n`;
}

export function createFeishuUserCsvTemplate(): string {
    return "\uFEFFfeishuId,username,userGroup\r\n";
}
