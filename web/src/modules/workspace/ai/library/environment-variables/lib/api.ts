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

export const AI_ENVIRONMENT_VARIABLE_KIND = "AiEnvironmentVariable";

const ENVIRONMENT_VARIABLES_PLURAL = "aienvironmentvariables";

export const ENVIRONMENT_VARIABLES_QUERY_KEY = ["ai-environment-variables"] as const;

export const ENVIRONMENT_VARIABLE_NAME_PATTERN = /^[A-Z][A-Z0-9_]*$/;

export type EnvironmentVariableSpec = {
  name: string;
  value?: string;
  configured?: boolean;
  description?: string;
  enabled?: boolean;
  agents?: string[];
};

export type EnvironmentVariableItem = {
  apiVersion: string;
  kind: string;
  metadata: AiMetadata;
  spec: EnvironmentVariableSpec;
};

export type EnvironmentVariableDraft = {
  apiVersion: string;
  kind: string;
  metadata: { generateName: string };
  spec: EnvironmentVariableSpec;
};

export type MaskedEnvironmentVariable = {
  resourceName?: string;
  name?: string;
  configured: boolean;
  description?: string;
  enabled: boolean;
  agents: string[];
};

export function fetchEnvironmentVariables(): Promise<EnvironmentVariableItem[]> {
  return listLibraryResources<EnvironmentVariableItem>(ENVIRONMENT_VARIABLES_PLURAL);
}

export function createEnvironmentVariable(
  draft: EnvironmentVariableDraft,
): Promise<EnvironmentVariableItem> {
  return createLibraryResource<EnvironmentVariableItem>(ENVIRONMENT_VARIABLES_PLURAL, draft);
}

export function updateEnvironmentVariable(
  item: EnvironmentVariableItem,
): Promise<EnvironmentVariableItem> {
  return updateLibraryResource(ENVIRONMENT_VARIABLES_PLURAL, item);
}

export function deleteEnvironmentVariable(name: string): Promise<void> {
  return deleteLibraryResource(ENVIRONMENT_VARIABLES_PLURAL, name);
}

export async function updateEnvironmentVariableValue(
  name: string,
  value: string,
): Promise<MaskedEnvironmentVariable> {
  const response = await axiosInstance.post<MaskedEnvironmentVariable>(
    `${AI_API_PREFIX}/${ENVIRONMENT_VARIABLES_PLURAL}/${encodeURIComponent(name)}/value`,
    { value },
  );
  return response.data;
}

export async function clearEnvironmentVariableValue(
  name: string,
): Promise<MaskedEnvironmentVariable> {
  const response = await axiosInstance.post<MaskedEnvironmentVariable>(
    `${AI_API_PREFIX}/${ENVIRONMENT_VARIABLES_PLURAL}/${encodeURIComponent(name)}/value`,
    { clear: true },
  );
  return response.data;
}
