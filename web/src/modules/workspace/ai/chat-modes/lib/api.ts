import {
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  updateLibraryResource,
} from "@/modules/workspace/ai/library/lib/api";
import type { AiMetadata, AiModelTier } from "@/modules/workspace/ai/providers/lib/api";

export const AI_CHAT_MODE_KIND = "AiChatMode";

export const CHAT_MODES_QUERY_KEY = ["ai-chat-modes", "all"] as const;

export type AiChatModeTier = AiModelTier | "AUTO";

export type AiChatModeSpec = {
  displayName: string;
  description?: string;
  routeGroup: string;
  defaultTier?: AiChatModeTier;
  knowledgeBaseCodes?: string[];
  enabled?: boolean;
  sortOrder?: number;
};

export type AiChatModeItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: AiChatModeSpec;
};

export function fetchChatModes(): Promise<AiChatModeItem[]> {
  return listLibraryResources<AiChatModeItem>("aichatmodes");
}

export function createChatMode(mode: AiChatModeItem): Promise<AiChatModeItem> {
  return createLibraryResource<AiChatModeItem>("aichatmodes", mode);
}

export function updateChatMode(mode: AiChatModeItem): Promise<AiChatModeItem> {
  return updateLibraryResource<AiChatModeItem>("aichatmodes", mode);
}

export function deleteChatMode(name: string): Promise<void> {
  return deleteLibraryResource("aichatmodes", name);
}
