import { useMemo } from "react";

import { apiClient } from "@pudans-dev/api-client";
import {
  type QueryClient,
  type UseQueryResult,
  useInfiniteQuery,
  useQuery,
} from "@tanstack/react-query";

import { useAgentsQuery } from "@/modules/workspace/ai/library/lib/agents";
import type { AiAgentItem } from "@/modules/workspace/ai/library/lib/api";
import { fetchAiSetting } from "@/modules/workspace/ai/providers/lib/api";

import {
  type MemoryAssetListQuery,
  type MemoryAssetView,
  type MemoryCursor,
  type MemoryGenerationLogItem,
  type MemoryGenerationLogQuery,
  type MemoryLayer,
  type MemoryLayerCounts,
  type MemoryPipelineStats,
  type MemoryPromptBindingItem,
  type MemoryPromptItem,
  type MemoryPromptLayer,
  type MemoryPromptLogQuery,
  type MemoryPromptTargetType,
  type MemoryRecordItem,
  type MemoryRecordQuery,
  type MemoryRequestItem,
  type MemoryRequestQuery,
  type MemorySettingItem,
  type OrgMemoryItem,
  type OrgMemoryQuery,
  type ResolvedMemoryPrompt,
  fetchMemoryAsset,
  fetchMemoryAssets,
  fetchMemoryCounts,
  fetchMemoryGenerationLog,
  fetchMemoryGenerationLogs,
  fetchMemoryLayer,
  fetchMemoryPipeline,
  fetchMemoryProfile,
  fetchMemoryPrompt,
  fetchMemoryPromptBindings,
  fetchMemoryPromptLogs,
  fetchMemoryPromptSetting,
  fetchMemoryPrompts,
  fetchMemoryRecord,
  fetchMemoryRecords,
  fetchMemoryRequests,
  fetchMemorySceneBody,
  fetchOrgMemories,
  fetchPendingOrgMemories,
} from "./api";

const MEMORY_ASSETS_QUERY_KEY = ["ai-memory-assets"] as const;

const MEMORY_COUNTS_QUERY_PREFIX = "ai-memory-counts";

const MEMORY_LAYER_QUERY_PREFIX = "ai-memory-layer";

const MEMORY_SCENE_BODY_QUERY_KEY = "ai-memory-scene-body";

const MEMORY_PROFILE_QUERY_KEY = "ai-memory-profile";

const MEMORY_GENERATION_LOGS_QUERY_KEY = "ai-memory-generation-logs";

const MEMORY_GENERATION_LOG_QUERY_KEY = "ai-memory-generation-log";

const MEMORY_PROMPTS_QUERY_KEY = "ai-memory-prompts";

const MEMORY_PROMPT_SETTING_QUERY_KEY = "ai-memory-prompt-setting";

const MEMORY_PROMPT_LOGS_QUERY_KEY = "ai-memory-prompt-logs";

const MEMORY_ASSET_DETAIL_QUERY_KEY = "ai-memory-asset-detail";

const MEMORY_PROMPT_DETAIL_QUERY_KEY = "ai-memory-prompt-detail";

const MEMORY_PROMPT_BINDINGS_QUERY_KEY = "ai-memory-prompt-bindings";

const MEMORY_RECORDS_QUERY_KEY = "ai-memory-records";

const MEMORY_RECORD_QUERY_KEY = "ai-memory-record";

const MEMORY_PIPELINE_QUERY_KEY = "ai-memory-pipeline";

const MEMORY_REQUESTS_QUERY_KEY = "ai-memory-requests";

const ORG_MEMORIES_QUERY_KEY = "ai-org-memories";

const ACCOUNT_PROFILE_QUERY_KEY = "account-profile";

const AI_SETTING_QUERY_KEY = ["ai-settings", "ai-setting"] as const;

export function invalidateMemoryAssetQueries(
  queryClient: QueryClient,
  assetName: string,
): Promise<void> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: MEMORY_ASSETS_QUERY_KEY }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_ASSET_DETAIL_QUERY_KEY, assetName] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_COUNTS_QUERY_PREFIX, assetName] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_LAYER_QUERY_PREFIX, assetName] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_PROFILE_QUERY_KEY, assetName] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_GENERATION_LOGS_QUERY_KEY, assetName] }),
  ]).then(() => undefined);
}

export function invalidateMemoryPromptQueries(queryClient: QueryClient): Promise<void> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: [MEMORY_PROMPTS_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_PROMPT_DETAIL_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_PROMPT_BINDINGS_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_PROMPT_SETTING_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_PROMPT_LOGS_QUERY_KEY] }),
  ]).then(() => undefined);
}

export function invalidateMemoryPromptBindingQueries(queryClient: QueryClient): Promise<void> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: [MEMORY_PROMPT_BINDINGS_QUERY_KEY] }),
  ]).then(() => undefined);
}

export function invalidateMemoryReviewQueries(queryClient: QueryClient): Promise<void> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: [MEMORY_RECORDS_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_RECORD_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_PIPELINE_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [MEMORY_REQUESTS_QUERY_KEY] }),
    queryClient.invalidateQueries({ queryKey: [ORG_MEMORIES_QUERY_KEY] }),
  ]).then(() => undefined);
}

export type MemoryLayerFilters = {
  from?: string;
  to?: string;
  q?: string;
  limit: number;
};

export function useMemoryAssets(query: MemoryAssetListQuery, enabled: boolean) {
  return useQuery({
    queryKey: [...MEMORY_ASSETS_QUERY_KEY, query],
    queryFn: () => fetchMemoryAssets(query),
    enabled,
    retry: false,
  });
}

export function useMemoryCounts(name: string | undefined, team: string, enabled: boolean) {
  return useQuery<MemoryLayerCounts>({
    queryKey: [MEMORY_COUNTS_QUERY_PREFIX, name ?? "", team],
    queryFn: () => fetchMemoryCounts(name as string, team),
    enabled: enabled && Boolean(name),
    retry: false,
  });
}

export function useMemoryLayerPage<T>(
  assetName: string | undefined,
  team: string,
  layer: MemoryLayer,
  filters: MemoryLayerFilters,
  enabled = true,
) {
  return useInfiniteQuery({
    queryKey: [MEMORY_LAYER_QUERY_PREFIX, assetName ?? "", team, layer, filters],
    queryFn: ({ pageParam }) =>
      fetchMemoryLayer<T>(assetName as string, team, layer, {
        ...filters,
        cursor: pageParam ?? undefined,
      }),
    initialPageParam: null as MemoryCursor | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? null,
    enabled: enabled && Boolean(assetName),
    retry: false,
  });
}

export function useMemorySceneBody(
  assetName: string,
  team: string,
  path: string,
  enabled: boolean,
) {
  return useQuery({
    queryKey: [MEMORY_SCENE_BODY_QUERY_KEY, assetName, team, path],
    queryFn: () => fetchMemorySceneBody(assetName, team, path),
    enabled,
    retry: false,
  });
}

export function useMemoryProfile(assetName: string | undefined, team: string, enabled: boolean) {
  return useQuery({
    queryKey: [MEMORY_PROFILE_QUERY_KEY, assetName ?? "", team],
    queryFn: () => fetchMemoryProfile(assetName as string, team),
    enabled: enabled && Boolean(assetName),
    retry: false,
  });
}

export function useMemoryGenerationLogs(
  assetName: string | undefined,
  team: string,
  query: MemoryGenerationLogQuery,
  enabled: boolean,
) {
  return useInfiniteQuery({
    queryKey: [MEMORY_GENERATION_LOGS_QUERY_KEY, assetName ?? "", team, query],
    queryFn: ({ pageParam }) =>
      fetchMemoryGenerationLogs(assetName as string, team, {
        ...query,
        beforeId: typeof pageParam === "number" ? pageParam : undefined,
      }),
    initialPageParam: null as number | null,
    getNextPageParam: (lastPage) => {
      if (typeof lastPage.nextCursor === "number") {
        return lastPage.nextCursor;
      }
      if (lastPage.items.length < query.limit) {
        return null;
      }
      const oldest = lastPage.items.reduce(
        (min, item) => (item.id < min ? item.id : min),
        Number.MAX_SAFE_INTEGER,
      );
      return Number.isFinite(oldest) ? oldest : null;
    },
    enabled: enabled && Boolean(assetName),
    retry: false,
  });
}

export function useMemoryGenerationLog(id: number | null) {
  return useQuery<MemoryGenerationLogItem>({
    queryKey: [MEMORY_GENERATION_LOG_QUERY_KEY, id ?? 0],
    queryFn: () => fetchMemoryGenerationLog(id as number),
    enabled: id !== null,
    retry: false,
  });
}

export function useMemoryPrompts() {
  return useQuery<MemoryPromptItem[]>({
    queryKey: [MEMORY_PROMPTS_QUERY_KEY],
    queryFn: fetchMemoryPrompts,
    retry: false,
  });
}

export function useMemoryAssetDetail(name: string | undefined, team: string, enabled: boolean) {
  return useQuery<MemoryAssetView>({
    queryKey: [MEMORY_ASSET_DETAIL_QUERY_KEY, name ?? "", team],
    queryFn: () => fetchMemoryAsset(name as string, team),
    enabled: enabled && Boolean(name),
    retry: false,
  });
}

export function useMemoryPromptDetail(name: string | undefined, enabled: boolean) {
  return useQuery<MemoryPromptItem>({
    queryKey: [MEMORY_PROMPT_DETAIL_QUERY_KEY, name ?? ""],
    queryFn: () => fetchMemoryPrompt(name as string),
    enabled: enabled && Boolean(name),
    retry: false,
  });
}

export function useMemoryPromptBindings() {
  return useQuery<MemoryPromptBindingItem[]>({
    queryKey: [MEMORY_PROMPT_BINDINGS_QUERY_KEY],
    queryFn: fetchMemoryPromptBindings,
    retry: false,
  });
}

export function useMemoryRecords(query: MemoryRecordQuery, enabled: boolean) {
  return useQuery<MemoryRecordItem[]>({
    queryKey: [MEMORY_RECORDS_QUERY_KEY, query],
    queryFn: () => fetchMemoryRecords(query),
    enabled,
    retry: false,
  });
}

export function useMemoryRecord(name: string | undefined, enabled: boolean) {
  return useQuery<MemoryRecordItem>({
    queryKey: [MEMORY_RECORD_QUERY_KEY, name ?? ""],
    queryFn: () => fetchMemoryRecord(name as string),
    enabled: enabled && Boolean(name),
    retry: false,
  });
}

export function useMemoryPipeline(enabled: boolean) {
  return useQuery<MemoryPipelineStats>({
    queryKey: [MEMORY_PIPELINE_QUERY_KEY],
    queryFn: fetchMemoryPipeline,
    enabled,
    retry: false,
  });
}

export function useMemoryRequests(query: MemoryRequestQuery, enabled: boolean) {
  return useQuery<MemoryRequestItem[]>({
    queryKey: [MEMORY_REQUESTS_QUERY_KEY, query],
    queryFn: () => fetchMemoryRequests(query),
    enabled,
    retry: false,
  });
}

export function useOrgMemories(
  trust: "pending" | "verified",
  query: OrgMemoryQuery,
  enabled: boolean,
) {
  return useQuery<OrgMemoryItem[]>({
    queryKey: [ORG_MEMORIES_QUERY_KEY, trust, query],
    queryFn: () => (trust === "pending" ? fetchPendingOrgMemories(query) : fetchOrgMemories(query)),
    enabled,
    retry: false,
  });
}

export function useMemoryPromptSetting(
  query: { layer: MemoryPromptLayer; team?: string; agent?: string },
  enabled: boolean,
) {
  return useQuery<ResolvedMemoryPrompt>({
    queryKey: [MEMORY_PROMPT_SETTING_QUERY_KEY, query],
    queryFn: () => fetchMemoryPromptSetting(query),
    enabled,
    retry: false,
  });
}

export function useMemoryPromptLogs(query: MemoryPromptLogQuery, enabled: boolean) {
  return useQuery({
    queryKey: [MEMORY_PROMPT_LOGS_QUERY_KEY, query],
    queryFn: () => fetchMemoryPromptLogs(query),
    enabled,
    retry: false,
  });
}

export function useCurrentUser() {
  return useQuery({
    queryKey: [ACCOUNT_PROFILE_QUERY_KEY],
    queryFn: async () => (await apiClient.user.getCurrentUser()).data,
    retry: false,
  });
}

export function useOwnedAgents(currentUserId: string | undefined): {
  agents: AiAgentItem[];
  query: UseQueryResult<AiAgentItem[]>;
} {
  const query = useAgentsQuery();
  const agents = useMemo(
    () =>
      (query.data ?? []).filter(
        (agent) => Boolean(currentUserId) && agent.spec.ownerUserId === currentUserId,
      ),
    [query.data, currentUserId],
  );
  return { agents, query };
}

export function useMemorySetting() {
  return useQuery<MemorySettingItem>({
    queryKey: AI_SETTING_QUERY_KEY,
    queryFn: async () => (await fetchAiSetting()) as unknown as MemorySettingItem,
    retry: false,
  });
}

export function promptSettingTarget(
  targetType: MemoryPromptTargetType,
  team: string,
  agent: string,
): { team?: string; agent?: string } {
  return {
    team: targetType === "INSTANCE" ? undefined : team,
    agent: targetType === "AGENT" ? agent : undefined,
  };
}
