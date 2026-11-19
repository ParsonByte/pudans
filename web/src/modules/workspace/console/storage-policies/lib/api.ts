import { axiosInstance } from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

export const STORAGE_POLICY_API_VERSION = "pudans.tolim.cn/v1alpha1";

export const STORAGE_POLICY_KIND = "StoragePolicy";

export type StoragePolicyType =
  "local" | "s3" | "ks3" | "oss" | "cos" | "obs" | "qiniu" | "upyun" | "onedrive";

export type StoragePolicyCredentials = {
  accessKey?: string;
  secretKey?: string;
  token?: string;
  clientSecret?: string;
};

export type StoragePolicySettings = {
  region?: string;
  s3PathStyle?: boolean;
  deleteBatchSize?: number;
  tenantId?: string;
  driveId?: string;
};

export type StoragePolicySpec = {
  displayName: string;
  description?: string;
  type: StoragePolicyType;
  server?: string;
  bucketName?: string;
  privateBucket?: boolean;
  maxSize?: number;
  dirNameRule?: string;
  fileNameRule?: string;
  chunkSize?: number;
  fileTypes?: string[];
  fileTypeDenyList?: boolean;
  nameRegexp?: string;
  nameRegexpDenyList?: boolean;
  proxyDownload?: boolean;
  defaultPolicy?: boolean;
  credentials?: StoragePolicyCredentials;
  settings?: StoragePolicySettings;
};

type StoragePolicyStatus = {
  ready?: boolean;
  message?: string;
  lastTestedAt?: string;
  credentialConfigured?: Record<string, boolean>;
};

export type StoragePolicyItem = {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    version?: number;
    creationTimestamp?: string;
    labels?: Record<string, string>;
    annotations?: Record<string, string>;
  };
  spec: StoragePolicySpec;
  status?: StoragePolicyStatus;
};

export type StoragePolicyTypeDescriptor = {
  type: StoragePolicyType;
  displayName: string;
  description: string;
  credentialsRequired: boolean;
  capabilities: string[];
};

type StoragePolicyListResponse = {
  items?: StoragePolicyItem[];
  total?: number;
};

export type StoragePolicyPage = {
  items: StoragePolicyItem[];
  total: number;
};

export type StoragePolicyValidationResult = {
  valid: boolean;
  errors: string[];
};

export type StorageTestResult = {
  success: boolean;
  message: string;
  testedAt?: string;
};

export type OneDriveOAuthStatus = {
  policy: string;
  authorized: boolean;
  message: string;
};

export type OneDriveAuthorizeUrl = {
  url: string;
  state: string;
  redirectUri: string;
};

type OneDriveRoot = {
  driveId: string;
};

export async function fetchStoragePolicies(page = 1, size = 10): Promise<StoragePolicyPage> {
  const response = await axiosInstance.get<StoragePolicyListResponse>(
    `${API_PREFIX}/storagepolicies`,
    { params: { page, size } },
  );
  return {
    items: response.data.items ?? [],
    total: response.data.total ?? 0,
  };
}

export async function fetchStoragePolicyTypes(): Promise<StoragePolicyTypeDescriptor[]> {
  const response = await axiosInstance.get<StoragePolicyTypeDescriptor[]>(
    `${API_PREFIX}/storagepolicies/-/types`,
  );
  return Array.isArray(response.data) ? response.data : [];
}

export async function createStoragePolicy(policy: StoragePolicyItem): Promise<StoragePolicyItem> {
  const response = await axiosInstance.post<StoragePolicyItem>(
    `${API_PREFIX}/storagepolicies`,
    policy,
  );
  return response.data;
}

export async function updateStoragePolicy(policy: StoragePolicyItem): Promise<StoragePolicyItem> {
  const response = await axiosInstance.put<StoragePolicyItem>(
    `${API_PREFIX}/storagepolicies/${encodeURIComponent(policy.metadata.name)}`,
    policy,
  );
  return response.data;
}

export async function deleteStoragePolicy(name: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/storagepolicies/${encodeURIComponent(name)}`);
}

export async function validateStoragePolicy(
  policy: StoragePolicyItem,
): Promise<StoragePolicyValidationResult> {
  const response = await axiosInstance.post<StoragePolicyValidationResult>(
    `${API_PREFIX}/storagepolicies/-/validate`,
    policy,
  );
  return response.data;
}

export async function testStoragePolicy(name: string): Promise<StorageTestResult> {
  const response = await axiosInstance.post<StorageTestResult>(
    `${API_PREFIX}/storagepolicies/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}

export async function configureStoragePolicyCors(name: string, origins: string[]): Promise<void> {
  await axiosInstance.post(`${API_PREFIX}/storagepolicies/${encodeURIComponent(name)}/cors`, {
    origins,
  });
}

export async function fetchOneDriveAuthorizeUrl(name: string): Promise<OneDriveAuthorizeUrl> {
  const response = await axiosInstance.get<OneDriveAuthorizeUrl>(
    `${API_PREFIX}/storagepolicies/${encodeURIComponent(name)}/oauth/authorize`,
  );
  return response.data;
}

export async function fetchOneDriveStatus(name: string): Promise<OneDriveOAuthStatus> {
  const response = await axiosInstance.get<OneDriveOAuthStatus>(
    `${API_PREFIX}/storagepolicies/${encodeURIComponent(name)}/oauth/status`,
  );
  return response.data;
}

export async function resolveOneDriveRoot(name: string, url: string): Promise<OneDriveRoot> {
  const response = await axiosInstance.get<OneDriveRoot>(
    `${API_PREFIX}/storagepolicies/${encodeURIComponent(name)}/oauth/root`,
    { params: { url } },
  );
  return response.data;
}

export function resolveStorageApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "存储策略不存在或已被删除。",
    conflict: "存储策略状态冲突，请刷新后重试。",
  });
}
