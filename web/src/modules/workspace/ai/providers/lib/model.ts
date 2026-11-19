import type {
  AiAccountType,
  AiModelTier,
  AiModelType,
  AiProviderAccountBalance,
  AiProviderAccountQuota,
  AiProviderAffinity,
  AiProviderProtocol,
  AiProviderRouting,
  AiQuotaUnit,
} from "./api";

type Option<T extends string> = {
  value: T;
  label: string;
};

export const RESOURCE_NAME_PATTERN = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/;

export const RESOURCE_NAME_HINT = "仅支持小写字母、数字和中划线，且以字母或数字开头结尾。";

export const PROTOCOL_OPTIONS: Option<AiProviderProtocol>[] = [
  { value: "OPENAI_CHAT", label: "OpenAI Chat" },
  { value: "OPENAI_RESPONSES", label: "OpenAI Responses" },
  { value: "ANTHROPIC", label: "Anthropic" },
  { value: "DEEPSEEK", label: "DeepSeek" },
  { value: "DEEPSEEK_CHAT", label: "DeepSeek Chat" },
  { value: "DEEPSEEK_RESPONSES", label: "DeepSeek Responses" },
  { value: "MINIMAX", label: "MiniMax" },
  { value: "MINIMAX_CHAT", label: "MiniMax Chat" },
  { value: "MINIMAX_NATIVE", label: "MiniMax 原生" },
  { value: "MINIMAX_RESPONSES", label: "MiniMax Responses" },
  { value: "QIANWEN", label: "通义千问" },
  { value: "GEMINI", label: "Gemini" },
  { value: "OLLAMA", label: "Ollama" },
  { value: "CUSTOM_HTTP", label: "自定义 HTTP" },
];

export const ROUTING_OPTIONS: Option<AiProviderRouting>[] = [
  { value: "SMART", label: "智能" },
  { value: "ORDER", label: "顺序" },
  { value: "ROTATE", label: "轮询" },
  { value: "USAGE", label: "用量" },
  { value: "PACE", label: "节奏" },
  { value: "WEIGHT", label: "权重" },
  { value: "FREE", label: "免费优先" },
  { value: "FAST", label: "速度优先" },
  { value: "CHEAP", label: "成本优先" },
  { value: "DRAIN", label: "排空" },
  { value: "EXPIRING", label: "到期优先" },
];

export const AFFINITY_OPTIONS: Option<AiProviderAffinity>[] = [
  { value: "AUTO", label: "自动" },
  { value: "SESSION", label: "会话粘滞" },
  { value: "TURN", label: "轮次粘滞" },
  { value: "OFF", label: "关闭" },
  { value: "STRICT", label: "严格" },
];

export const ACCOUNT_TYPE_OPTIONS: Option<AiAccountType>[] = [
  { value: "API_KEY", label: "API Key" },
  { value: "OAUTH", label: "OAuth" },
  { value: "CUSTOM", label: "自定义" },
];

export const QUOTA_UNIT_OPTIONS: Option<AiQuotaUnit>[] = [
  { value: "TOKEN", label: "Token" },
  { value: "REQUEST", label: "请求" },
  { value: "CREDIT", label: "点数" },
  { value: "USD", label: "美元" },
];

export const MODEL_TYPE_OPTIONS: Option<AiModelType>[] = [
  { value: "CHAT", label: "对话" },
  { value: "EMBEDDING", label: "向量" },
  { value: "RERANK", label: "重排" },
  { value: "IMAGE", label: "图像" },
  { value: "VIDEO", label: "视频" },
  { value: "SPEECH", label: "语音" },
  { value: "AUDIO", label: "音频" },
  { value: "MUSIC", label: "音乐" },
  { value: "THREE_D", label: "3D" },
];

export const MODEL_TIER_OPTIONS: Option<AiModelTier>[] = [
  { value: "CHEAP", label: "经济" },
  { value: "MEDIUM", label: "标准" },
  { value: "HIGH", label: "高级" },
  { value: "ULTRA", label: "旗舰" },
];

type ProviderDefaults = {
  protocol: AiProviderProtocol;
  routing: AiProviderRouting;
  affinity: AiProviderAffinity;
  maxConcurrency: number;
  maxRPM: number;
  connectTimeoutMs: number;
  readTimeoutMs: number;
  free: boolean;
  enabled: boolean;
};

export const DEFAULT_PROVIDER_SPEC: ProviderDefaults = {
  protocol: "OPENAI_CHAT",
  routing: "SMART",
  affinity: "AUTO",
  maxConcurrency: 100,
  maxRPM: 0,
  connectTimeoutMs: 5000,
  readTimeoutMs: 60000,
  free: false,
  enabled: true,
};

type AccountDefaults = {
  accountType: AiAccountType;
  weight: number;
  priority: number;
  enabled: boolean;
  maxConcurrency: number;
  maxRPM: number;
  freePlan: boolean;
  creditsEnabled: boolean;
};

export const DEFAULT_ACCOUNT_SPEC: AccountDefaults = {
  accountType: "API_KEY",
  weight: 1,
  priority: 0,
  enabled: true,
  maxConcurrency: 100,
  maxRPM: 0,
  freePlan: false,
  creditsEnabled: false,
};

export const PROTOCOL_DEFAULT_BASE_URLS: Partial<Record<AiProviderProtocol, string>> = {
  DEEPSEEK: "https://api.deepseek.com",
  DEEPSEEK_CHAT: "https://api.deepseek.com",
  DEEPSEEK_RESPONSES: "https://api.deepseek.com",
};

export function defaultBaseUrlFor(protocol: AiProviderProtocol | undefined): string {
  return protocol ? (PROTOCOL_DEFAULT_BASE_URLS[protocol] ?? "") : "";
}

export function optionLabel<T extends string>(
  options: readonly Option<T>[],
  value: T | undefined,
  fallback = "—",
): string {
  if (!value) {
    return fallback;
  }
  return options.find((option) => option.value === value)?.label ?? value;
}

export function formatNumber(value: number | undefined, maximumFractionDigits = 2): string {
  if (value == null) {
    return "0";
  }
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits }).format(value);
}

export function formatQuota(quota: AiProviderAccountQuota | undefined): string {
  if (!quota) {
    return "未设置配额";
  }
  const limit = quota.limit == null ? "不限" : formatNumber(quota.limit);
  const used = formatNumber(quota.used);
  return `${used} / ${limit} ${optionLabel(QUOTA_UNIT_OPTIONS, quota.unit)}`;
}

export function formatBalance(balance: AiProviderAccountBalance | undefined): string {
  if (!balance || balance.left == null) {
    return "未设置余额";
  }
  return `${formatNumber(balance.left)} ${optionLabel(QUOTA_UNIT_OPTIONS, balance.unit)}`;
}

export function formatTestResult(result: {
  successful: boolean;
  latencyMillis?: number;
  message?: string;
}): string {
  if (!result.successful) {
    return result.message || "测试未通过。";
  }
  const latency = result.latencyMillis == null ? "" : `，耗时 ${result.latencyMillis} ms`;
  return result.message ? `${result.message}${latency}` : `连通性正常${latency}`;
}

export function supportsModelDiscovery(protocol: AiProviderProtocol | undefined): boolean {
  return (
    protocol === "DEEPSEEK" || protocol === "DEEPSEEK_CHAT" || protocol === "DEEPSEEK_RESPONSES"
  );
}

export function modelNameFromCode(code: string): string {
  return code
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 63);
}

export function uniqueModelName(base: string, used: ReadonlySet<string>): string {
  const fallback = base || "model";
  let candidate = fallback;
  let suffix = 2;
  while (used.has(candidate)) {
    const suffixText = `-${suffix}`;
    candidate = `${fallback.slice(0, 63 - suffixText.length)}${suffixText}`;
    suffix += 1;
  }
  return candidate;
}

export type ProviderEndpoint = {
  label: string;
  url: string;
};

export function providerEndpoints(
  protocol: AiProviderProtocol | undefined,
  baseUrl: string | undefined,
): ProviderEndpoint[] {
  if (!protocol || !baseUrl) {
    return [];
  }
  const base = baseUrl.replace(/\/+$/, "");
  switch (protocol) {
    case "DEEPSEEK":
    case "DEEPSEEK_CHAT":
    case "DEEPSEEK_RESPONSES":
      return [
        { label: "OpenAI", url: `${base}/chat/completions` },
        { label: "Responses", url: `${base}/responses` },
        { label: "Anthropic", url: `${base}/anthropic/v1/messages` },
      ];
    case "OPENAI_CHAT":
      return [{ label: "OpenAI", url: `${base}/v1/chat/completions` }];
    case "OPENAI_RESPONSES":
      return [{ label: "Responses", url: `${base}/v1/responses` }];
    case "ANTHROPIC":
      return [{ label: "Anthropic", url: `${base}/v1/messages` }];
    case "GEMINI":
      return [{ label: "Gemini", url: `${base}/v1beta/models/{model}:generateContent` }];
    case "QIANWEN":
      return [{ label: "OpenAI 兼容", url: `${base}/compatible-mode/v1/chat/completions` }];
    case "MINIMAX":
    case "MINIMAX_CHAT":
    case "MINIMAX_NATIVE":
    case "MINIMAX_RESPONSES":
      return [{ label: "MiniMax", url: `${base}/v1/text/chatcompletion_v2` }];
    case "OLLAMA":
      return [{ label: "Ollama", url: `${base}/api/chat` }];
    case "CUSTOM_HTTP":
      return [{ label: "自定义", url: base }];
  }
}
