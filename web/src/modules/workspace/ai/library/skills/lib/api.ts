import { axiosInstance } from "@pudans-dev/api-client";

import {
  AI_API_PREFIX,
  AI_API_VERSION,
  type AiMetadata,
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  updateLibraryResource,
} from "../../lib/api";

export { AI_API_VERSION };

export const AI_SKILL_KIND = "AiSkill";

const SKILLS_PLURAL = "aiskills";

export const SKILLS_QUERY_KEY = ["ai-skills"] as const;

export const SKILL_SOURCES = ["INLINE", "URL", "GIT"] as const;

export type SkillSource = (typeof SKILL_SOURCES)[number];

export type SkillSpec = {
  displayName: string;
  description?: string;
  source: SkillSource;
  url?: string;
  ref?: string;
  path?: string;
  content?: string;
  enabled?: boolean;
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

export function deleteSkill(name: string): Promise<void> {
  return deleteLibraryResource(SKILLS_PLURAL, name);
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
