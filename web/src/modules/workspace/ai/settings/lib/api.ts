import {
  type AiMetadata,
  type AiModelTier,
  type AiProviderProtocol,
  type AiSettingItem,
  fetchAiSetting,
  updateAiSetting,
} from "@/modules/workspace/ai/providers/lib/api";

export const AI_SETTING_QUERY_KEY = ["ai-settings", "ai-setting"] as const;

export type AiSettingTier = AiModelTier | "AUTO";

type AiSettingDefaults = {
  team?: string;
  agent?: string;
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

type AiSettingEmbedding = {
  dimension?: number;
  memoryModel?: string;
  knowledgeBaseModel?: string;
  queryModel?: string;
  batchSize?: number;
};

type AiSettingRetrieval = {
  strategy?: string;
  rrfK?: number;
  l1Limit?: number;
  l2Limit?: number;
  orgLimit?: number;
  scoreThreshold?: number;
  agenticEnabled?: boolean;
};

type AiSettingPrompt = {
  maxInputTokens?: number;
  maxOutputTokens?: number;
  historyWindowTurns?: number;
  knowledgeBaseCharBudget?: number;
};

type AiSettingRouting = {
  classifierEnabled?: boolean;
  classifierFallbackTier?: AiSettingTier;
};

type AiSettingMemory = {
  enabled?: boolean;
  extractionEnabled?: boolean;
  extractionRouteGroup?: string;
  maxMessagesPerExtraction?: number;
  maxBackgroundMessages?: number;
  maxMemoriesPerSession?: number;
  dedupEnabled?: boolean;
  conflictRecallTopK?: number;
  memoryLimit?: number;
  creditLimit?: number;
  promptEnabled?: boolean;
  generationLogEnabled?: boolean;
  l2Enabled?: boolean;
  l3Enabled?: boolean;
  jevMinConfidence?: number;
  l3RefreshHours?: number;
  decayEnabled?: boolean;
  decayMinScore?: number;
  decayAccessBoost?: number;
  decayDailyAmount?: number;
  decayStaleDays?: number;
  deleteRetentionDays?: number;
  requestMinReasonLength?: number;
  orgPromotionThreshold?: number;
  requireDoubleApprovalForL0?: boolean;
  excludeSessionPrefixes?: string[];
};

export type AiSettingSpec = {
  defaultRouteGroup?: string;
  defaultChatMode?: string;
  defaultKnowledgeBaseCodes?: string[];
  defaults?: AiSettingDefaults;
  gateway?: AiSettingGateway;
  embedding?: AiSettingEmbedding;
  retrieval?: AiSettingRetrieval;
  prompt?: AiSettingPrompt;
  routing?: AiSettingRouting;
  memory?: AiSettingMemory;
  billing?: Record<string, unknown>;
};

export type AiSettingRecord = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiSettingSpec;
};

export async function fetchAiSettingRecord(): Promise<AiSettingRecord> {
  return (await fetchAiSetting()) as unknown as AiSettingRecord;
}

export async function saveAiSetting(setting: AiSettingRecord): Promise<AiSettingRecord> {
  return (await updateAiSetting(setting as unknown as AiSettingItem)) as unknown as AiSettingRecord;
}
