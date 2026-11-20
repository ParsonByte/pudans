import {
  AI_API_VERSION,
  type AiMetadata,
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  updateLibraryResource,
} from "../../lib/api";

export { AI_API_VERSION };

export const AI_HOOK_KIND = "AiHook";

const HOOKS_PLURAL = "aihooks";

export const HOOKS_QUERY_KEY = ["ai-hooks"] as const;

export const HOOK_EVENTS = [
  "SESSION_START",
  "USER_PROMPT_SUBMIT",
  "PRE_TOOL_USE",
  "POST_TOOL_USE",
  "SESSION_END",
] as const;

export type HookEvent = (typeof HOOK_EVENTS)[number];

export type HookSpec = {
  displayName: string;
  description?: string;
  event: HookEvent;
  matcher?: string;
  command: string;
  timeoutSeconds: number;
  enabled?: boolean;
  agents?: string[];
};

export type HookItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: HookSpec;
};

export type HookDraftPayload = {
  apiVersion: string;
  kind: string;
  metadata: { generateName: string };
  spec: HookSpec;
};

export function fetchHooks(): Promise<HookItem[]> {
  return listLibraryResources<HookItem>(HOOKS_PLURAL);
}

export function createHook(draft: HookDraftPayload): Promise<HookItem> {
  return createLibraryResource<HookItem>(HOOKS_PLURAL, draft);
}

export function updateHook(item: HookItem): Promise<HookItem> {
  return updateLibraryResource(HOOKS_PLURAL, item);
}

export function deleteHook(name: string): Promise<void> {
  return deleteLibraryResource(HOOKS_PLURAL, name);
}
