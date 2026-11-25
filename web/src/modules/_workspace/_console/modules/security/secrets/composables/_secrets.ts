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

const SECRET_TYPE_LABELS: Record<string, string> = {
    Opaque: "普通键值",
    "kubernetes.io/basic-auth": "基础认证",
    "kubernetes.io/dockerconfigjson": "镜像仓库凭据",
    "kubernetes.io/ssh-auth": "SSH 凭据",
    "kubernetes.io/tls": "TLS 证书",
};

export type SecretEditorEntry = {
    key: string;
    originalKey?: string;
    value?: string;
};

export type SecretEditorValues = {
    entries?: SecretEditorEntry[];
    name: string;
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

function escapeJsonPointer(value: string): string {
    return value.replaceAll("~", "~0").replaceAll("/", "~1");
}

export function buildCreateSecretPayload(values: SecretEditorValues): Secret {
    const entries = normalizedEntries(values.entries);
    const stringData = Object.fromEntries(entries.map((entry) => [entry.key, entry.value ?? ""]));

    return {
        apiVersion: "v1alpha1",
        kind: "Secret",
        metadata: { name: values.name.trim() },
        type: normalizedType(values.type),
        ...(entries.length > 0 ? { stringData } : {}),
    };
}

type BuildSecretPatchInput = {
    entries?: SecretEditorEntry[];
    hasStringData?: boolean;
    initialType?: string;
    removedKeys?: Iterable<string>;
    type?: string;
};

export function buildSecretPatch({
    entries,
    hasStringData,
    initialType,
    removedKeys,
    type,
}: BuildSecretPatchInput): JsonPatchInner[] {
    const operations: JsonPatchInner[] = [];
    const normalized = normalizedEntries(entries);
    const nextType = normalizedType(type);
    const currentType = normalizedType(initialType);
    const additions = new Map<string, string>();
    const removals = new Set(removedKeys ?? []);
    const finalKeys = new Set(normalized.map((entry) => entry.key));

    if (nextType !== currentType) {
        operations.push({ op: "add", path: "/type", value: nextType });
    }

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
            operations.push({ op: "remove", path: `/stringData/${escapeJsonPointer(key)}` });
        }
    }

    if (additions.size > 0) {
        if (!hasStringData) {
            operations.push({ op: "add", path: "/stringData", value: Object.fromEntries(additions) });
        } else {
            for (const [key, value] of additions) {
                operations.push({
                    op: "add",
                    path: `/stringData/${escapeJsonPointer(key)}`,
                    value,
                });
            }
        }
    }

    return operations;
}

export function duplicateSecretKey(entries?: SecretEditorEntry[]): string | undefined {
    const seen = new Set<string>();
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

    return undefined;
}

function normalizedEntries(entries?: SecretEditorEntry[]): SecretEditorEntry[] {
    return (entries ?? [])
        .map((entry) => ({
            key: entry.key.trim(),
            originalKey: entry.originalKey?.trim(),
            value: entry.value,
        }))
        .filter((entry) => Boolean(entry.key));
}

function normalizedType(type?: string): string {
    return type?.trim() || DEFAULT_SECRET_TYPE;
}
