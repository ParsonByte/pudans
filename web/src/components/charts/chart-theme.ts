"use client";

import { useSyncExternalStore } from "react";

export type ChartTokens = {
  background: string;
  border: string;
  chart1: string;
  chart2: string;
  chart3: string;
  chart4: string;
  chart5: string;
  destructive: string;
  foreground: string;
  muted: string;
  mutedForeground: string;
  popover: string;
  popoverForeground: string;
  primary: string;
  primaryForeground: string;
};

const TOKEN_VARIABLES: Record<keyof ChartTokens, string> = {
  background: "--background",
  border: "--border",
  chart1: "--chart-1",
  chart2: "--chart-2",
  chart3: "--chart-3",
  chart4: "--chart-4",
  chart5: "--chart-5",
  destructive: "--destructive",
  foreground: "--foreground",
  muted: "--muted",
  mutedForeground: "--muted-foreground",
  popover: "--popover",
  popoverForeground: "--popover-foreground",
  primary: "--primary",
  primaryForeground: "--primary-foreground",
};

const EMPTY_TOKENS: ChartTokens = {
  background: "",
  border: "",
  chart1: "",
  chart2: "",
  chart3: "",
  chart4: "",
  chart5: "",
  destructive: "",
  foreground: "",
  muted: "",
  mutedForeground: "",
  popover: "",
  popoverForeground: "",
  primary: "",
  primaryForeground: "",
};

let cachedTokens: ChartTokens | null = null;

function readTokens(): ChartTokens {
  if (typeof window === "undefined") {
    return EMPTY_TOKENS;
  }
  const style = getComputedStyle(document.documentElement);
  return Object.fromEntries(
    Object.entries(TOKEN_VARIABLES).map(([key, variable]) => [
      key,
      style.getPropertyValue(variable).trim(),
    ]),
  ) as ChartTokens;
}

function getSnapshot(): ChartTokens {
  if (!cachedTokens) {
    cachedTokens = readTokens();
  }
  return cachedTokens;
}

function getServerSnapshot(): ChartTokens {
  return EMPTY_TOKENS;
}

function subscribe(onStoreChange: () => void): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }
  cachedTokens = readTokens();
  const observer = new MutationObserver(() => {
    cachedTokens = null;
    onStoreChange();
  });
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class", "style"],
  });
  return () => observer.disconnect();
}

export function useChartTokens(): ChartTokens {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
