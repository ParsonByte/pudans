import { useCallback, useMemo } from "react";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import {
  USAGE_RANGE_OPTIONS,
  type UsageDateRangeValue,
  type UsageRangePreset,
  usageDateRange,
  validateUsageDateRange,
} from "./model";

export type UsageUrlSearch = Record<string, unknown>;

export type UsageSearchPatch = Record<string, string | undefined>;

const PRESET_VALUES = new Set<string>(
  USAGE_RANGE_OPTIONS.filter((option) => option.value !== "all").map((option) => option.value),
);

function textOf(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

export function usageTextParam(search: UsageUrlSearch, key: string): string {
  return textOf(search[key]);
}

export function usageRangeFromSearch(search: UsageUrlSearch): UsageDateRangeValue {
  const presetValue = textOf(search.range);
  const from = textOf(search.from);
  const to = textOf(search.to);
  if (presetValue === "custom" || (!presetValue && from && to)) {
    if (!validateUsageDateRange(from, to)) {
      return { preset: "custom", from, to };
    }
    return usageDateRange("7d");
  }
  if (PRESET_VALUES.has(presetValue) && presetValue !== "custom") {
    return usageDateRange(presetValue as Exclude<UsageRangePreset, "custom">);
  }
  return usageDateRange("7d");
}

export function usageRangeSearch(range: UsageDateRangeValue): UsageSearchPatch {
  if (range.preset === "custom") {
    return { range: "custom", from: range.from, to: range.to };
  }
  return { range: range.preset, from: undefined, to: undefined };
}

export function useUsageUrlSearch() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const search = useMemo<UsageUrlSearch>(
    () => Object.fromEntries(searchParams.entries()),
    [searchParams],
  );

  const update = useCallback(
    (patch: UsageSearchPatch) => {
      const params = new URLSearchParams(searchParams);
      for (const [key, value] of Object.entries(patch)) {
        if (value == null || value === "") {
          params.delete(key);
        } else {
          params.set(key, value);
        }
      }
      const query = params.toString();
      router.push(query ? `${pathname}?${query}` : pathname);
    },
    [pathname, router, searchParams],
  );

  return { search, update };
}
