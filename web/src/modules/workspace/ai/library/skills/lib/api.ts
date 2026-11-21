import { axiosInstance } from "@pudans-dev/api-client";

import {
  AI_API_PREFIX,
  AI_API_VERSION,
  type AiMetadata,
  type JsonPatchOperation,
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  patchLibraryResource,
  updateLibraryResource,
} from "../../lib/api";

export { AI_API_VERSION };

export const AI_SKILL_KIND = "AiSkill";

const SKILLS_PLURAL = "aiskills";

export const SKILLS_QUERY_KEY = ["ai-skills"] as const;

export const SKILL_USAGE_QUERY_KEY = ["ai-skill-usage"] as const;

export const SKILL_AUDIT_QUERY_KEY = ["ai-skill-audit"] as const;

export const SKILL_SOURCES = ["INLINE", "URL", "GIT"] as const;

export type SkillSource = (typeof SKILL_SOURCES)[number];

export type SkillStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export type SkillSpec = {
  displayName: string;
  description?: string;
  source: SkillSource;
  url?: string;
  ref?: string;
  path?: string;
  content?: string;
  enabled?: boolean;
  status?: SkillStatus;
  agents?: string[];
};

export type SkillItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: SkillSpec;
};

export type SkillDraft = {
  apiVersion?: string;
  kind?: string;
  metadata?: AiMetadata | null;
  spec: SkillSpec;
};

export type SkillDraftPayload = {
  apiVersion: string;
  kind: string;
  metadata: { generateName: string };
  spec: SkillSpec;
};

export function fetchSkills(): Promise<SkillItem[]> {
  return listLibraryResources<SkillItem>(SKILLS_PLURAL);
}

export function createSkill(draft: SkillDraftPayload): Promise<SkillItem> {
  return createLibraryResource<SkillItem>(SKILLS_PLURAL, draft);
}

export function updateSkill(item: SkillItem): Promise<SkillItem> {
  return updateLibraryResource(SKILLS_PLURAL, item);
}

export function patchSkill(name: string, operations: JsonPatchOperation[]): Promise<SkillItem> {
  return patchLibraryResource<SkillItem>(SKILLS_PLURAL, name, operations);
}

export function deleteSkill(name: string): Promise<void> {
  return deleteLibraryResource(SKILLS_PLURAL, name);
}

export type SkillUsageRecord = {
  createdBy?: string;
  useCount: number;
  viewCount: number;
  patchCount: number;
  createdAt: string;
  lastActivityAt?: string;
  state: "ACTIVE" | "ARCHIVED";
  promotedBy?: string;
  environments: string[];
};

export type SkillAuditEntry = {
  at: string;
  actor: string;
  operation: string;
  target: string;
  decision: string;
  detail?: string;
};

export type SkillCuratorResult = {
  status: "APPROVED" | "ARCHIVED" | "INVALID";
  name: string;
  message: string;
};

export async function fetchSkillUsage(): Promise<SkillUsageRecord[]> {
  const response = await axiosInstance.get<SkillUsageRecord[]>(
    `${AI_API_PREFIX}/${SKILLS_PLURAL}/-/usage`,
  );
  return Array.isArray(response.data) ? response.data : [];
}

export async function fetchSkillAudit(limit = 100): Promise<SkillAuditEntry[]> {
  const response = await axiosInstance.get<SkillAuditEntry[]>(
    `${AI_API_PREFIX}/${SKILLS_PLURAL}/-/audit`,
    { params: { limit } },
  );
  return Array.isArray(response.data) ? response.data : [];
}

export async function promoteSkill(name: string): Promise<SkillCuratorResult> {
  const response = await axiosInstance.post<SkillCuratorResult>(
    `${AI_API_PREFIX}/${SKILLS_PLURAL}/${encodeURIComponent(name)}/actions/promote`,
  );
  return response.data;
}

export async function archiveSkill(name: string): Promise<SkillCuratorResult> {
  const response = await axiosInstance.post<SkillCuratorResult>(
    `${AI_API_PREFIX}/${SKILLS_PLURAL}/${encodeURIComponent(name)}/actions/archive`,
  );
  return response.data;
}

export async function importSkillFromUrl(url: string, ref: string): Promise<SkillDraft> {
  const response = await axiosInstance.post<SkillDraft>(
    `${AI_API_PREFIX}/${SKILLS_PLURAL}/-/import`,
    {
      url,
      ref: ref || undefined,
    },
  );
  return response.data;
}
