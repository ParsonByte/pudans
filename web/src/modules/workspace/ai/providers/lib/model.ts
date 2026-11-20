import { formatBeijingDateTime, toDate } from "@/lib/date";

import type {
  AiAccountType,
  AiModality,
  AiModelCapability,
  AiModelItem,
  AiModelTier,
  AiModelType,
  AiProviderAccountBalance,
  AiProviderAccountQuota,
  AiProviderAccountQuotaWindow,
  AiProviderAffinity,
  AiProviderItem,
  AiProviderRouting,
  AiProviderStatus,
  AiProviderVendor,
  AiQuotaUnit,
} from "./api";
import { vendorLabel } from "./presets";

type Option<T extends string> = {
  value: T;
  label: string;
};

export const RESOURCE_NAME_PATTERN = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/;

export const RESOURCE_NAME_HINT = "仅支持小写字母、数字和中划线，且以字母或数字开头结尾。";

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

export const MODEL_CAPABILITY_OPTIONS: Option<AiModelCapability>[] = [
  { value: "CHAT", label: "对话" },
  { value: "TOOLS", label: "工具调用" },
  { value: "JSON", label: "JSON 输出" },
  { value: "VISION", label: "图像理解" },
  { value: "AUDIO_UNDERSTANDING", label: "音频理解" },
  { value: "VIDEO_UNDERSTANDING", label: "视频理解" },
  { value: "EMBEDDING", label: "向量" },
  { value: "RERANK", label: "重排" },
  { value: "IMAGE_GENERATION", label: "图像生成" },
  { value: "IMAGE_EDITING", label: "图像编辑" },
  { value: "VIDEO_GENERATION", label: "视频生成" },
  { value: "SPEECH_RECOGNITION", label: "语音识别" },
  { value: "SPEECH_SYNTHESIS", label: "语音合成" },
  { value: "AUDIO_GENERATION", label: "音频生成" },
  { value: "MUSIC_GENERATION", label: "音乐生成" },
  { value: "THREE_D_GENERATION", label: "3D 生成" },
  { value: "OCR", label: "OCR" },
  { value: "STREAM", label: "流式输出" },
];

export const MODALITY_OPTIONS: Option<AiModality>[] = [
  { value: "TEXT", label: "文本" },
  { value: "IMAGE", label: "图像" },
  { value: "AUDIO", label: "音频" },
  { value: "VIDEO", label: "视频" },
  { value: "FILE", label: "文件" },
  { value: "THREE_D", label: "3D" },
];

type ProviderDefaults = {
  vendor: AiProviderVendor;
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
  vendor: "CUSTOM",
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

export function modelDisplayName(model: AiModelItem): string {
  return model.spec.displayName || model.spec.modelCode;
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

export function formatTestTimestamp(value: string | undefined): string | undefined {
  return toDate(value) ? formatBeijingDateTime(value) : undefined;
}

export function statusLabel(status: AiProviderStatus | undefined): string {
  if (!status) {
    return "尚未测试";
  }
  if (status.message) {
    return status.healthScore != null && status.healthScore < 100 ? "异常" : "已记录";
  }
  return status.healthScore == null ? "尚未测试" : "正常";
}

function formatWindow(window: AiProviderAccountQuotaWindow): string {
  const unit = optionLabel(QUOTA_UNIT_OPTIONS, window.unit);
  const limit = window.limit == null ? "不限" : formatNumber(window.limit);
  const used = formatNumber(window.used);
  return `${window.name || "窗口"} ${used} / ${limit} ${unit}`;
}

export function windowsSummary(
  windows: readonly AiProviderAccountQuotaWindow[] | undefined,
): string {
  if (!windows || windows.length === 0) {
    return "未设置窗口";
  }
  return windows.map((window) => formatWindow(window)).join(" · ");
}

function providerSortOrder(provider: AiProviderItem): number {
  return provider.spec.order ?? Number.MAX_SAFE_INTEGER;
}

export function sortProvidersByOrder(providers: readonly AiProviderItem[]): AiProviderItem[] {
  return [...providers].sort(
    (left, right) =>
      providerSortOrder(left) - providerSortOrder(right) ||
      (left.spec.displayName || left.metadata.name).localeCompare(
        right.spec.displayName || right.metadata.name,
        "zh-CN",
      ),
  );
}

export function supportsModelDiscovery(vendor: AiProviderVendor | undefined): boolean {
  return Boolean(
    vendor &&
    (vendor === "OPENAI" ||
      vendor === "ANTHROPIC" ||
      vendor === "GEMINI" ||
      vendor === "DEEPSEEK" ||
      vendor === "MINIMAX" ||
      vendor === "QIANWEN"),
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

export function vendorEndpoint(
  vendor: AiProviderVendor | undefined,
  baseUrl: string | undefined,
): string | undefined {
  if (!vendor || !baseUrl) {
    return undefined;
  }
  const base = baseUrl.replace(/\/+$/, "");
  switch (vendor) {
    case "OPENAI":
      return `${base}/v1/chat/completions`;
    case "ANTHROPIC":
      return `${base}/v1/messages`;
    case "GEMINI":
      return `${base}/v1beta/models/{model}:generateContent`;
    case "DEEPSEEK":
      return `${base}/chat/completions`;
    case "MINIMAX":
      return `${base}/v1/chat/completions`;
    case "QIANWEN":
      return `${base}/compatible-mode/v1/chat/completions`;
    case "OLLAMA":
      return `${base}/api/chat`;
    case "CUSTOM":
      return base;
  }
}

export function providerEndpoints(
  vendor: AiProviderVendor | undefined,
  baseUrl: string | undefined,
): ProviderEndpoint[] {
  const url = vendorEndpoint(vendor, baseUrl);
  return url ? [{ label: vendorLabel(vendor), url }] : [];
}
