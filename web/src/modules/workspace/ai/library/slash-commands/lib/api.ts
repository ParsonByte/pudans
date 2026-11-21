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

export const AI_SLASH_COMMAND_KIND = "AiSlashCommand";

const SLASH_COMMANDS_PLURAL = "aislashcommands";

export const SLASH_COMMANDS_QUERY_KEY = ["ai-slash-commands"] as const;

export const SLASH_COMMAND_NAME_PATTERN = /^[a-z0-9][a-z0-9-]*$/;

export type SlashCommandSpec = {
  name: string;
  description?: string;
  prompt: string;
  argumentHint?: string;
  enabled?: boolean;
  agents?: string[];
};

export type SlashCommandItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: SlashCommandSpec;
};

export type SlashCommandDraftPayload = {
  apiVersion: string;
  kind: string;
  metadata: { generateName: string };
  spec: SlashCommandSpec;
};

export function fetchSlashCommands(): Promise<SlashCommandItem[]> {
  return listLibraryResources<SlashCommandItem>(SLASH_COMMANDS_PLURAL);
}

export function createSlashCommand(draft: SlashCommandDraftPayload): Promise<SlashCommandItem> {
  return createLibraryResource<SlashCommandItem>(SLASH_COMMANDS_PLURAL, draft);
}

export function updateSlashCommand(item: SlashCommandItem): Promise<SlashCommandItem> {
  return updateLibraryResource(SLASH_COMMANDS_PLURAL, item);
}

export function patchSlashCommand(
  name: string,
  operations: JsonPatchOperation[],
): Promise<SlashCommandItem> {
  return patchLibraryResource<SlashCommandItem>(SLASH_COMMANDS_PLURAL, name, operations);
}

export function deleteSlashCommand(name: string): Promise<void> {
  return deleteLibraryResource(SLASH_COMMANDS_PLURAL, name);
}

export type ResolvedSlashCommand = {
  name: string;
  expandedPrompt: string;
};

export async function resolveSlashCommand(
  agent: string,
  text: string,
): Promise<ResolvedSlashCommand> {
  const response = await axiosInstance.post<ResolvedSlashCommand>(
    `${AI_API_PREFIX}/${SLASH_COMMANDS_PLURAL}/-/resolve`,
    { agent, text },
  );
  return response.data;
}
