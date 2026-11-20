import { useQuery } from "@tanstack/react-query";

import { fetchProviderPresets } from "./api";

const AI_PROVIDER_PRESETS_QUERY_KEY = ["ai-provider-presets"] as const;

export function useProviderPresetsQuery(enabled = true) {
  return useQuery({
    queryKey: AI_PROVIDER_PRESETS_QUERY_KEY,
    queryFn: fetchProviderPresets,
    staleTime: 5 * 60_000,
    enabled,
    retry: false,
  });
}
