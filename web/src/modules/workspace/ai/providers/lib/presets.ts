import type { AiProviderProtocol } from "./api";

export type ProviderProtocolFamily = "openai" | "responses" | "anthropic";

export type ProviderRegionId = "global" | "cn";

export type ProviderGroup = "international" | "domestic";

export type PresetEndpoint = {
  protocol: AiProviderProtocol;
  baseUrl: string;
};

export type PresetRegion = {
  id: ProviderRegionId;
  label: string;
  website: string;
  keysUrl: string;
  openai?: PresetEndpoint;
  responses?: PresetEndpoint;
  anthropic?: PresetEndpoint;
};

export type ProviderPreset = {
  id: string;
  displayName: string;
  description: string;
  group: ProviderGroup;
  families: ProviderProtocolFamily[];
  regions: PresetRegion[];
};

export const PROVIDER_GROUP_LABELS: Record<ProviderGroup, string> = {
  international: "国际",
  domestic: "国内",
};

export const PROTOCOL_FAMILY_OPTIONS: {
  value: ProviderProtocolFamily;
  label: string;
}[] = [
  { value: "openai", label: "OpenAI" },
  { value: "responses", label: "Responses" },
  { value: "anthropic", label: "Anthropic" },
];

const PRESET_ICON_PATHS: Record<string, string> = {
  openai: "brand/providers/openai.svg",
  anthropic: "brand/providers/anthropic.svg",
  gemini: "brand/providers/gemini.svg",
  deepseek: "brand/providers/deepseek.svg",
  minimax: "brand/providers/minimax.svg",
  qianwen: "brand/providers/qianwen.svg",
};

export const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    id: "openai",
    displayName: "OpenAI",
    description: "OpenAI 官方 API",
    group: "international",
    families: ["openai", "responses"],
    regions: [
      {
        id: "global",
        label: "国际",
        website: "https://platform.openai.com",
        keysUrl: "https://platform.openai.com/api-keys",
        openai: { protocol: "OPENAI_CHAT", baseUrl: "https://api.openai.com" },
        responses: { protocol: "OPENAI_RESPONSES", baseUrl: "https://api.openai.com" },
      },
    ],
  },
  {
    id: "anthropic",
    displayName: "Anthropic",
    description: "Claude 官方 API",
    group: "international",
    families: ["anthropic"],
    regions: [
      {
        id: "global",
        label: "国际",
        website: "https://console.anthropic.com",
        keysUrl: "https://console.anthropic.com/settings/keys",
        anthropic: { protocol: "ANTHROPIC", baseUrl: "https://api.anthropic.com" },
      },
    ],
  },
  {
    id: "gemini",
    displayName: "Google Gemini",
    description: "Gemini Developer API · OpenAI 兼容",
    group: "international",
    families: ["openai"],
    regions: [
      {
        id: "global",
        label: "国际",
        website: "https://aistudio.google.com",
        keysUrl: "https://aistudio.google.com/apikey",
        openai: {
          protocol: "GEMINI_OPENAI",
          baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai",
        },
      },
    ],
  },
  {
    id: "deepseek",
    displayName: "DeepSeek",
    description: "DeepSeek 官方 API",
    group: "domestic",
    families: ["openai", "responses", "anthropic"],
    regions: [
      {
        id: "global",
        label: "国际",
        website: "https://platform.deepseek.com",
        keysUrl: "https://platform.deepseek.com/api_keys",
        openai: { protocol: "DEEPSEEK_CHAT", baseUrl: "https://api.deepseek.com" },
        responses: { protocol: "DEEPSEEK_RESPONSES", baseUrl: "https://api.deepseek.com" },
        anthropic: { protocol: "DEEPSEEK", baseUrl: "https://api.deepseek.com" },
      },
    ],
  },
  {
    id: "minimax",
    displayName: "MiniMax",
    description: "MiniMax 官方 API",
    group: "domestic",
    families: ["openai", "responses", "anthropic"],
    regions: [
      {
        id: "global",
        label: "国际",
        website: "https://platform.minimax.io",
        keysUrl: "https://platform.minimax.io/user-center/basic-information/interface-key",
        openai: { protocol: "MINIMAX_CHAT", baseUrl: "https://api.minimax.io" },
        responses: { protocol: "MINIMAX_RESPONSES", baseUrl: "https://api.minimax.io" },
        anthropic: { protocol: "MINIMAX", baseUrl: "https://api.minimax.io" },
      },
      {
        id: "cn",
        label: "中国",
        website: "https://platform.minimaxi.com",
        keysUrl: "https://platform.minimaxi.com/user-center/basic-information/interface-key",
        openai: { protocol: "MINIMAX_CHAT", baseUrl: "https://api.minimaxi.com" },
        responses: { protocol: "MINIMAX_RESPONSES", baseUrl: "https://api.minimaxi.com" },
        anthropic: { protocol: "MINIMAX", baseUrl: "https://api.minimaxi.com" },
      },
    ],
  },
  {
    id: "qianwen",
    displayName: "千问",
    description: "阿里云百炼 · DashScope",
    group: "domestic",
    families: ["openai", "anthropic"],
    regions: [
      {
        id: "global",
        label: "国际",
        website: "https://modelstudio.console.alibabacloud.com",
        keysUrl: "https://modelstudio.console.alibabacloud.com/?tab=playground#/api-key",
        openai: {
          protocol: "QIANWEN",
          baseUrl: "https://dashscope-intl.aliyuncs.com",
        },
        anthropic: {
          protocol: "ANTHROPIC",
          baseUrl: "https://dashscope-intl.aliyuncs.com/apps/anthropic",
        },
      },
      {
        id: "cn",
        label: "中国",
        website: "https://bailian.console.aliyun.com",
        keysUrl: "https://bailian.console.aliyun.com/?tab=model#/api-key",
        openai: { protocol: "QIANWEN", baseUrl: "https://dashscope.aliyuncs.com" },
        anthropic: {
          protocol: "ANTHROPIC",
          baseUrl: "https://dashscope.aliyuncs.com/apps/anthropic",
        },
      },
    ],
  },
];

export function presetIcon(preset: ProviderPreset): string | undefined {
  const path = PRESET_ICON_PATHS[preset.id];
  return path ? `${import.meta.env.BASE_URL}${path}` : undefined;
}

export function presetRegion(preset: ProviderPreset, regionId: ProviderRegionId): PresetRegion {
  return preset.regions.find((region) => region.id === regionId) ?? preset.regions[0];
}

export function presetEndpoint(
  preset: ProviderPreset,
  regionId: ProviderRegionId,
  family: ProviderProtocolFamily,
): PresetEndpoint | undefined {
  return presetRegion(preset, regionId)[family];
}

export function protocolForFamily(family: ProviderProtocolFamily): AiProviderProtocol {
  if (family === "anthropic") {
    return "ANTHROPIC";
  }
  if (family === "responses") {
    return "OPENAI_RESPONSES";
  }
  return "OPENAI_CHAT";
}

export function protocolFamilyLabel(protocol: AiProviderProtocol | undefined): string {
  if (protocol?.endsWith("RESPONSES")) {
    return "Responses";
  }
  return protocol === "ANTHROPIC" || protocol === "DEEPSEEK" || protocol === "MINIMAX"
    ? "Anthropic"
    : "OpenAI";
}

export function presetOfProvider(provider: {
  spec: { protocol: AiProviderProtocol; baseUrl?: string };
}): ProviderPreset | undefined {
  const protocol = provider.spec.protocol;
  const baseUrl = provider.spec.baseUrl ?? "";
  if (protocol === "GEMINI_OPENAI" || baseUrl.includes("generativelanguage")) {
    return PROVIDER_PRESETS.find((preset) => preset.id === "gemini");
  }
  if (
    (protocol === "OPENAI_CHAT" || protocol === "OPENAI_RESPONSES") &&
    baseUrl.includes("api.openai.com")
  ) {
    return PROVIDER_PRESETS.find((preset) => preset.id === "openai");
  }
  if (
    protocol === "DEEPSEEK" ||
    protocol === "DEEPSEEK_CHAT" ||
    protocol === "DEEPSEEK_RESPONSES" ||
    baseUrl.includes("api.deepseek.com")
  ) {
    return PROVIDER_PRESETS.find((preset) => preset.id === "deepseek");
  }
  if (protocol.startsWith("MINIMAX") || baseUrl.includes("minimax")) {
    return PROVIDER_PRESETS.find((preset) => preset.id === "minimax");
  }
  if (protocol === "QIANWEN" || baseUrl.includes("dashscope") || baseUrl.includes("aliyuncs")) {
    return PROVIDER_PRESETS.find((preset) => preset.id === "qianwen");
  }
  if (protocol === "ANTHROPIC" && baseUrl.includes("api.anthropic.com")) {
    return PROVIDER_PRESETS.find((preset) => preset.id === "anthropic");
  }
  return undefined;
}

export function websiteForProvider(provider: {
  spec: { protocol: AiProviderProtocol; baseUrl?: string };
}): string | undefined {
  const preset = presetOfProvider(provider);
  if (!preset) {
    return undefined;
  }
  const matched = preset.regions.find(
    (region) =>
      region.openai?.baseUrl === provider.spec.baseUrl ||
      region.responses?.baseUrl === provider.spec.baseUrl ||
      region.anthropic?.baseUrl === provider.spec.baseUrl,
  );
  return matched?.website;
}
