import type { AiProviderVendor } from "./api";

export type ProviderRegionId = string;

export type ProviderGroup = "international" | "domestic";

type ProviderPresetKind = "vendor" | "relay" | "local";

export type PresetRegion = {
  id: ProviderRegionId;
  label: string;
  website: string;
  keysUrl: string;
  baseUrl: string;
  models: string[];
};

export type ProviderPreset = {
  id: string;
  displayName: string;
  description: string;
  group: ProviderGroup;
  kind: ProviderPresetKind;
  supported: boolean;
  unsupportedReason?: string | null;
  vendor: AiProviderVendor;
  regions: PresetRegion[];
  website: string;
  keysUrl: string;
  models: string[];
  note?: string | null;
};

export type ProviderPresetCatalog = {
  supported: ProviderPreset[];
  unsupported: ProviderPreset[];
};

export const PROVIDER_GROUP_LABELS: Record<ProviderGroup, string> = {
  international: "国际",
  domestic: "国内",
};

const VENDOR_LABELS: Record<AiProviderVendor, string> = {
  OPENAI: "OpenAI",
  ANTHROPIC: "Anthropic",
  GEMINI: "Gemini",
  DEEPSEEK: "DeepSeek",
  MINIMAX: "MiniMax",
  QIANWEN: "千问",
  OLLAMA: "Ollama",
  CUSTOM: "自定义",
};

const PRESET_ICON_PATHS: Record<string, string> = {
  openai: "brand/providers/openai.svg",
  anthropic: "brand/providers/anthropic.svg",
  gemini: "brand/providers/gemini.svg",
  deepseek: "brand/providers/deepseek.svg",
  minimax: "brand/providers/minimax.svg",
  qianwen: "brand/providers/qianwen.svg",
};

export function presetIcon(preset: ProviderPreset): string | undefined {
  const path = PRESET_ICON_PATHS[preset.id];
  return path ? `${import.meta.env.BASE_URL}${path}` : undefined;
}

export function presetRegion(preset: ProviderPreset, regionId: ProviderRegionId): PresetRegion {
  return preset.regions.find((region) => region.id === regionId) ?? preset.regions[0];
}

export function vendorLabel(vendor: AiProviderVendor | undefined): string {
  return vendor ? VENDOR_LABELS[vendor] : "—";
}

function normalizedUrl(value: string | undefined): string {
  return (value ?? "").trim().replace(/\/+$/, "").toLowerCase();
}

function endpointUrls(preset: ProviderPreset): string[] {
  return preset.regions.map((region) => normalizedUrl(region.baseUrl));
}

function urlHost(value: string | undefined): string | undefined {
  const trimmed = (value ?? "").trim().toLowerCase();
  if (!trimmed) {
    return undefined;
  }
  try {
    return new URL(trimmed).host;
  } catch {
    return undefined;
  }
}

export function presetOfProvider(
  presets: readonly ProviderPreset[],
  provider: {
    spec: { baseUrl?: string };
  },
): ProviderPreset | undefined {
  const baseUrl = normalizedUrl(provider.spec.baseUrl);
  const exact = presets.find((preset) => endpointUrls(preset).includes(baseUrl));
  if (exact) {
    return exact;
  }
  const host = urlHost(provider.spec.baseUrl);
  if (!host) {
    return undefined;
  }
  return presets.find((preset) =>
    preset.regions.some((region) => urlHost(region.baseUrl) === host),
  );
}

export function websiteForProvider(
  presets: readonly ProviderPreset[],
  provider: {
    spec: { baseUrl?: string };
  },
): string | undefined {
  const preset = presetOfProvider(presets, provider);
  if (!preset) {
    return undefined;
  }
  const baseUrl = normalizedUrl(provider.spec.baseUrl);
  const matched = preset.regions.find((region) => normalizedUrl(region.baseUrl) === baseUrl);
  return matched?.website || preset.website || preset.regions[0]?.website || undefined;
}
