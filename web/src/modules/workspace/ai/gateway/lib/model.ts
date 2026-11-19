import { AI_API_PREFIX } from "../../providers/lib/api";

export type GatewayProtocol = "openai" | "anthropic";

export type GatewayExample = "shell" | "curl" | "python" | "node";

export const GATEWAY_PROTOCOLS: {
  value: GatewayProtocol;
  label: string;
  description: string;
}[] = [
  {
    value: "openai",
    label: "OpenAI",
    description: "Chat Completions，兼容多数 Agent 和 OpenAI SDK。",
  },
  {
    value: "anthropic",
    label: "Anthropic",
    description: "Messages API，兼容 Claude Code 和 Anthropic SDK。",
  },
];

export const GATEWAY_EXAMPLES: { value: GatewayExample; label: string }[] = [
  { value: "shell", label: "Shell" },
  { value: "curl", label: "curl" },
  { value: "python", label: "Python" },
  { value: "node", label: "Node" },
];

export function gatewayApiBase(protocol: GatewayProtocol): string {
  const origin = typeof window === "undefined" ? "" : window.location.origin;
  return protocol === "openai"
    ? `${origin}${AI_API_PREFIX}/aigateway/-/openai/v1`
    : `${origin}${AI_API_PREFIX}/aigateway/-/anthropic`;
}

export function gatewayProtocolDescription(protocol: GatewayProtocol): string {
  return GATEWAY_PROTOCOLS.find((item) => item.value === protocol)?.description ?? "";
}

export function ingressProtocol(agentSource: string | undefined): string {
  if (agentSource === "claude-code" || agentSource === "anthropic") {
    return "Anthropic";
  }
  if (agentSource === "openai" || agentSource === "pudans-chat") {
    return "OpenAI";
  }
  return "—";
}

export function gatewayExamples(
  protocol: GatewayProtocol,
  modelCode: string,
  key: string,
): Record<GatewayExample, string> {
  const base = gatewayApiBase(protocol);
  const model = modelCode || "<provider/model>";
  if (protocol === "anthropic") {
    return {
      shell: `export ANTHROPIC_BASE_URL=${base}
export ANTHROPIC_AUTH_TOKEN=${key}`,
      curl: `curl "${base}/v1/messages" \\
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
  return {
    shell: `export OPENAI_BASE_URL=${base}
export OPENAI_API_KEY=${key}`,
    curl: `curl "${base}/chat/completions" \\
  -H "Authorization: Bearer $OPENAI_API_KEY" \\
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
