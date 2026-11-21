import { axiosInstance } from "@pudans-dev/api-client";

import {
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  updateLibraryResource,
} from "@/modules/workspace/ai/library/lib/api";
import {
  AI_API_PREFIX,
  type AiMetadata,
  type JsonPatchOperation,
} from "@/modules/workspace/ai/providers/lib/api";

export const AI_TEAM_KIND = "AiTeam";

export const AI_TEAMS_QUERY_KEY = ["ai-teams", "all"] as const;

export function aiTeamQueryKey(name: string) {
  return ["ai-teams", name] as const;
}

export type AiTeamStatus = "ACTIVE" | "ARCHIVED";

export type AiTeamRole = "ADMIN" | "MEMBER" | "REVIEWER";

export type AiTeamMemberStatus = "ACTIVE" | "REMOVED";

export type AiTeamQuota = {
  limit?: number;
  resetsAt?: string;
  windowDays?: number;
};

export type AiTeamMember = {
  userId: string;
  role?: AiTeamRole;
  status?: AiTeamMemberStatus;
};

export type AiTeamSpec = {
  displayName: string;
  description?: string;
  ownerUserId: string;
  status?: AiTeamStatus;
  defaultAgent?: string;
  members?: AiTeamMember[];
  quota?: AiTeamQuota;
  metadata?: Record<string, unknown>;
};

export type AiTeamItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiTeamSpec;
};

type AiTeamMemberInput = {
  userId: string;
  role?: AiTeamRole;
};

export function fetchAiTeams(): Promise<AiTeamItem[]> {
  return listLibraryResources<AiTeamItem>("aiteams");
}

export async function fetchAiTeam(name: string): Promise<AiTeamItem> {
  const response = await axiosInstance.get<AiTeamItem>(
    `${AI_API_PREFIX}/aiteams/${encodeURIComponent(name)}`,
  );
  return response.data;
}

export function createAiTeam(team: AiTeamItem): Promise<AiTeamItem> {
  return createLibraryResource<AiTeamItem>("aiteams", team);
}

export function updateAiTeam(team: AiTeamItem): Promise<AiTeamItem> {
  return updateLibraryResource<AiTeamItem>("aiteams", team);
}

export function deleteAiTeam(name: string): Promise<void> {
  return deleteLibraryResource("aiteams", name);
}

export async function patchAiTeam(
  name: string,
  operations: JsonPatchOperation[],
): Promise<AiTeamItem> {
  const response = await axiosInstance.patch<AiTeamItem>(
    `${AI_API_PREFIX}/aiteams/${encodeURIComponent(name)}`,
    operations,
    { headers: { "Content-Type": "application/json-patch+json" } },
  );
  return response.data;
}

export async function addAiTeamMember(
  name: string,
  member: AiTeamMemberInput,
): Promise<AiTeamItem> {
  const response = await axiosInstance.post<AiTeamItem>(
    `${AI_API_PREFIX}/aiteams/${encodeURIComponent(name)}/members`,
    member,
  );
  return response.data;
}

export async function removeAiTeamMember(name: string, userId: string): Promise<AiTeamItem> {
  const response = await axiosInstance.delete<AiTeamItem>(
    `${AI_API_PREFIX}/aiteams/${encodeURIComponent(name)}/members/${encodeURIComponent(userId)}`,
  );
  return response.data;
}
