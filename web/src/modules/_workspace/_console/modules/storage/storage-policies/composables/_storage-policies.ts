import type { ComponentType } from "react";

import { CloudIcon, HardDriveIcon } from "tdesign-icons-react";

import type { StoragePolicy, StoragePolicyType } from "../../../../composables/_storage";

export const STORAGE_POLICY_PERMISSIONS = {
    create: "storage:create",
    delete: "storage:delete",
    manage: "storage:manage",
    update: "storage:update",
    view: "storage:view",
} as const;

export const STORAGE_POLICIES_QUERY_ROOT = ["console", "storage-policies"] as const;
export const STORAGE_POLICIES_QUERY_KEY = [...STORAGE_POLICIES_QUERY_ROOT, "all"] as const;
export const STORAGE_POLICY_TYPES_QUERY_KEY = [...STORAGE_POLICIES_QUERY_ROOT, "types"] as const;
export const STORAGE_POLICY_LIST_SIZE = 1000;
export const STORAGE_POLICY_TYPE_ORDER: StoragePolicyType[] = [
    "local",
    "s3",
    "ks3",
    "oss",
    "cos",
    "obs",
    "qiniu",
    "upyun",
    "onedrive",
];

const MEBIBYTE = 1024 * 1024;
const ENDPOINT_REQUIRED = new Set<StoragePolicyType>(["oss", "cos", "obs", "qiniu", "upyun"]);
const REGION_SUPPORTED = new Set<StoragePolicyType>(["s3", "ks3", "oss", "cos", "obs"]);
const S3_PATH_STYLE_SUPPORTED = new Set<StoragePolicyType>(["s3", "ks3"]);

type StoragePolicyTypeMeta = {
    group: "local" | "object";
    icon: ComponentType<{ className?: string; size?: number | string }>;
    label: string;
};

const STORAGE_POLICY_TYPES: Record<string, StoragePolicyTypeMeta> = {
    cos: { group: "object", icon: CloudIcon, label: "COS" },
    ks3: { group: "object", icon: CloudIcon, label: "KS3" },
    local: { group: "local", icon: HardDriveIcon, label: "本地存储" },
    obs: { group: "object", icon: CloudIcon, label: "OBS" },
    onedrive: { group: "object", icon: CloudIcon, label: "OneDrive" },
    oss: { group: "object", icon: CloudIcon, label: "OSS" },
    qiniu: { group: "object", icon: CloudIcon, label: "七牛云" },
    s3: { group: "object", icon: CloudIcon, label: "S3" },
    upyun: { group: "object", icon: CloudIcon, label: "又拍云" },
};

export function storagePolicyTypeMeta(type?: string | null): StoragePolicyTypeMeta {
    const normalized = type?.trim().toLowerCase();

    if (!normalized) {
        return { group: "object", icon: CloudIcon, label: "未知类型" };
    }

    return STORAGE_POLICY_TYPES[normalized] ?? { group: "object", icon: CloudIcon, label: type?.trim() || "未知类型" };
}

export function storagePolicyDisplayName(policy: StoragePolicy): string {
    return policy.spec.displayName?.trim() || policy.metadata.name;
}

export function storagePolicyLocation(policy: StoragePolicy): string {
    const type = storagePolicyTypeMeta(policy.spec.type);

    if (type.group === "local") {
        return "本地目录";
    }

    return [policy.spec.server?.trim(), policy.spec.bucketName?.trim()]
        .filter((value): value is string => Boolean(value))
        .join(" / ");
}

export function isLocalStoragePolicy(type?: StoragePolicyType | null): boolean {
    return type === "local";
}

export function isOneDriveStoragePolicy(type?: StoragePolicyType | null): boolean {
    return type === "onedrive";
}

export function storagePolicyEndpointRequired(type?: StoragePolicyType | null): boolean {
    return Boolean(type && ENDPOINT_REQUIRED.has(type));
}

export function storagePolicyRegionSupported(type?: StoragePolicyType | null): boolean {
    return Boolean(type && REGION_SUPPORTED.has(type));
}

export function storagePolicyS3PathStyleSupported(type?: StoragePolicyType | null): boolean {
    return Boolean(type && S3_PATH_STYLE_SUPPORTED.has(type));
}

export function storagePolicyMinimumChunkMegabytes(type?: StoragePolicyType | null): number {
    return type === "s3" || type === "ks3" ? 5 : 1;
}

export function storagePolicyMegabytesToBytes(megabytes?: number | null): number {
    if (!megabytes || megabytes <= 0) {
        return 0;
    }
    return Math.round(megabytes * MEBIBYTE);
}

export function parseStoragePolicyFileTypes(value?: string | null): string[] {
    const normalized = new Set<string>();
    for (const part of (value ?? "").split(/[,，\s]+/)) {
        const extension = part.trim().replace(/^\./, "").toLowerCase();
        if (extension) {
            normalized.add(extension);
        }
    }
    return [...normalized];
}

export function newStoragePolicyName(): string {
    return `storage-policy-${crypto.randomUUID().replaceAll("-", "").slice(0, 10)}`;
}
