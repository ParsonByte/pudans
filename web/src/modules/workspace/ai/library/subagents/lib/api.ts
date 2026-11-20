import {
  AI_API_VERSION,
  type AiMetadata,
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  updateLibraryResource,
} from "../../lib/api";

export { AI_API_VERSION };

export const AI_SUBAGENT_KIND = "AiSubagent";

const SUBAGENTS_PLURAL = "aisubagents";

export const SUBAGENTS_QUERY_KEY = ["ai-subagents"] as const;

export type SubagentSpec = {
  displayName: string;
  description?: string;
  prompt: string;
  routeGroup?: string;
  model?: string;
  skills?: string[];
  mcpServers?: string[];
  enabled?: boolean;
  agents?: string[];
};

export type SubagentItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: SubagentSpec;
};

export type SubagentDraftPayload = {
  apiVersion: string;
  kind: string;
  metadata: { generateName: string };
  spec: SubagentSpec;
};

export function fetchSubagents(): Promise<SubagentItem[]> {
  return listLibraryResources<SubagentItem>(SUBAGENTS_PLURAL);
}

export function createSubagent(draft: SubagentDraftPayload): Promise<SubagentItem> {
  return createLibraryResource<SubagentItem>(SUBAGENTS_PLURAL, draft);
}

export function updateSubagent(item: SubagentItem): Promise<SubagentItem> {
  return updateLibraryResource(SUBAGENTS_PLURAL, item);
}

export function deleteSubagent(name: string): Promise<void> {
  return deleteLibraryResource(SUBAGENTS_PLURAL, name);
}
