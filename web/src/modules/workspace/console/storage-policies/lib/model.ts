import {
  STORAGE_POLICY_API_VERSION,
  STORAGE_POLICY_KIND,
  type StoragePolicyItem,
  type StoragePolicySpec,
  type StoragePolicyType,
  type StoragePolicyTypeDescriptor,
} from "./api";

const MEBIBYTE = 1024 * 1024;

const STORAGE_TYPE_ORDER: StoragePolicyType[] = [
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

const TYPE_FALLBACK: Record<StoragePolicyType, { label: string; description: string }> = {
  local: { label: "本机存储", description: "使用服务器本地磁盘保存文件。" },
  s3: { label: "S3 兼容", description: "兼容 Amazon S3 API 的对象存储。" },
  ks3: { label: "金山云 KS3", description: "金山云对象存储服务。" },
  oss: { label: "阿里云 OSS", description: "阿里云对象存储服务。" },
  cos: { label: "腾讯云 COS", description: "腾讯云对象存储服务。" },
  obs: { label: "华为云 OBS", description: "华为云对象存储服务。" },
  qiniu: { label: "七牛", description: "七牛云对象存储服务。" },
  upyun: { label: "又拍云", description: "又拍云对象存储服务。" },
  onedrive: { label: "OneDrive", description: "Microsoft OneDrive 或 SharePoint。" },
};

const ENDPOINT_REQUIRED = new Set<StoragePolicyType>([
  "s3",
  "ks3",
  "oss",
  "cos",
  "obs",
  "qiniu",
  "upyun",
]);

const REGION_TYPES = new Set<StoragePolicyType>(["s3", "ks3", "oss", "cos", "obs"]);

const S3_STYLE_TYPES = new Set<StoragePolicyType>(["s3", "ks3"]);

export function storageTypeLabel(
  type: StoragePolicyType,
  descriptors?: readonly StoragePolicyTypeDescriptor[],
): string {
  return descriptors?.find((item) => item.type === type)?.displayName ?? TYPE_FALLBACK[type].label;
}

export function storageTypeDescription(
  type: StoragePolicyType,
  descriptors?: readonly StoragePolicyTypeDescriptor[],
): string {
  return (
    descriptors?.find((item) => item.type === type)?.description ?? TYPE_FALLBACK[type].description
  );
}

export function orderedStorageTypes(
  descriptors?: readonly StoragePolicyTypeDescriptor[],
): StoragePolicyTypeDescriptor[] {
  const available = new Map((descriptors ?? []).map((item) => [item.type, item]));
  return STORAGE_TYPE_ORDER.map((type) => {
    const descriptor = available.get(type);
    return (
      descriptor ?? {
        type,
        displayName: TYPE_FALLBACK[type].label,
        description: TYPE_FALLBACK[type].description,
        credentialsRequired: type !== "local",
        capabilities: [],
      }
    );
  });
}

export function requiresEndpoint(type: StoragePolicyType): boolean {
  return ENDPOINT_REQUIRED.has(type);
}

export function supportsRegion(type: StoragePolicyType): boolean {
  return REGION_TYPES.has(type);
}

export function supportsS3PathStyle(type: StoragePolicyType): boolean {
  return S3_STYLE_TYPES.has(type);
}

export function supportsCors(descriptor?: StoragePolicyTypeDescriptor): boolean {
  return descriptor?.capabilities?.includes("CORS_CONFIGURABLE") ?? false;
}

export function isOneDrive(type: StoragePolicyType): boolean {
  return type === "onedrive";
}

export function isLocal(type: StoragePolicyType): boolean {
  return type === "local";
}

export function requiresStoredCredentials(type: StoragePolicyType): boolean {
  return type !== "local";
}

function defaultSpec(type: StoragePolicyType): StoragePolicySpec {
  return {
    displayName: "",
    type,
    maxSize: 0,
    dirNameRule: "uploads/{path}",
    fileNameRule: "{randomkey8}_{originname}",
    chunkSize: 0,
    fileTypes: [],
    fileTypeDenyList: false,
    nameRegexpDenyList: false,
    privateBucket: false,
    proxyDownload: true,
    defaultPolicy: false,
    credentials: {},
    settings: {
      deleteBatchSize: 1000,
      ...(isOneDrive(type) ? { tenantId: "common" } : {}),
    },
  };
}

function newStoragePolicyName(): string {
  return `storage-policy-${globalThis.crypto.randomUUID().replaceAll("-", "").slice(0, 10)}`;
}

export function createStoragePolicyDraft(type: StoragePolicyType): StoragePolicyItem {
  return {
    apiVersion: STORAGE_POLICY_API_VERSION,
    kind: STORAGE_POLICY_KIND,
    metadata: { name: newStoragePolicyName() },
    spec: defaultSpec(type),
  };
}

export function bytesToMegabytes(bytes: number | undefined): number {
  if (!bytes || bytes <= 0) {
    return 0;
  }
  return Math.round((bytes / MEBIBYTE) * 10) / 10;
}

export function megabytesToBytes(megabytes: number): number {
  if (!Number.isFinite(megabytes) || megabytes <= 0) {
    return 0;
  }
  return Math.round(megabytes * MEBIBYTE);
}

export function parseFileTypes(value: string): string[] {
  const seen = new Set<string>();
  for (const part of value.split(/[,，\s]+/)) {
    const normalized = part.trim().replace(/^\./, "").toLowerCase();
    if (normalized) {
      seen.add(normalized);
    }
  }
  return [...seen];
}

export function formatFileTypes(fileTypes: string[] | undefined): string {
  return (fileTypes ?? []).join(",");
}

export function formatFileSize(bytes: number | undefined): string {
  if (!bytes || bytes <= 0) {
    return "不限制";
  }
  if (bytes >= 1024 * MEBIBYTE) {
    return `${Math.round((bytes / (1024 * MEBIBYTE)) * 10) / 10} GB`;
  }
  if (bytes >= MEBIBYTE) {
    return `${Math.round((bytes / MEBIBYTE) * 10) / 10} MB`;
  }
  if (bytes >= 1024) {
    return `${Math.round((bytes / 1024) * 10) / 10} KB`;
  }
  return `${bytes} B`;
}

export function minimumChunkMegabytes(type: StoragePolicyType): number {
  return type === "s3" || type === "ks3" ? 5 : 1;
}

export function policyEndpoint(policy: StoragePolicyItem): string | undefined {
  return policy.spec.server?.trim() || undefined;
}
