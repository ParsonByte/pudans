import { AI_API_PREFIX, type AiProviderProtocol } from "../../providers/lib/api";
import type {
  GatewayKeyLimit,
  GatewayRecentError,
  GatewayServiceTier,
  GatewayTokenItem,
} from "./api";

export type GatewayProtocol = "openai" | "anthropic" | "gemini";

export type GatewayExample = "shell" | "curl" | "python" | "node";

export type GatewayExampleOs = "unix" | "windows";

export type GatewayTokenState = "ACTIVE" | "REVOKED" | "EXPIRED";

export const GATEWAY_PROTOCOLS: {
  value: GatewayProtocol;
  label: string;
  description: string;
  baseHint: string;
  agentSource: string;
}[] = [
  {
    value: "openai",
    label: "OpenAI",
    description: "Chat Completions，兼容多数 Agent 和 OpenAI SDK。",
    baseHint: "OpenAI SDK 会在这个地址后追加 /chat/completions。",
    agentSource: "openai",
  },
  {
    value: "anthropic",
    label: "Anthropic",
    description: "Messages API，兼容 Claude Code 和 Anthropic SDK。",
    baseHint: "Anthropic SDK 会在这个地址后追加 /v1/messages。",
    agentSource: "claude-code",
  },
  {
    value: "gemini",
    label: "Gemini",
    description: "generateContent / streamGenerateContent，兼容 Gemini 原生客户端。",
    baseHint: "请求路径为 /v1beta/models/{model}:generateContent；模型由路径中的 model 指定。",
    agentSource: "gemini",
  },
];

export const GATEWAY_EXAMPLES: { value: GatewayExample; label: string }[] = [
  { value: "shell", label: "Shell" },
  { value: "curl", label: "curl" },
  { value: "python", label: "Python" },
  { value: "node", label: "Node" },
];

export const GATEWAY_OS_OPTIONS: { value: GatewayExampleOs; label: string }[] = [
  { value: "unix", label: "macOS / Linux" },
  { value: "windows", label: "Windows（PowerShell）" },
];

export const GATEWAY_UPSTREAM_PROTOCOL_OPTIONS: {
  value: AiProviderProtocol;
  label: string;
}[] = [
  { value: "OPENAI_CHAT", label: "OpenAI Chat" },
  { value: "OPENAI_RESPONSES", label: "OpenAI Responses" },
  { value: "ANTHROPIC", label: "Anthropic Messages" },
  { value: "DEEPSEEK", label: "DeepSeek Anthropic" },
  { value: "DEEPSEEK_CHAT", label: "DeepSeek Chat" },
  { value: "DEEPSEEK_RESPONSES", label: "DeepSeek Responses" },
  { value: "MINIMAX", label: "MiniMax Anthropic" },
  { value: "MINIMAX_CHAT", label: "MiniMax Chat" },
  { value: "MINIMAX_NATIVE", label: "MiniMax 原生" },
  { value: "MINIMAX_RESPONSES", label: "MiniMax Responses" },
  { value: "QIANWEN", label: "通义千问" },
  { value: "GEMINI", label: "Gemini" },
  { value: "GEMINI_OPENAI", label: "Gemini OpenAI 兼容" },
  { value: "OLLAMA", label: "Ollama" },
  { value: "CUSTOM_HTTP", label: "自定义 HTTP" },
];

export const GATEWAY_SERVICE_TIER_OPTIONS: { value: GatewayServiceTier; label: string }[] = [
  { value: "STANDARD", label: "标准" },
  { value: "PRIORITY", label: "优先" },
  { value: "ULTRAFAST", label: "极速" },
];

export const GATEWAY_LIMIT_PERIOD_OPTIONS: {
  value: GatewayKeyLimit["period"];
  label: string;
}[] = [
  { value: "DAY", label: "每日" },
  { value: "WEEK", label: "每周" },
  { value: "MONTH", label: "每月" },
  { value: "TOTAL", label: "总计" },
];

export const GATEWAY_KEY_PLACEHOLDER = "pat_REPLACE_WITH_GATEWAY_KEY";

export function defaultGatewayExampleOs(): GatewayExampleOs {
  if (typeof navigator !== "undefined" && /Windows/i.test(navigator.userAgent)) {
    return "windows";
  }
  return "unix";
}

export function gatewayRootUrl(entryUrl?: string): string {
  const origin = typeof window === "undefined" ? "" : window.location.origin;
  const raw = entryUrl?.trim() || origin;
  const candidate = raw.startsWith("/") ? `${origin}${raw}` : raw;
  const base = candidate.replace(/\/+$/, "");
  const marker = `${AI_API_PREFIX}/aigateway`;
  const markerIndex = base.indexOf(marker);
  if (markerIndex >= 0) {
    return base.slice(0, markerIndex + marker.length);
  }
  if (base.endsWith("/aigateway")) {
    return base;
  }
  return `${base}${marker}`;
}

export function gatewayApiBase(protocol: GatewayProtocol, entryUrl?: string): string {
  const root = gatewayRootUrl(entryUrl);
  if (protocol === "openai") {
    return `${root}/openai/v1`;
  }
  if (protocol === "gemini") {
    return `${root}/gemini`;
  }
  return `${root}/anthropic`;
}

export function gatewayProtocolDescription(protocol: GatewayProtocol): string {
  return GATEWAY_PROTOCOLS.find((item) => item.value === protocol)?.description ?? "";
}

export function gatewayProtocolBaseHint(protocol: GatewayProtocol): string {
  return GATEWAY_PROTOCOLS.find((item) => item.value === protocol)?.baseHint ?? "";
}

export function gatewayProtocolAgentSource(protocol: GatewayProtocol): string {
  return GATEWAY_PROTOCOLS.find((item) => item.value === protocol)?.agentSource ?? "openai";
}

export function gatewayStatusProtocolLabel(value: string): string {
  const option = GATEWAY_UPSTREAM_PROTOCOL_OPTIONS.find((item) => item.value === value);
  if (option) {
    return option.label;
  }
  const ingress = GATEWAY_PROTOCOLS.find((item) => item.value === value.toLowerCase());
  if (ingress) {
    return ingress.label;
  }
  if (value.toLowerCase() === "responses" || value.toLowerCase() === "openai_responses") {
    return "OpenAI Responses";
  }
  return value;
}

export function gatewayStatusCount(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (Array.isArray(value)) {
    return value.length;
  }
  return undefined;
}

export function gatewayStatusProtocols(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string" && item.length > 0);
}

export function gatewayStatusRecentErrors(value: unknown): GatewayRecentError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const errors: GatewayRecentError[] = [];
  for (const item of value) {
    if (typeof item === "string" && item.trim()) {
      errors.push({ code: item.trim() });
      continue;
    }
    if (item && typeof item === "object") {
      const record = item as Record<string, unknown>;
      const code = [record.code, record.errorCode, record.message].find(
        (entry): entry is string => typeof entry === "string" && entry.trim().length > 0,
      );
      if (code) {
        const at = [record.at, record.createdAt, record.timestamp].find(
          (entry): entry is string => typeof entry === "string" && entry.length > 0,
        );
        errors.push({ code: code.trim(), at });
      }
    }
  }
  return errors.slice(0, 5);
}

export function ingressProtocol(agentSource: string | undefined): string {
  if (agentSource === "claude-code" || agentSource === "anthropic") {
    return "Anthropic";
  }
  if (agentSource === "gemini") {
    return "Gemini";
  }
  if (agentSource === "openai" || agentSource === "pudans-chat") {
    return "OpenAI";
  }
  return "—";
}

export function gatewayTokenState(token: GatewayTokenItem, now = Date.now()): GatewayTokenState {
  if (token.spec.revoked) {
    return "REVOKED";
  }
  if (token.spec.expiresAt && new Date(token.spec.expiresAt).getTime() <= now) {
    return "EXPIRED";
  }
  return "ACTIVE";
}

export function gatewayTokenStateLabel(state: GatewayTokenState): string {
  if (state === "REVOKED") {
    return "已吊销";
  }
  if (state === "EXPIRED") {
    return "已过期";
  }
  return "有效";
}

export function gatewayTokenFingerprint(tokenId: string | undefined): string {
  return tokenId ? `pat_${tokenId.slice(0, 8)}` : "pat_••••";
}

export function gatewayServiceTierLabel(tier: GatewayServiceTier | undefined): string {
  return GATEWAY_SERVICE_TIER_OPTIONS.find((item) => item.value === tier)?.label ?? "标准";
}

export function gatewayLimitPeriodLabel(period: GatewayKeyLimit["period"]): string {
  return GATEWAY_LIMIT_PERIOD_OPTIONS.find((item) => item.value === period)?.label ?? period;
}

const COUNT_FORMATTER = new Intl.NumberFormat("zh-CN");

export function gatewayKeyLimitSummary(limit: GatewayKeyLimit): string {
  const parts: string[] = [];
  if (limit.tokens != null) {
    parts.push(`${COUNT_FORMATTER.format(limit.tokens)} Token`);
  }
  if (limit.points != null) {
    parts.push(`${COUNT_FORMATTER.format(limit.points)} 积分`);
  }
  if (limit.requests != null) {
    parts.push(`${COUNT_FORMATTER.format(limit.requests)} 请求`);
  }
  if (parts.length === 0) {
    parts.push("未设置上限");
  }
  if (limit.models && limit.models.length > 0) {
    parts.push(`模型 ${limit.models.length} 个`);
  }
  if (limit.account) {
    parts.push(`账号 ${limit.account}`);
  }
  return parts.join(" · ");
}

export function gatewayExamples(
  protocol: GatewayProtocol,
  modelCode: string,
  key: string,
  os: GatewayExampleOs = "unix",
): Record<GatewayExample, string> {
  const base = gatewayApiBase(protocol);
  const model = modelCode || "<model>";
  const shell = (name: string, value: string) =>
    os === "windows" ? `$env:${name} = "${value}"` : `export ${name}=${value}`;
  const curlCommand = os === "windows" ? "curl.exe" : "curl";
  if (protocol === "anthropic") {
    return {
      shell: `${shell("ANTHROPIC_BASE_URL", base)}
${shell("ANTHROPIC_AUTH_TOKEN", key)}`,
      curl: `${curlCommand} "${base}/v1/messages" \\
  -H "Authorization: Bearer $ANTHROPIC_AUTH_TOKEN" \\
  -H "anthropic-version: 2023-06-01" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "${model}",
    "max_tokens": 1024,
    "messages": [{"role": "user", "content": "你好"}]
  }'`,
      python: `import os
from anthropic import Anthropic

client = Anthropic(
    base_url=os.environ["ANTHROPIC_BASE_URL"],
    auth_token=os.environ["ANTHROPIC_AUTH_TOKEN"],
)
message = client.messages.create(
    model="${model}",
    max_tokens=1024,
    messages=[{"role": "user", "content": "你好"}],
)
print(message.content)`,
      node: `import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic({
  baseURL: process.env.ANTHROPIC_BASE_URL,
  authToken: process.env.ANTHROPIC_AUTH_TOKEN,
});

const message = await client.messages.create({
  model: "${model}",
  max_tokens: 1024,
  messages: [{ role: "user", content: "你好" }],
});
console.log(message.content);`,
    };
  }
  if (protocol === "gemini") {
    const envKey = os === "windows" ? "$env:GEMINI_API_KEY" : "$GEMINI_API_KEY";
    return {
      shell: `${shell("GEMINI_BASE_URL", base)}
${shell("GEMINI_API_KEY", key)}`,
      curl: `${curlCommand} "${base}/v1beta/models/${model}:generateContent" \\
  -H "Authorization: Bearer ${envKey}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "contents": [{"role": "user", "parts": [{"text": "你好"}]}]
  }'`,
      python: `import os
import requests

response = requests.post(
    f"{os.environ['GEMINI_BASE_URL']}/v1beta/models/${model}:generateContent",
    headers={"Authorization": f"Bearer {os.environ['GEMINI_API_KEY']}"},
    json={"contents": [{"role": "user", "parts": [{"text": "你好"}]}]},
)
print(response.json())`,
      node: `const url = \`\${process.env.GEMINI_BASE_URL}/v1beta/models/${model}:generateContent\`;
const response = await fetch(url, {
  method: "POST",
  headers: {
    Authorization: \`Bearer \${process.env.GEMINI_API_KEY}\`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    contents: [{ role: "user", parts: [{ text: "你好" }] }],
  }),
});
console.log(await response.json());`,
    };
  }
  const envKey = os === "windows" ? "$env:OPENAI_API_KEY" : "$OPENAI_API_KEY";
  return {
    shell: `${shell("OPENAI_BASE_URL", base)}
${shell("OPENAI_API_KEY", key)}`,
    curl: `${curlCommand} "${base}/chat/completions" \\
  -H "Authorization: Bearer ${envKey}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "${model}",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": false
  }'`,
    python: `import os
from openai import OpenAI

client = OpenAI(
    base_url=os.environ["OPENAI_BASE_URL"],
    api_key=os.environ["OPENAI_API_KEY"],
)
completion = client.chat.completions.create(
    model="${model}",
    messages=[{"role": "user", "content": "你好"}],
)
print(completion.choices[0].message.content)`,
    node: `import OpenAI from "openai";

const client = new OpenAI({
  baseURL: process.env.OPENAI_BASE_URL,
  apiKey: process.env.OPENAI_API_KEY,
});

const completion = await client.chat.completions.create({
  model: "${model}",
  messages: [{ role: "user", content: "你好" }],
});
console.log(completion.choices[0].message.content);`,
  };
}
