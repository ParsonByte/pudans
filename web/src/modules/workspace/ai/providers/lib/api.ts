import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { resolveApiError } from "@/lib/api-error";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

export const AI_API_VERSION = "pudans.tolim.cn/v1alpha1";

export const AI_PROVIDER_KIND = "AiProvider";

export const AI_PROVIDER_ACCOUNT_KIND = "AiProviderAccount";

export const AI_MODEL_KIND = "AiModel";

export type AiProviderProtocol =
  | "OPENAI_CHAT"
  | "OPENAI_RESPONSES"
  | "ANTHROPIC"
  | "DEEPSEEK"
  | "DEEPSEEK_CHAT"
  | "DEEPSEEK_RESPONSES"
  | "MINIMAX"
  | "MINIMAX_CHAT"
  | "MINIMAX_NATIVE"
  | "MINIMAX_RESPONSES"
  | "QIANWEN"
  | "GEMINI"
  | "OLLAMA"
  | "CUSTOM_HTTP";

export type AiProviderRouting =
  | "SMART"
  | "ORDER"
  | "ROTATE"
  | "USAGE"
  | "PACE"
  | "WEIGHT"
  | "FREE"
  | "FAST"
  | "CHEAP"
  | "DRAIN"
  | "EXPIRING";

export type AiProviderAffinity = "AUTO" | "SESSION" | "TURN" | "OFF" | "STRICT";

export type AiAccountType = "API_KEY" | "OAUTH" | "CUSTOM";

export type AiQuotaUnit = "TOKEN" | "REQUEST" | "CREDIT" | "USD";

export type AiModelType =
  "CHAT" | "EMBEDDING" | "RERANK" | "IMAGE" | "VIDEO" | "SPEECH" | "AUDIO" | "MUSIC" | "THREE_D";

export type AiModelTier = "CHEAP" | "MEDIUM" | "HIGH" | "ULTRA";

export type AiMetadata = {
  name: string;
  version?: number;
  creationTimestamp?: string;
  labels?: Record<string, string>;
  annotations?: Record<string, string>;
};

export type AiProviderSpec = {
  displayName: string;
  protocol: AiProviderProtocol;
  baseUrl?: string;
  headers?: Record<string, string>;
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  maxConcurrency?: number;
  maxRPM?: number;
  routing?: AiProviderRouting;
  affinity?: AiProviderAffinity;
  fallback?: string[];
  free?: boolean;
  enabled?: boolean;
  [key: string]: unknown;
};

export type AiProviderStatus = {
  healthScore?: number;
  lastTestedAt?: string;
  message?: string;
};

export type AiProviderItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiProviderSpec;
  status?: AiProviderStatus;
};

export type AiProviderAccountQuota = {
  unit: AiQuotaUnit;
  limit?: number;
  used?: number;
  resetAt?: string;
};

export type AiProviderAccountQuotaWindow = {
  name?: string;
  unit?: AiQuotaUnit;
  limit?: number;
  used?: number;
  resetsAt?: string;
};

export type AiProviderAccountBalance = {
  unit: AiQuotaUnit;
  left?: number;
};

export type AiProviderAccountSpec = {
  provider: string;
  displayName?: string;
  accountType: AiAccountType;
  weight: number;
  priority: number;
  enabled: boolean;
  maxConcurrency?: number;
  maxRPM?: number;
  freePlan: boolean;
  creditsEnabled: boolean;
  quota?: AiProviderAccountQuota;
  windows?: AiProviderAccountQuotaWindow[];
  balance?: AiProviderAccountBalance;
  [key: string]: unknown;
};

export type AiProviderAccountStatus = {
  healthScore?: number;
  lastUsedAt?: string;
  lastError?: string;
};

export type AiProviderAccountItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiProviderAccountSpec;
  status?: AiProviderAccountStatus;
};

export type AiModelCapability =
  | "CHAT"
  | "TOOLS"
  | "JSON"
  | "VISION"
  | "AUDIO_UNDERSTANDING"
  | "VIDEO_UNDERSTANDING"
  | "EMBEDDING"
  | "RERANK"
  | "IMAGE_GENERATION"
  | "IMAGE_EDITING"
  | "VIDEO_GENERATION"
  | "SPEECH_RECOGNITION"
  | "SPEECH_SYNTHESIS"
  | "AUDIO_GENERATION"
  | "MUSIC_GENERATION"
  | "THREE_D_GENERATION"
  | "OCR"
  | "STREAM";

export type AiModelSpec = {
  provider: string;
  modelCode: string;
  displayName?: string;
  modelType: AiModelType;
  tier?: AiModelTier;
  contextWindow?: number;
  maxOutputTokens?: number;
  supportsStream?: boolean;
  supportsTools?: boolean;
  supportsJson?: boolean;
  supportsVision?: boolean;
  supportsAsync?: boolean;
  capabilities?: AiModelCapability[];
  embeddingDim?: number;
  enabled?: boolean;
  [key: string]: unknown;
};

export type AiModelItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiModelSpec;
};

export type AiProviderTestResult = {
  successful: boolean;
  latencyMillis?: number;
  message?: string;
  model?: string;
};

export type AiRemoteModel = {
  id: string;
  object?: string;
  ownedBy?: string;
  name?: string;
  contextWindow?: number;
  maxOutputTokens?: number;
  inputModalities?: string[];
  outputModalities?: string[];
};

export type AiRemoteModelList = {
  object?: string;
  data?: AiRemoteModel[];
};

export type JsonPatchOperation = {
  op: "add" | "replace" | "remove";
  path: string;
  value?: unknown;
};

type ListResponse<T> = {
  items?: T[];
  total?: number;
};

type PageResult<T> = {
  items: T[];
  total: number;
};

const ALL_PAGE_SIZE = 500;

function toPageResult<T>(data: ListResponse<T>): PageResult<T> {
  return { items: data.items ?? [], total: data.total ?? 0 };
}

async function listAll<T>(path: string, params: Record<string, unknown> = {}): Promise<T[]> {
  const items: T[] = [];
  for (let page = 1; ; page += 1) {
    const response = await axiosInstance.get<ListResponse<T>>(`${API_PREFIX}${path}`, {
      params: { page, size: ALL_PAGE_SIZE, ...params },
    });
    const pageItems = response.data.items ?? [];
    items.push(...pageItems);
    if (pageItems.length === 0 || items.length >= (response.data.total ?? items.length)) {
      break;
    }
  }
  return items;
}

export async function fetchProviders(page = 1, size = 10): Promise<PageResult<AiProviderItem>> {
  const response = await axiosInstance.get<ListResponse<AiProviderItem>>(
    `${API_PREFIX}/aiproviders`,
    { params: { page, size } },
  );
  return toPageResult(response.data);
}

export async function fetchProvider(name: string): Promise<AiProviderItem> {
  const response = await axiosInstance.get<AiProviderItem>(
    `${API_PREFIX}/aiproviders/${encodeURIComponent(name)}`,
  );
  return response.data;
}

export function fetchAllProviderAccounts(): Promise<AiProviderAccountItem[]> {
  return listAll<AiProviderAccountItem>("/aiprovideraccounts");
}

export function fetchProviderAccounts(providerName: string): Promise<AiProviderAccountItem[]> {
  return listAll<AiProviderAccountItem>("/aiprovideraccounts", {
    fieldSelector: [`spec.provider=${providerName}`],
  });
}

export function fetchAllProviderModels(): Promise<AiModelItem[]> {
  return listAll<AiModelItem>("/aimodels");
}

export function fetchProviderModels(providerName: string): Promise<AiModelItem[]> {
  return listAll<AiModelItem>("/aimodels", {
    fieldSelector: [`spec.provider=${providerName}`],
  });
}

export async function createProvider(provider: AiProviderItem): Promise<AiProviderItem> {
  const response = await axiosInstance.post<AiProviderItem>(`${API_PREFIX}/aiproviders`, provider);
  return response.data;
}

export async function updateProvider(provider: AiProviderItem): Promise<AiProviderItem> {
  const response = await axiosInstance.put<AiProviderItem>(
    `${API_PREFIX}/aiproviders/${encodeURIComponent(provider.metadata.name)}`,
    provider,
  );
  return response.data;
}

export async function deleteProvider(name: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/aiproviders/${encodeURIComponent(name)}`);
}

export async function testProvider(name: string): Promise<AiProviderTestResult> {
  const response = await axiosInstance.post<AiProviderTestResult>(
    `${API_PREFIX}/aiproviders/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}

export async function createProviderAccount(
  account: AiProviderAccountItem & { spec: AiProviderAccountSpec & { apiKey?: string } },
): Promise<AiProviderAccountItem> {
  const response = await axiosInstance.post<AiProviderAccountItem>(
    `${API_PREFIX}/aiprovideraccounts`,
    account,
  );
  return response.data;
}

export async function patchProviderAccount(
  name: string,
  operations: JsonPatchOperation[],
): Promise<AiProviderAccountItem> {
  const response = await axiosInstance.patch<AiProviderAccountItem>(
    `${API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}`,
    operations,
    { headers: { "Content-Type": "application/json-patch+json" } },
  );
  return response.data;
}

export async function deleteProviderAccount(name: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}`);
}

export async function testProviderAccount(name: string): Promise<AiProviderTestResult> {
  const response = await axiosInstance.post<AiProviderTestResult>(
    `${API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}

export async function updateAccountQuota(
  name: string,
  quota: AiProviderAccountQuota,
  balance: AiProviderAccountBalance,
): Promise<AiProviderAccountItem> {
  const response = await axiosInstance.post<AiProviderAccountItem>(
    `${API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}/quota`,
    { quota, balance },
  );
  return response.data;
}

export async function createModel(model: AiModelItem): Promise<AiModelItem> {
  const response = await axiosInstance.post<AiModelItem>(`${API_PREFIX}/aimodels`, model);
  return response.data;
}

export async function updateModel(model: AiModelItem): Promise<AiModelItem> {
  const response = await axiosInstance.put<AiModelItem>(
    `${API_PREFIX}/aimodels/${encodeURIComponent(model.metadata.name)}`,
    model,
  );
  return response.data;
}

export async function deleteModel(name: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/aimodels/${encodeURIComponent(name)}`);
}

export async function testModel(name: string): Promise<AiProviderTestResult> {
  const response = await axiosInstance.post<AiProviderTestResult>(
    `${API_PREFIX}/aimodels/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}

export async function fetchProviderRemoteModels(name: string): Promise<AiRemoteModel[]> {
  const response = await axiosInstance.get<AiRemoteModelList>(
    `${API_PREFIX}/aiproviders/${encodeURIComponent(name)}/models`,
  );
  return response.data.data ?? [];
}

export function resolveAiProviderApiError(error: unknown): string {
  if (isAxiosError<{ detail?: string }>(error)) {
    const detail = error.response?.data?.detail;
    if (detail) {
      return detail;
    }
  }
  return resolveApiError(error, {
    notFound: "供应商资源不存在或已被删除。",
    conflict: "名称已存在，请更换后重试。",
  });
}
