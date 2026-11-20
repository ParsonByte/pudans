import { axiosInstance } from "@pudans-dev/api-client";

import { resolveApiErrorWithDetail } from "@/lib/api-error";
import {
  AI_API_PREFIX,
  AI_API_VERSION,
  type AiMetadata,
  listAll,
} from "@/modules/workspace/ai/providers/lib/api";

export { AI_API_PREFIX, AI_API_VERSION, type AiMetadata };

export const AI_AGENT_KIND = "AiAgent";

type AiAgentVisibility = "PRIVATE" | "TEAM" | "RESTRICTED";

type AiAgentStatus = "ACTIVE" | "INACTIVE";

export type AiAgentSpec = {
  team: string;
  ownerUserId: string;
  displayName: string;
  description?: string;
  visibility?: AiAgentVisibility;
  status?: AiAgentStatus;
};

export type AiAgentItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiAgentSpec;
};

export function fetchAllAgents(): Promise<AiAgentItem[]> {
  return listAll<AiAgentItem>("/aiagents");
}

export function listLibraryResources<T>(plural: string): Promise<T[]> {
  return listAll<T>(`/${plural}`);
}

export async function createLibraryResource<T>(plural: string, item: unknown): Promise<T> {
  const response = await axiosInstance.post<T>(`${AI_API_PREFIX}/${plural}`, item);
  return response.data;
}

export async function updateLibraryResource<T extends { metadata: { name: string } }>(
  plural: string,
  item: T,
): Promise<T> {
  const response = await axiosInstance.put<T>(
    `${AI_API_PREFIX}/${plural}/${encodeURIComponent(item.metadata.name)}`,
    item,
  );
  return response.data;
}

export async function deleteLibraryResource(plural: string, name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/${plural}/${encodeURIComponent(name)}`);
}

export function resolveLibraryApiError(error: unknown, resourceLabel: string): string {
  return resolveApiErrorWithDetail(error, {
    notFound: `${resourceLabel}不存在或已被删除。`,
    conflict: "名称已存在，请更换后重试。",
  });
}
