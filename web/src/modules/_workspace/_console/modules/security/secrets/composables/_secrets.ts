import type { JsonPatchInner, Secret, SecretCatalogItem } from "@pudans-dev/api-client";

import { USERNAME_PATTERN } from "@/constants/validation";

export const SECRET_PERMISSIONS = {
    create: "secrets:create",
    delete: "secrets:delete",
    update: "secrets:update",
    view: "secrets:view",
} as const;

export const SECRET_QUERY_ROOT = ["console", "secrets"] as const;
export const DEFAULT_SECRET_TYPE = "Opaque";
export const SECRET_GENERATED_KEY = "token";
export const SECRET_KEY_PATTERN = /^[-._a-zA-Z0-9]+$/;
export const SECRET_NAME_PATTERN = USERNAME_PATTERN;
export const SECRET_KEY_MAX_LENGTH = 253;
export const SECRET_NAME_MAX_LENGTH = 253;
export const SECRET_TYPE_MAX_LENGTH = 128;

const SECRET_BASE64_PATTERN = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;

const SECRET_TYPE_LABELS: Record<string, string> = {
    Opaque: "普通键值",
    "kubernetes.io/basic-auth": "基础认证",
    "kubernetes.io/dockerconfigjson": "镜像仓库凭据",
    "kubernetes.io/ssh-auth": "SSH 凭据",
    "kubernetes.io/tls": "TLS 证书",
};

export type SecretEntryKind = "data" | "stringData";

export type SecretEditorEntry = {
    key: string;
    originalKey?: string;
    value?: string;
};

export type SecretEditorValues = {
    dataEntries?: SecretEditorEntry[];
    name: string;
    stringEntries?: SecretEditorEntry[];
    type?: string;
};

export function secretTypeLabel(type?: string | null): string {
    if (!type) {
        return SECRET_TYPE_LABELS[DEFAULT_SECRET_TYPE];
    }

    return SECRET_TYPE_LABELS[type] ?? type;
}

export function secretEntryCount(item?: SecretCatalogItem): number {
    return item?.keys?.length ?? 0;
}

export function generateSecretValue(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(24));

    return `sk-${Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("")}`;
}

export function normalizeSecretDataValue(value?: string): string {
    return value?.replace(/\s+/g, "") ?? "";
}

export function isSecretBase64(value?: string): boolean {
    const normalized = normalizeSecretDataValue(value);

    return normalized.length > 0 && SECRET_BASE64_PATTERN.test(normalized);
}

function escapeJsonPointer(value: string): string {
    return value.replaceAll("~", "~0").replaceAll("/", "~1");
}

export function buildCreateSecretPayload({ dataEntries, name, stringEntries, type }: SecretEditorValues): Secret {
    const stringData = entriesToMap(stringEntries, "stringData");
    const data = entriesToMap(dataEntries, "data");

    return {
        apiVersion: "v1alpha1",
        kind: "Secret",
        metadata: { name: name.trim() },
        type: normalizedType(type),
        ...(Object.keys(stringData).length > 0 ? { stringData } : {}),
        ...(Object.keys(data).length > 0 ? { data } : {}),
    };
}

type BuildSecretPatchInput = {
    dataEntries?: SecretEditorEntry[];
    hasData?: boolean;
    hasStringData?: boolean;
    initialType?: string;
    removedDataKeys?: Iterable<string>;
    removedStringKeys?: Iterable<string>;
    stringEntries?: SecretEditorEntry[];
    type?: string;
};

export function buildSecretPatch({
    dataEntries,
    hasData,
    hasStringData,
    initialType,
    removedDataKeys,
    removedStringKeys,
    stringEntries,
    type,
}: BuildSecretPatchInput): JsonPatchInner[] {
    const operations: JsonPatchInner[] = [];
    const nextType = normalizedType(type);
    const currentType = normalizedType(initialType);

    if (nextType !== currentType) {
        operations.push({ op: "add", path: "/type", value: nextType });
    }

    appendMapPatch(operations, "stringData", stringEntries, hasStringData, removedStringKeys, "stringData");
    appendMapPatch(operations, "data", dataEntries, hasData, removedDataKeys, "data");

    return operations;
}

export function duplicateSecretKey(...entryLists: Array<SecretEditorEntry[] | undefined>): string | undefined {
    const seen = new Set<string>();
    for (const entries of entryLists) {
        for (const entry of entries ?? []) {
            const key = entry.key.trim();
            if (!key) {
                continue;
            }
            if (seen.has(key)) {
                return key;
            }
            seen.add(key);
        }
    }

    return undefined;
}

function appendMapPatch(
    operations: JsonPatchInner[],
    field: SecretEntryKind,
    entries: SecretEditorEntry[] | undefined,
    hasField: boolean | undefined,
    removedKeys: Iterable<string> | undefined,
    kind: SecretEntryKind,
): void {
    const normalized = normalizedEntries(entries, kind);
    const additions = new Map<string, string>();
    const removals = new Set(removedKeys ?? []);
    const finalKeys = new Set(normalized.map((entry) => entry.key));

    for (const entry of normalized) {
        if (entry.originalKey && entry.originalKey !== entry.key) {
            removals.add(entry.originalKey);
        }
        if (!entry.originalKey || entry.originalKey !== entry.key || Boolean(entry.value)) {
            additions.set(entry.key, entry.value ?? "");
        }
    }

    for (const key of removals) {
        if (!finalKeys.has(key)) {
            operations.push({ op: "remove", path: `/${field}/${escapeJsonPointer(key)}` });
        }
    }

    if (additions.size === 0) {
        return;
    }

    if (!hasField) {
        operations.push({ op: "add", path: `/${field}`, value: Object.fromEntries(additions) });
        return;
    }

    for (const [key, value] of additions) {
        operations.push({
            op: "add",
            path: `/${field}/${escapeJsonPointer(key)}`,
            value,
        });
    }
}

function entriesToMap(entries: SecretEditorEntry[] | undefined, kind: SecretEntryKind): Record<string, string> {
    const values: Record<string, string> = {};
    for (const entry of normalizedEntries(entries, kind)) {
        values[entry.key] = entry.value ?? "";
    }

    return values;
}

function normalizedEntries(entries: SecretEditorEntry[] | undefined, kind: SecretEntryKind): SecretEditorEntry[] {
    return (entries ?? [])
        .map((entry) => ({
            key: entry.key.trim(),
            originalKey: entry.originalKey?.trim(),
            value: kind === "data" ? normalizeSecretDataValue(entry.value) : entry.value,
        }))
        .filter((entry) => Boolean(entry.key));
}

function normalizedType(type?: string): string {
    return type?.trim() || DEFAULT_SECRET_TYPE;
}
