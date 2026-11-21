import type { AiSdkChat } from "@shadcn/helpers/ai-sdk";
import type { ChatTransport, UIMessage, UIMessageChunk } from "ai";

import { getCookie } from "@/lib/cookies";
import { type SseEvent, readSseEvents } from "@/lib/sse";

import {
  CHAT_COMPLETIONS,
  type ChatArtifact,
  type ChatMessage,
  type ChatRequest,
  type ChatStreamDone,
  type ChatStreamError,
  type ChatStreamMeta,
  type ChatStreamStage,
  type ChatStreamUsage,
  type ChatToolCall,
  type ChatToolConfirm,
  type ChatToolResult,
  readChatResponseError,
} from "./chat-api";

type PudansChatMetadata = {
  conversationId?: number;
  requestId?: string;
  mode?: string;
  routeGroup?: string;
  provider?: string;
  account?: string;
  model?: string;
  tier?: string;
  teamId?: string;
  agentId?: string;
  sessionKey?: string;
  usage?: ChatStreamUsage;
  finishReason?: string;
  partial?: boolean;
};

type PudansChatData = {
  artifact: ChatArtifact;
  stage: ChatStreamStage;
  usage: ChatStreamUsage;
};

export type PudansUIMessage = UIMessage<PudansChatMetadata, PudansChatData>;

type PudansChunk = UIMessageChunk<PudansChatMetadata, PudansChatData>;

type PudansChat = AiSdkChat<PudansUIMessage>;

type PudansChatTransportContract = ReturnType<PudansChat["transport"]>;

export type PudansChatTransportOptions = {
  conversationId: number;
  mode?: string;
  routeGroup?: string;
  agentId?: string;
  kbCodes?: string[];
  model?: string;
};

type ToolExternal = {
  toolCallId?: string;
  name?: string;
  arguments?: string;
};

type ToolExternalResult = {
  toolCallId?: string;
  output?: string;
  isError?: boolean;
};

export class PudansChatTransport implements PudansChatTransportContract {
  readonly resumeStreamIsReplay = false;

  private readonly options: PudansChatTransportOptions;

  private readonly runIds = new Map<string, string>();

  private currentRunId: string | null = null;

  constructor(options: PudansChatTransportOptions) {
    this.options = options;
  }

  runIdFor(toolCallId: string): string | undefined {
    return this.runIds.get(toolCallId);
  }

  get runId(): string | null {
    return this.currentRunId;
  }

  async sendMessages({
    abortSignal,
    body,
    messages,
    trigger,
  }: Parameters<ChatTransport<PudansUIMessage>["sendMessages"]>[0]): Promise<
    ReadableStream<PudansChunk>
  > {
    const extra = (body ?? {}) as Partial<ChatRequest>;
    const request: ChatRequest = {
      conversationId: this.options.conversationId,
      mode: extra.mode ?? this.options.mode,
      routeGroup: extra.routeGroup ?? this.options.routeGroup,
      agentId: extra.agentId ?? this.options.agentId,
      kbCodes: extra.kbCodes ?? this.options.kbCodes,
      model: extra.model ?? this.options.model,
      messages: lastUserMessage(messages),
      attachments: extra.attachments,
      command: extra.command,
      commandInput: extra.commandInput,
      regenerate: trigger === "regenerate-message" ? true : extra.regenerate,
      idempotencyKey: extra.idempotencyKey ?? crypto.randomUUID(),
    };
    const csrfToken = getCookie("XSRF-TOKEN");
    const response = await fetch(CHAT_COMPLETIONS, {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
        "X-Requested-With": "XMLHttpRequest",
        ...(csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {}),
      },
      body: JSON.stringify(request),
      signal: abortSignal,
    });
    if (!response.ok) {
      throw new Error(await readChatResponseError(response));
    }
    if (!response.body) {
      throw new Error("网关没有返回流式响应。");
    }
    return toUiMessageStream(response.body, this.runIds, (runId) => {
      this.currentRunId = runId;
    });
  }

  async reconnectToStream(): Promise<ReadableStream<PudansChunk> | null> {
    return null;
  }
}

function messageText(message: PudansUIMessage): string {
  return message.parts
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("");
}

function lastUserMessage(messages: PudansUIMessage[]): ChatRequest["messages"] {
  const lastUser = [...messages].reverse().find((message) => message.role === "user");
  if (!lastUser) {
    return [];
  }
  const content = messageText(lastUser);
  return content.trim() ? [{ role: "user", content }] : [];
}

function parsePayload<T>(event: SseEvent): T | null {
  try {
    return JSON.parse(event.data) as T;
  } catch {
    return null;
  }
}

function parseToolInput(value: string | undefined): unknown {
  if (!value) {
    return {};
  }
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function toMetadata(meta: ChatStreamMeta): PudansChatMetadata {
  return {
    conversationId: meta.conversationId,
    requestId: meta.requestId,
    mode: meta.mode,
    routeGroup: meta.routeGroup,
    provider: meta.provider,
    account: meta.account,
    model: meta.model,
    tier: meta.tier,
    teamId: meta.teamId,
    agentId: meta.agentId,
    sessionKey: meta.sessionKey,
  };
}

function toUiMessageStream(
  stream: ReadableStream<Uint8Array>,
  runIds: Map<string, string>,
  onRunStarted: (runId: string) => void,
): ReadableStream<PudansChunk> {
  return new ReadableStream<PudansChunk>({
    async start(controller) {
      let started = false;
      let finished = false;
      let textId: string | null = null;
      let reasoningId: string | null = null;
      let metadata: PudansChatMetadata = {};

      const start = () => {
        if (started) {
          return;
        }
        started = true;
        controller.enqueue({
          type: "start",
          messageId: crypto.randomUUID(),
          messageMetadata: metadata,
        });
      };

      const finish = () => {
        if (finished) {
          return;
        }
        finished = true;
        if (reasoningId) {
          controller.enqueue({ type: "reasoning-end", id: reasoningId });
        }
        if (textId) {
          controller.enqueue({ type: "text-end", id: textId });
        }
        controller.enqueue({ type: "finish", messageMetadata: metadata });
      };

      try {
        for await (const event of readSseEvents(stream)) {
          switch (event.event) {
            case "meta": {
              const meta = parsePayload<ChatStreamMeta>(event);
              if (!meta) {
                break;
              }
              metadata = { ...metadata, ...toMetadata(meta) };
              start();
              controller.enqueue({ type: "message-metadata", messageMetadata: metadata });
              break;
            }
            case "run_started": {
              const run = parsePayload<{ runId?: string }>(event);
              if (run?.runId) {
                onRunStarted(run.runId);
              }
              start();
              break;
            }
            case "delta": {
              const delta = parsePayload<{ content?: string; reasoning?: string }>(event);
              if (!delta) {
                break;
              }
              start();
              if (delta.reasoning) {
                if (!reasoningId) {
                  reasoningId = crypto.randomUUID();
                  controller.enqueue({ type: "reasoning-start", id: reasoningId });
                }
                controller.enqueue({
                  type: "reasoning-delta",
                  id: reasoningId,
                  delta: delta.reasoning,
                });
              }
              if (delta.content) {
                if (!textId) {
                  textId = crypto.randomUUID();
                  controller.enqueue({ type: "text-start", id: textId });
                }
                controller.enqueue({ type: "text-delta", id: textId, delta: delta.content });
              }
              break;
            }
            case "tool_call": {
              const call = parsePayload<ChatToolCall>(event);
              if (!call) {
                break;
              }
              start();
              controller.enqueue({
                type: "tool-input-start",
                toolCallId: call.id,
                toolName: call.name,
                dynamic: true,
              });
              controller.enqueue({
                type: "tool-input-available",
                toolCallId: call.id,
                toolName: call.name,
                input: call.arguments ?? {},
                dynamic: true,
              });
              break;
            }
            case "tool_result": {
              const result = parsePayload<ChatToolResult>(event);
              if (!result) {
                break;
              }
              if (result.isError) {
                controller.enqueue({
                  type: "tool-output-error",
                  toolCallId: result.id,
                  errorText: result.output,
                  dynamic: true,
                });
              } else {
                controller.enqueue({
                  type: "tool-output-available",
                  toolCallId: result.id,
                  output: result.output,
                  dynamic: true,
                });
              }
              break;
            }
            case "tool_confirm": {
              const confirm = parsePayload<ChatToolConfirm>(event);
              if (!confirm?.toolCallId) {
                break;
              }
              if (confirm.runId) {
                runIds.set(confirm.toolCallId, confirm.runId);
              }
              controller.enqueue({
                type: "tool-approval-request",
                approvalId: confirm.toolCallId,
                toolCallId: confirm.toolCallId,
                reason: confirm.message,
                approvalDescriptor: { name: confirm.name, runId: confirm.runId },
              });
              break;
            }
            case "tool_external": {
              const external = parsePayload<ToolExternal>(event);
              if (!external?.toolCallId || !external.name) {
                break;
              }
              start();
              controller.enqueue({
                type: "tool-input-start",
                toolCallId: external.toolCallId,
                toolName: external.name,
                dynamic: true,
              });
              controller.enqueue({
                type: "tool-input-available",
                toolCallId: external.toolCallId,
                toolName: external.name,
                input: parseToolInput(external.arguments),
                dynamic: true,
              });
              break;
            }
            case "tool_external_result": {
              const result = parsePayload<ToolExternalResult>(event);
              if (!result?.toolCallId) {
                break;
              }
              if (result.isError) {
                controller.enqueue({
                  type: "tool-output-error",
                  toolCallId: result.toolCallId,
                  errorText: result.output ?? "工具调用失败。",
                  dynamic: true,
                });
              } else {
                controller.enqueue({
                  type: "tool-output-available",
                  toolCallId: result.toolCallId,
                  output: result.output ?? "",
                  dynamic: true,
                });
              }
              break;
            }
            case "artifact": {
              const artifact = parsePayload<ChatArtifact>(event);
              if (!artifact) {
                break;
              }
              start();
              controller.enqueue({
                type: "data-artifact",
                id: artifact.fileId ?? artifact.url ?? crypto.randomUUID(),
                data: artifact,
              });
              break;
            }
            case "stage": {
              const stage = parsePayload<ChatStreamStage>(event);
              if (!stage) {
                break;
              }
              controller.enqueue({ type: "data-stage", id: "stage", data: stage });
              break;
            }
            case "usage": {
              const usage = parsePayload<ChatStreamUsage>(event);
              if (!usage) {
                break;
              }
              metadata = { ...metadata, usage };
              controller.enqueue({ type: "data-usage", id: "usage", data: usage });
              break;
            }
            case "done": {
              const done = parsePayload<ChatStreamDone>(event);
              if (done) {
                metadata = {
                  ...metadata,
                  finishReason: done.finishReason,
                  partial: done.partial,
                };
              }
              start();
              finish();
              break;
            }
            case "error": {
              const error = parsePayload<ChatStreamError>(event);
              controller.enqueue({
                type: "error",
                errorText: error?.message ?? "生成失败，请稍后重试。",
              });
              finish();
              break;
            }
            default:
              break;
          }
        }
        start();
        finish();
        controller.close();
      } catch (error) {
        controller.error(error);
      }
    },
  });
}

export function toUiMessages(messages: ChatMessage[]): PudansUIMessage[] {
  return messages.map((message) => {
    const parts: PudansUIMessage["parts"] = [];
    if (message.content) {
      parts.push({ type: "text", text: message.content });
    }
    if (message.metadata?.reasoning) {
      parts.push({ type: "reasoning", text: message.metadata.reasoning });
    }
    if (message.metadata?.artifact) {
      const artifact = message.metadata.artifact;
      parts.push({
        type: "data-artifact",
        id: artifact.fileId ?? artifact.url ?? String(message.id),
        data: artifact,
      });
    }
    return {
      id: String(message.id),
      role: message.role === "user" ? "user" : message.role === "system" ? "system" : "assistant",
      parts,
      metadata: { model: message.modelCode },
    };
  });
}
