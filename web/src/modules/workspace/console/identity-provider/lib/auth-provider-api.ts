import { type AuthProvider, type ListedAuthProvider, apiClient } from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";
import {
  type ExtensionRecord,
  mergeExtensionRecord,
  normalizeExtensionRecord,
} from "@/modules/workspace/console/settings/lib/extension-records";

const AUTH_PROVIDER_API_VERSION = "pudans.tolim.cn/v1alpha1";

const AUTH_PROVIDER_KIND = "AuthProvider";

const LIST_SIZE = 200;

export async function listAuthProviderRecords(): Promise<ExtensionRecord[]> {
  const response = await apiClient.authProvider.listAuthProvider({ page: 1, size: LIST_SIZE });

  return (response.data.items ?? []).map(normalizeExtensionRecord);
}

export async function listAuthProviderStates(): Promise<ListedAuthProvider[]> {
  const response = await apiClient.authProvider.listAuthProviders();

  return Array.isArray(response.data) ? response.data : [];
}

export async function createAuthProviderRecord(record: ExtensionRecord): Promise<void> {
  await apiClient.authProvider.createAuthProvider({
    authProvider: record as unknown as AuthProvider,
  });
}

export async function updateAuthProviderRecord(
  name: string,
  record: ExtensionRecord,
): Promise<void> {
  await apiClient.authProvider.updateAuthProvider({
    name,
    authProvider: record as unknown as AuthProvider,
  });
}

export async function deleteAuthProviderRecord(name: string): Promise<void> {
  await apiClient.authProvider.deleteAuthProvider({ name });
}

export async function setAuthProviderEnabled(name: string, enabled: boolean): Promise<void> {
  if (enabled) {
    await apiClient.authProvider.enableAuthProvider({ name });
    return;
  }
  await apiClient.authProvider.disableAuthProvider({ name });
}

export function buildAuthProviderRecord(
  existing: ExtensionRecord | undefined,
  body: Record<string, unknown>,
): ExtensionRecord {
  const record = mergeExtensionRecord(existing, body, {
    apiVersion: AUTH_PROVIDER_API_VERSION,
    kind: AUTH_PROVIDER_KIND,
  });
  const spec = record.spec ?? {};
  const nextSpec: Record<string, unknown> = { ...spec };

  for (const key of ["description", "logo", "website", "helpPage", "bindingUrl", "unbindUrl"]) {
    if (typeof nextSpec[key] === "string" && !String(nextSpec[key]).trim()) {
      delete nextSpec[key];
    }
  }

  const settingRef = nextSpec.settingRef;
  if (settingRef && typeof settingRef === "object") {
    const ref = settingRef as Record<string, unknown>;
    const name = typeof ref.name === "string" ? ref.name.trim() : "";
    const group = typeof ref.group === "string" ? ref.group.trim() : "";
    if (name && group) {
      nextSpec.settingRef = { name, group };
    } else {
      delete nextSpec.settingRef;
    }
  }

  const configMapRef = nextSpec.configMapRef;
  if (configMapRef && typeof configMapRef === "object") {
    const ref = configMapRef as Record<string, unknown>;
    const name = typeof ref.name === "string" ? ref.name.trim() : "";
    if (name) {
      nextSpec.configMapRef = { name };
    } else {
      delete nextSpec.configMapRef;
    }
  }

  record.spec = nextSpec;
  return record;
}

export function resolveAuthProviderError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "登录提供方不存在或已被删除。",
    conflict: "登录提供方名称已存在，或与现有配置冲突。",
  });
}
