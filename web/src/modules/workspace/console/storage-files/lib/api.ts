import { axiosInstance } from "@pudans-dev/api-client";
import axios from "axios";

import { resolveApiError } from "@/lib/api-error";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

const DEFAULT_STORAGE_FILE_SORT = "metadata.creationTimestamp,desc";

export type StorageFileState = "UPLOADING" | "ACTIVE" | "TRASHED";

type StorageFileSpec = {
  policy: string;
  storageKey: string;
  originalName: string;
  extension?: string;
  contentType?: string;
  size?: number;
  owner?: string;
  module?: string;
  attributes?: Record<string, string>;
  state?: StorageFileState;
  sha256?: string;
  chunkSize?: number;
  partCount?: number;
  uploadExpiresAt?: string;
  uploadedAt?: string;
  trashedAt?: string;
  trashExpiresAt?: string;
};

export type StorageFileItem = {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    version?: number;
    creationTimestamp?: string;
    labels?: Record<string, string>;
    annotations?: Record<string, string>;
  };
  spec: StorageFileSpec;
};

export type StorageFilePage = {
  page: number;
  size: number;
  total: number;
  items: StorageFileItem[];
};

export type StorageFileListParams = {
  page: number;
  size: number;
  name?: string;
  owner?: string;
  module?: string;
  states?: StorageFileState[];
  policies?: string[];
  sort?: string;
};

export type StorageFileDownloadUrl = {
  url: string;
  expiresAt?: string;
};

type StorageUploadRequest = {
  policy?: string;
  owner?: string;
  module?: string;
  originPath?: string;
  originalName: string;
  contentType?: string;
  size: number;
  attributes?: Record<string, string>;
  sha256?: string;
};

type StorageUploadTicket = {
  fileId: string;
  policy: string;
  storageKey: string;
  chunkSize: number;
  partCount: number;
  expiresAt?: string;
  uploadToken: string;
  presigned: boolean;
};

type StorageFileListResponse = {
  page?: number;
  size?: number;
  total?: number;
  items?: StorageFileItem[];
};

type UploadPartUrl = {
  partNumber: number;
  url: string;
  expiresAt?: string;
};

type StorageUploadOptions = {
  policy?: string;
  owner?: string;
  module?: string;
  attributes?: Record<string, string>;
};

function fieldSelectors(params: StorageFileListParams): string[] {
  const selectors: string[] = [];
  if (params.name) {
    selectors.push(`metadata.name=${params.name}`);
  }
  if (params.owner) {
    selectors.push(`spec.owner=${params.owner}`);
  }
  if (params.module) {
    selectors.push(`spec.module=${params.module}`);
  }
  if (params.states && params.states.length === 1) {
    selectors.push(`spec.state=${params.states[0]}`);
  } else if (params.states && params.states.length > 1) {
    selectors.push(`spec.state=(${params.states.join(",")})`);
  }
  if (params.policies && params.policies.length === 1) {
    selectors.push(`spec.policy=${params.policies[0]}`);
  } else if (params.policies && params.policies.length > 1) {
    selectors.push(`spec.policy=(${params.policies.join(",")})`);
  }
  return selectors;
}

export async function fetchStorageFiles(params: StorageFileListParams): Promise<StorageFilePage> {
  const response = await axiosInstance.get<StorageFileListResponse>(`${API_PREFIX}/storagefiles`, {
    params: {
      page: params.page,
      size: params.size,
      sort: params.sort ?? DEFAULT_STORAGE_FILE_SORT,
      fieldSelector: fieldSelectors(params),
    },
  });
  return {
    page: response.data.page ?? params.page,
    size: response.data.size ?? params.size,
    total: response.data.total ?? 0,
    items: response.data.items ?? [],
  };
}

export async function trashStorageFile(name: string): Promise<StorageFileItem> {
  const response = await axiosInstance.post<StorageFileItem>(
    `${API_PREFIX}/storagefiles/${encodeURIComponent(name)}/trash`,
  );
  return response.data;
}

export async function restoreStorageFile(name: string): Promise<StorageFileItem> {
  const response = await axiosInstance.post<StorageFileItem>(
    `${API_PREFIX}/storagefiles/${encodeURIComponent(name)}/restore`,
  );
  return response.data;
}

export async function purgeStorageFile(name: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/storagefiles/${encodeURIComponent(name)}/purge`);
}

export async function createStorageFileDownloadUrl(name: string): Promise<StorageFileDownloadUrl> {
  const response = await axiosInstance.post<StorageFileDownloadUrl>(
    `${API_PREFIX}/storagefiles/${encodeURIComponent(name)}/download-url`,
  );
  return response.data;
}

async function initiateStorageUpload(request: StorageUploadRequest): Promise<StorageUploadTicket> {
  const response = await axiosInstance.post<StorageUploadTicket>(
    `${API_PREFIX}/storagefiles/-/upload`,
    request,
  );
  return response.data;
}

async function uploadStoragePart(
  fileId: string,
  uploadToken: string,
  partNumber: number,
  body: Blob,
  contentType: string,
): Promise<void> {
  await axiosInstance.put(
    `${API_PREFIX}/storagefiles/${encodeURIComponent(fileId)}/upload/parts/${partNumber}`,
    body,
    { params: { token: uploadToken }, headers: { "Content-Type": contentType } },
  );
}

async function presignStoragePart(
  fileId: string,
  uploadToken: string,
  partNumber: number,
): Promise<string> {
  const response = await axiosInstance.get<UploadPartUrl>(
    `${API_PREFIX}/storagefiles/${encodeURIComponent(fileId)}/upload/parts/${partNumber}/url`,
    { params: { token: uploadToken } },
  );
  return response.data.url;
}

async function completeStorageUpload(
  fileId: string,
  uploadToken: string,
): Promise<StorageFileItem> {
  const response = await axiosInstance.post<StorageFileItem>(
    `${API_PREFIX}/storagefiles/${encodeURIComponent(fileId)}/upload/complete`,
    [],
    { params: { token: uploadToken } },
  );
  return response.data;
}

export async function uploadStorageFile(
  file: File,
  options: StorageUploadOptions,
  onProgress?: (percent: number) => void,
): Promise<StorageFileItem> {
  const contentType = file.type || "application/octet-stream";
  const ticket = await initiateStorageUpload({
    policy: options.policy,
    owner: options.owner,
    module: options.module,
    originalName: file.name,
    contentType,
    size: file.size,
    attributes: options.attributes,
  });
  const partCount = Math.max(1, ticket.partCount);
  const chunkSize = ticket.chunkSize > 0 ? ticket.chunkSize : Math.max(file.size, 1);
  for (let part = 1; part <= partCount; part += 1) {
    const start = (part - 1) * chunkSize;
    const chunk = file.slice(start, Math.min(start + chunkSize, file.size));
    if (ticket.presigned) {
      const url = await presignStoragePart(ticket.fileId, ticket.uploadToken, part);
      await axios.put(url, chunk, { headers: { "Content-Type": contentType } });
    } else {
      await uploadStoragePart(ticket.fileId, ticket.uploadToken, part, chunk, contentType);
    }
    onProgress?.(Math.round((part / partCount) * 100));
  }
  return completeStorageUpload(ticket.fileId, ticket.uploadToken);
}

export function resolveStorageFileApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "存储文件不存在或已被删除。",
    conflict: "存储文件状态已变化，请刷新后重试。",
  });
}
