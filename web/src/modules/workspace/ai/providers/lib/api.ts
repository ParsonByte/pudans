import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { resolveApiError } from "@/lib/api-error";

import type { ProviderPresetCatalog } from "./presets";

export const AI_API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

export const AI_API_VERSION = "pudans.tolim.cn/v1alpha1";

export const AI_PROVIDER_KIND = "AiProvider";

export const AI_PROVIDER_ACCOUNT_KIND = "AiProviderAccount";

export const AI_MODEL_KIND = "AiModel";

export const AI_ROUTE_GROUP_KIND = "AiRouteGroup";

const AI_SETTING_NAME = "ai-setting";

export type AiProviderVendor =
  "OPENAI" | "ANTHROPIC" | "GEMINI" | "DEEPSEEK" | "MINIMAX" | "QIANWEN" | "OLLAMA" | "CUSTOM";

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
  | "GEMINI_OPENAI"
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

export type AiModality = "TEXT" | "IMAGE" | "AUDIO" | "VIDEO" | "FILE" | "THREE_D";

export type AiModelTier = "CHEAP" | "MEDIUM" | "HIGH" | "ULTRA";

export type AiMetadata = {
  name: string;
  version?: number;
  creationTimestamp?: string;
  labels?: Record<string, string>;
  annotations?: Record<string, string>;
};

type AiProviderSpec = {
  displayName: string;
  vendor: AiProviderVendor;
  protocol?: AiProviderProtocol;
  baseUrl?: string;
  headers?: Record<string, string>;
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  maxConcurrency?: number;
  maxRPM?: number;
  routing?: AiProviderRouting;
  affinity?: AiProviderAffinity;
  fallback?: string[];
  order?: number;
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

export type AiProviderAccountBalanceProbe = {
  url?: string;
  path?: string;
  method?: string;
  token?: string;
  headerName?: string;
};

export type AiProviderAccountBalance = {
  unit: AiQuotaUnit;
  left?: number;
};

type AiProviderAccountSpec = {
  provider: string;
  displayName?: string;
  accountType: AiAccountType;
  weight: number;
  priority: number;
  enabled: boolean;
  keyName?: string;
  protocolOverride?: AiProviderProtocol;
  keyPlan?: string;
  spendCapPoints?: number;
  maxConcurrency?: number;
  maxRPM?: number;
  freePlan: boolean;
  creditsEnabled: boolean;
  balanceProbe?: AiProviderAccountBalanceProbe;
  concurrencyByModel?: Record<string, number>;
  quota?: AiProviderAccountQuota;
  windows?: AiProviderAccountQuotaWindow[];
  balance?: AiProviderAccountBalance;
  [key: string]: unknown;
};

type AiProviderAccountStatus = {
  healthScore?: number;
  health?: string;
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

export type AiProviderAccountInput = AiProviderAccountItem & {
  spec: AiProviderAccountSpec & { apiKey?: string };
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
  inputModalities?: AiModality[];
  outputModalities?: AiModality[];
  embeddingDim?: number;
  maxBatchSize?: number;
  maxInputImages?: number;
  maxInputSeconds?: number;
  maxOutputItems?: number;
  inputTokensPerPoint?: number;
  cacheTokensPerPoint?: number;
  outputTokensPerPoint?: number;
  imagePointsPerItem?: number;
  videoPointsPerSecond?: number;
  audioPointsPerSecond?: number;
  speechPointsPerKiloCharacter?: number;
  taskPointsPerInvocation?: number;
  enabled?: boolean;
  [key: string]: unknown;
};

export type AiModelItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiModelSpec;
  status?: AiProviderStatus;
};

export type AiRouteGroupMode =
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
  | "EXPIRING"
  | "MANUAL";

export type AiRouteGroupMember = {
  model: string;
  tierOverride?: AiModelTier;
  weight?: number;
  capabilityTags?: string[];
  extraContextMargin?: number;
  enabled?: boolean;
};

export type AiRouteGroupTier = AiModelTier | "AUTO";

export type AiRouteGroupParentMode = "FAST" | "PRO" | "NONE";

export type AiRouteGroupContextMode = "SMALLEST" | "LARGEST";

export type AiRouteGroupRule = {
  name?: string;
  condition?: string;
  member?: string;
  priority?: number;
  agents?: string[];
  tokensGte?: number;
  imagesGte?: number;
  pause?: boolean;
  use?: string;
};

type AiRouteGroupSpec = {
  displayName: string;
  description?: string;
  routeMode?: AiRouteGroupMode;
  defaultTier?: AiRouteGroupTier;
  parentMode?: AiRouteGroupParentMode;
  qualityThreshold?: number;
  affinity?: AiProviderAffinity;
  fallback?: string[];
  accounts?: string[];
  order?: number;
  patienceSeconds?: number;
  firstTokenSeconds?: number;
  sink?: boolean;
  contextMode?: AiRouteGroupContextMode;
  manualPick?: string;
  rules?: AiRouteGroupRule[];
  isDefault?: boolean;
  enabled?: boolean;
  members?: AiRouteGroupMember[];
};

export type AiRouteGroupItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiRouteGroupSpec;
};

type AiSettingRouting = {
  classifierEnabled?: boolean;
  classifierFallbackTier?: AiRouteGroupTier;
};

type AiSettingGateway = {
  injectionEnabled?: boolean;
  memoryBridgeEnabled?: boolean;
  streamIdleTimeoutSeconds?: number;
  rateLimitEnabled?: boolean;
  maxRetries?: number;
  retryBackoffMs?: number;
  retryOnStatuses?: number[];
  requestTimeoutSeconds?: number;
  supportedProtocols?: AiProviderProtocol[];
};

type AiSettingSpec = {
  defaultRouteGroup?: string;
  defaultChatMode?: string;
  routing?: AiSettingRouting;
  gateway?: AiSettingGateway;
  [key: string]: unknown;
};

export type AiSettingItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiSettingSpec;
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
  inputModalities?: AiModality[];
  outputModalities?: AiModality[];
};

type AiRemoteModelList = {
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

export async function listAll<T>(path: string, params: Record<string, unknown> = {}): Promise<T[]> {
  const items: T[] = [];
  for (let page = 1; ; page += 1) {
    const response = await axiosInstance.get<ListResponse<T>>(`${AI_API_PREFIX}${path}`, {
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
    `${AI_API_PREFIX}/aiproviders`,
    { params: { page, size } },
  );
  return toPageResult(response.data);
}

export async function fetchProvider(name: string): Promise<AiProviderItem> {
  const response = await axiosInstance.get<AiProviderItem>(
    `${AI_API_PREFIX}/aiproviders/${encodeURIComponent(name)}`,
  );
  return response.data;
}

export async function fetchProviderPresets(): Promise<ProviderPresetCatalog> {
  const response = await axiosInstance.get<ProviderPresetCatalog>(
    `${AI_API_PREFIX}/aiproviders/-/presets`,
  );
  return {
    supported: response.data.supported ?? [],
    unsupported: response.data.unsupported ?? [],
  };
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

export function fetchAllRouteGroups(): Promise<AiRouteGroupItem[]> {
  return listAll<AiRouteGroupItem>("/airoutegroups");
}

export async function createRouteGroup(group: AiRouteGroupItem): Promise<AiRouteGroupItem> {
  const response = await axiosInstance.post<AiRouteGroupItem>(
    `${AI_API_PREFIX}/airoutegroups`,
    group,
  );
  return response.data;
}

export async function updateRouteGroup(group: AiRouteGroupItem): Promise<AiRouteGroupItem> {
  const response = await axiosInstance.put<AiRouteGroupItem>(
    `${AI_API_PREFIX}/airoutegroups/${encodeURIComponent(group.metadata.name)}`,
    group,
  );
  return response.data;
}

export async function deleteRouteGroup(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/airoutegroups/${encodeURIComponent(name)}`);
}

export async function arrangeRouteGroups(names: string[]): Promise<AiRouteGroupItem[]> {
  const response = await axiosInstance.post<ListResponse<AiRouteGroupItem> | AiRouteGroupItem[]>(
    `${AI_API_PREFIX}/airoutegroups/-/arrange`,
    { names },
  );
  return Array.isArray(response.data) ? response.data : (response.data.items ?? []);
}

export async function copyRouteGroup(
  name: string,
  payload: { name: string; displayName?: string },
): Promise<AiRouteGroupItem> {
  const response = await axiosInstance.post<AiRouteGroupItem>(
    `${AI_API_PREFIX}/airoutegroups/${encodeURIComponent(name)}/actions/copy`,
    payload,
  );
  return response.data;
}

export async function fetchAiSetting(): Promise<AiSettingItem> {
  const response = await axiosInstance.get<AiSettingItem>(
    `${AI_API_PREFIX}/aisettings/${AI_SETTING_NAME}`,
  );
  return response.data;
}

export async function updateAiSetting(setting: AiSettingItem): Promise<AiSettingItem> {
  const response = await axiosInstance.put<AiSettingItem>(
    `${AI_API_PREFIX}/aisettings/${AI_SETTING_NAME}`,
    setting,
  );
  return response.data;
}

export async function createProvider(provider: AiProviderItem): Promise<AiProviderItem> {
  const response = await axiosInstance.post<AiProviderItem>(
    `${AI_API_PREFIX}/aiproviders`,
    provider,
  );
  return response.data;
}

export async function updateProvider(provider: AiProviderItem): Promise<AiProviderItem> {
  const response = await axiosInstance.put<AiProviderItem>(
    `${AI_API_PREFIX}/aiproviders/${encodeURIComponent(provider.metadata.name)}`,
    provider,
  );
  return response.data;
}

export async function deleteProvider(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/aiproviders/${encodeURIComponent(name)}`);
}

export async function testProvider(name: string): Promise<AiProviderTestResult> {
  const response = await axiosInstance.post<AiProviderTestResult>(
    `${AI_API_PREFIX}/aiproviders/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}

export async function arrangeProviders(names: string[]): Promise<AiProviderItem[]> {
  const response = await axiosInstance.post<ListResponse<AiProviderItem> | AiProviderItem[]>(
    `${AI_API_PREFIX}/aiproviders/-/arrange`,
    { names },
  );
  return Array.isArray(response.data) ? response.data : (response.data.items ?? []);
}

export async function duplicateProvider(
  name: string,
  payload: { name: string; displayName?: string },
): Promise<AiProviderItem> {
  const response = await axiosInstance.post<AiProviderItem>(
    `${AI_API_PREFIX}/aiproviders/${encodeURIComponent(name)}/actions/duplicate`,
    payload,
  );
  return response.data;
}

export async function createProviderAccount(
  account: AiProviderAccountInput,
): Promise<AiProviderAccountItem> {
  const response = await axiosInstance.post<AiProviderAccountItem>(
    `${AI_API_PREFIX}/aiprovideraccounts`,
    account,
  );
  return response.data;
}

export async function patchProviderAccount(
  name: string,
  operations: JsonPatchOperation[],
): Promise<AiProviderAccountItem> {
  const response = await axiosInstance.patch<AiProviderAccountItem>(
    `${AI_API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}`,
    operations,
    { headers: { "Content-Type": "application/json-patch+json" } },
  );
  return response.data;
}

export async function deleteProviderAccount(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}`);
}

export async function testProviderAccount(name: string): Promise<AiProviderTestResult> {
  const response = await axiosInstance.post<AiProviderTestResult>(
    `${AI_API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}

export async function updateAccountQuota(
  name: string,
  quota: AiProviderAccountQuota,
  balance: AiProviderAccountBalance,
): Promise<AiProviderAccountItem> {
  const response = await axiosInstance.post<AiProviderAccountItem>(
    `${AI_API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}/quota`,
    { quota, balance },
  );
  return response.data;
}

export async function createModel(model: AiModelItem): Promise<AiModelItem> {
  const response = await axiosInstance.post<AiModelItem>(`${AI_API_PREFIX}/aimodels`, model);
  return response.data;
}

export async function updateModel(model: AiModelItem): Promise<AiModelItem> {
  const response = await axiosInstance.put<AiModelItem>(
    `${AI_API_PREFIX}/aimodels/${encodeURIComponent(model.metadata.name)}`,
    model,
  );
  return response.data;
}

export async function deleteModel(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/aimodels/${encodeURIComponent(name)}`);
}

export async function testModel(name: string): Promise<AiProviderTestResult> {
  const response = await axiosInstance.post<AiProviderTestResult>(
    `${AI_API_PREFIX}/aimodels/${encodeURIComponent(name)}/test`,
  );
  return response.data;
}

export async function fetchProviderRemoteModels(name: string): Promise<AiRemoteModel[]> {
  const encoded = encodeURIComponent(name);
  const response = await axiosInstance.get<AiRemoteModelList>(
    `${AI_API_PREFIX}/aiproviders/${encoded}/models`,
  );
  return response.data.data ?? [];
}

type AiProviderBalanceInfo = {
  currency?: string;
  totalBalance?: string;
  grantedBalance?: string;
  toppedUpBalance?: string;
};

export type AiProviderBalanceResult = {
  available?: boolean;
  balanceInfos?: AiProviderBalanceInfo[];
};

export async function fetchProviderAccountBalance(name: string): Promise<AiProviderBalanceResult> {
  const response = await axiosInstance.get<AiProviderBalanceResult>(
    `${AI_API_PREFIX}/aiprovideraccounts/${encodeURIComponent(name)}/balance`,
  );
  return response.data;
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
