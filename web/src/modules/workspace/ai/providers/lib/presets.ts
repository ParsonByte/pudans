import type { AiProviderProtocol } from "./api";

export type ProviderPreset = {
  id: string;
  displayName: string;
  protocol: AiProviderProtocol;
  baseUrl: string;
};

export const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    id: "deepseek",
    displayName: "DeepSeek",
    protocol: "DEEPSEEK",
    baseUrl: "https://api.deepseek.com",
  },
  {
    id: "openai",
    displayName: "OpenAI",
    protocol: "OPENAI_CHAT",
    baseUrl: "https://api.openai.com",
  },
  {
    id: "anthropic",
    displayName: "Anthropic",
    protocol: "ANTHROPIC",
    baseUrl: "https://api.anthropic.com",
  },
  {
    id: "gemini",
    displayName: "Google Gemini",
    protocol: "GEMINI",
    baseUrl: "https://generativelanguage.googleapis.com",
  },
  {
    id: "qianwen",
    displayName: "通义千问",
    protocol: "QIANWEN",
    baseUrl: "https://dashscope.aliyuncs.com",
  },
  {
    id: "ollama",
    displayName: "Ollama",
    protocol: "OLLAMA",
    baseUrl: "http://localhost:11434",
  },
];
