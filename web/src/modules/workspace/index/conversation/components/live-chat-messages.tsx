"use client";

import {
  type ChatStatus,
  getToolName,
  isDataUIPart,
  isReasoningUIPart,
  isTextUIPart,
  isToolUIPart,
} from "ai";
import { Loader2 } from "lucide-react";

import { MarkdownContent } from "@/components/content/markdown";
import { Bubble, BubbleContent } from "@/components/ui/bubble";
import { Button } from "@/components/ui/button";
import { Message, MessageContent, MessageFooter, MessageHeader } from "@/components/ui/message";
import { MessageScrollerItem } from "@/components/ui/message-scroller";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";

import type { PudansUIMessage } from "../../lib/ai-chat-transport";
import type {
  ChatArtifact,
  ChatStreamStage,
  ChatStreamUsage,
  ChatToolCall,
  ChatToolResult,
} from "../../lib/chat-api";
import { ArtifactBlock } from "./artifact-block";
import { ThinkingBlock } from "./thinking-block";
import { ToolCallsBlock } from "./tool-call-block";

type LiveChatMessagesProps = {
  messages: PudansUIMessage[];
  status: ChatStatus;
  onApproval: (approvalId: string, approved: boolean) => void;
};

type ToolApproval = {
  id: string;
  name: string;
  reason?: string;
};

type LiveParts = {
  text: string;
  reasoning: string;
  calls: ChatToolCall[];
  results: ChatToolResult[];
  approvals: ToolApproval[];
  artifacts: ChatArtifact[];
  stage?: ChatStreamStage;
  usage?: ChatStreamUsage;
};

function liveParts(message: PudansUIMessage): LiveParts {
  const parts: LiveParts = {
    text: "",
    reasoning: "",
    calls: [],
    results: [],
    approvals: [],
    artifacts: [],
  };
  const texts: string[] = [];
  const reasoning: string[] = [];

  for (const part of message.parts) {
    if (isTextUIPart(part)) {
      texts.push(part.text);
      continue;
    }
    if (isReasoningUIPart(part)) {
      reasoning.push(part.text);
      continue;
    }
    if (isDataUIPart(part)) {
      if (part.type === "data-artifact") {
        parts.artifacts.push(part.data as ChatArtifact);
      } else if (part.type === "data-stage") {
        parts.stage = part.data as ChatStreamStage;
      } else if (part.type === "data-usage") {
        parts.usage = part.data as ChatStreamUsage;
      }
      continue;
    }
    if (!isToolUIPart(part)) {
      continue;
    }

    const name = getToolName(part);
    const call: ChatToolCall = {
      id: part.toolCallId,
      name,
      arguments: "input" in part ? part.input : undefined,
    };
    if (!parts.calls.some((item) => item.id === call.id)) {
      parts.calls.push(call);
    }
    if (part.state === "output-available") {
      parts.results.push({
        id: part.toolCallId,
        name,
        output: formatToolOutput(part.output),
        isError: false,
        durationMs: 0,
      });
    } else if (part.state === "output-error") {
      parts.results.push({
        id: part.toolCallId,
        name,
        output: part.errorText,
        isError: true,
        durationMs: 0,
      });
    } else if (part.state === "output-denied") {
      parts.results.push({
        id: part.toolCallId,
        name,
        output: "工具调用已被拒绝。",
        isError: true,
        durationMs: 0,
      });
    } else if (part.state === "approval-requested") {
      parts.approvals.push({
        id: part.approval.id,
        name,
        reason: part.approval.requestReason,
      });
    }
  }

  parts.text = texts.join("");
  parts.reasoning = reasoning.join("");
  return parts;
}

function formatToolOutput(output: unknown): string {
  if (typeof output === "string") {
    return output;
  }
  try {
    return JSON.stringify(output);
  } catch {
    return String(output);
  }
}

export function LiveChatMessages({ messages, status, onApproval }: LiveChatMessagesProps) {
  return (
    <>
      {messages.map((message) => (
        <MessageScrollerItem key={message.id}>
          <LiveMessage
            message={message}
            streaming={status === "streaming" || status === "submitted"}
            onApproval={onApproval}
          />
        </MessageScrollerItem>
      ))}
    </>
  );
}

function LiveMessage({
  message,
  streaming,
  onApproval,
}: {
  message: PudansUIMessage;
  streaming: boolean;
  onApproval: (approvalId: string, approved: boolean) => void;
}) {
  const parts = liveParts(message);
  const isUser = message.role === "user";

  if (isUser) {
    return (
      <Message align="end">
        <MessageContent>
          <Bubble align="end" variant="muted">
            <BubbleContent className="whitespace-pre-wrap">{parts.text}</BubbleContent>
          </Bubble>
        </MessageContent>
      </Message>
    );
  }

  const tokenCount = parts.usage?.units ?? parts.usage?.outputTokens;

  return (
    <Message>
      <MessageContent>
        <MessageHeader>
          PUDANS
          {message.metadata?.model ? (
            <span className="ms-2 font-mono text-[10px]">{message.metadata.model}</span>
          ) : null}
          {streaming ? (
            <span className="ms-2 inline-flex items-center gap-1 text-muted-foreground">
              <Loader2 className="size-3 animate-spin" />
              生成中
            </span>
          ) : null}
        </MessageHeader>
        {parts.reasoning ? (
          <ThinkingBlock reasoning={parts.reasoning} streaming={streaming} />
        ) : null}
        {parts.calls.length > 0 || parts.results.length > 0 ? (
          <ToolCallsBlock calls={parts.calls} results={parts.results} streaming={streaming} />
        ) : null}
        {parts.approvals.map((approval) => (
          <div
            key={approval.id}
            className="flex flex-wrap items-center gap-2 border border-border bg-muted/30 px-3 py-2 text-xs"
          >
            <span className="min-w-0 flex-1">
              模型请求调用工具「{approval.name || "未知工具"}」
              {approval.reason ? `：${approval.reason}` : "。"}
            </span>
            <Button type="button" size="sm" onClick={() => onApproval(approval.id, true)}>
              允许
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => onApproval(approval.id, false)}
            >
              拒绝
            </Button>
          </div>
        ))}
        {streaming && parts.stage ? (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Loader2 className="size-3 animate-spin" />
            {parts.stage.detail || parts.stage.stage || "处理中"}
          </div>
        ) : null}
        {parts.text ? (
          <Bubble variant="ghost">
            <BubbleContent className="text-sm leading-7">
              <MarkdownContent content={parts.text} />
            </BubbleContent>
          </Bubble>
        ) : null}
        {parts.artifacts.length > 0 ? (
          <div className="w-full space-y-2">
            {parts.artifacts.map((artifact, index) => (
              <ArtifactBlock
                key={artifact.fileId ?? artifact.url ?? `${artifact.kind}-${index}`}
                artifact={artifact}
              />
            ))}
          </div>
        ) : null}
        {!streaming ? (
          <MessageFooter>
            {tokenCount != null ? (
              <span className="text-muted-foreground">{formatTokenCount(tokenCount)} token</span>
            ) : null}
          </MessageFooter>
        ) : null}
      </MessageContent>
    </Message>
  );
}
