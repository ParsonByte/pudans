"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

export const PROFILE_PREFERENCE_GROUP = "profile";

export const PROFILE_PREFERENCES_QUERY_KEY = ["account-profile-preferences"] as const;

export type ProfilePreferences = {
  bioPreview: boolean;
  showAccountInfo: boolean;
};

export const DEFAULT_PROFILE_PREFERENCES: ProfilePreferences = {
  bioPreview: true,
  showAccountInfo: true,
};

function parsePreferenceRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  if (typeof value !== "string" || !value.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

function parseProfilePreferences(value: unknown): ProfilePreferences {
  const record = parsePreferenceRecord(value);
  return {
    bioPreview:
      typeof record?.bioPreview === "boolean"
        ? record.bioPreview
        : DEFAULT_PROFILE_PREFERENCES.bioPreview,
    showAccountInfo:
      typeof record?.showAccountInfo === "boolean"
        ? record.showAccountInfo
        : DEFAULT_PROFILE_PREFERENCES.showAccountInfo,
  };
}

export function useProfilePreferences() {
  return useQuery({
    queryKey: PROFILE_PREFERENCES_QUERY_KEY,
    queryFn: async () => {
      const response = await apiClient.userPreference.getMyPreference({
        group: PROFILE_PREFERENCE_GROUP,
      });
      return parseProfilePreferences(response.data);
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}
