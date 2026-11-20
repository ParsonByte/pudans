import {
  AI_API_VERSION,
  type AiMetadata,
  createLibraryResource,
  deleteLibraryResource,
  listLibraryResources,
  updateLibraryResource,
} from "../../lib/api";

export { AI_API_VERSION };

export const AI_INSTRUCTION_KIND = "AiInstruction";

const INSTRUCTIONS_PLURAL = "aiinstructions";

export const INSTRUCTIONS_QUERY_KEY = ["ai-instructions"] as const;

export const INSTRUCTION_SCOPES = ["SHARED", "AGENT"] as const;

export type InstructionScope = (typeof INSTRUCTION_SCOPES)[number];

export type InstructionSpec = {
  displayName: string;
  content: string;
  scope: InstructionScope;
  enabled?: boolean;
  agents?: string[];
};

export type InstructionItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: InstructionSpec;
};

export type InstructionDraftPayload = {
  apiVersion: string;
  kind: string;
  metadata: { generateName: string };
  spec: InstructionSpec;
};

export function fetchInstructions(): Promise<InstructionItem[]> {
  return listLibraryResources<InstructionItem>(INSTRUCTIONS_PLURAL);
}

export function createInstruction(draft: InstructionDraftPayload): Promise<InstructionItem> {
  return createLibraryResource<InstructionItem>(INSTRUCTIONS_PLURAL, draft);
}

export function updateInstruction(item: InstructionItem): Promise<InstructionItem> {
  return updateLibraryResource(INSTRUCTIONS_PLURAL, item);
}

export function deleteInstruction(name: string): Promise<void> {
  return deleteLibraryResource(INSTRUCTIONS_PLURAL, name);
}
