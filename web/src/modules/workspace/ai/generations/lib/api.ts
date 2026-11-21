import { axiosInstance } from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";
import { AI_API_PREFIX } from "@/modules/workspace/ai/providers/lib/api";

const GENERATIONS_API = `${AI_API_PREFIX}/aigenerations`;

export const GENERATION_LIST_LIMIT = 100;

export const AI_GENERATIONS_QUERY_KEY = ["ai-generations", "list"] as const;

export function aiGenerationDetailQueryKey(requestId: string) {
  return ["ai-generations", "detail", requestId] as const;
}

export type AiGenerationOutput = {
  type: string;
  text?: string | null;
  url?: string | null;
  data?: string | null;
  mediaType?: string | null;
  detail?: string | null;
  fileId?: string | null;
  filename?: string | null;
  durationSeconds?: number | null;
  format?: string | null;
  model?: string | null;
  dimensions?: number | null;
  values?: number[] | null;
  value?: unknown;
};

export type AiGenerationUsage = {
  inputTokens?: number | null;
  outputTokens?: number | null;
  cacheReadTokens?: number | null;
  cacheWriteTokens?: number | null;
  imageCount?: number | null;
  videoSeconds?: number | null;
  audioSeconds?: number | null;
  speechCharacters?: number | null;
  taskCount?: number | null;
  extra?: Record<string, unknown>;
};

export type AiGenerationItem = {
  requestId: string;
  status: string;
  modelType: string;
  capability: string;
  provider?: string | null;
  account?: string | null;
  model?: string | null;
  outputs: AiGenerationOutput[];
  usage: AiGenerationUsage;
  units?: number | null;
  billingSource?: string | null;
  billingStatus: string;
  providerTaskId?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  latencyMs?: number | null;
  createdAt: string;
  completedAt?: string | null;
};

export async function fetchAiGenerations(
  limit = GENERATION_LIST_LIMIT,
): Promise<AiGenerationItem[]> {
  const response = await axiosInstance.get<AiGenerationItem[]>(GENERATIONS_API, {
    params: { limit },
  });
  return response.data ?? [];
}

export async function fetchAiGeneration(requestId: string): Promise<AiGenerationItem> {
  const response = await axiosInstance.get<AiGenerationItem>(
    `${GENERATIONS_API}/${encodeURIComponent(requestId)}`,
  );
  return response.data;
}

export function resolveAiGenerationApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "生成记录不存在或已被删除。",
    conflict: "当前生成记录状态已变化，请刷新后重试。",
  });
}
