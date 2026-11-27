import type { AgentRun, AgentRunEvent } from "@pudans-dev/api-client";
import type { AIMessageContent, ChatMessageStatus, ToolCall } from "@tdesign-react/chat";

export type ConversationLiveRun = {
    contents: AIMessageContent[];
    errorMessage?: string;
    id: string;
    outputMessageId?: string;
    runId: string;
    sessionId: string;
    status: ChatMessageStatus;
};

export function createConversationLiveRun(run: AgentRun): ConversationLiveRun {
    if (!run.id || !run.sessionId) {
        throw new Error("运行响应缺少会话或运行标识。");
    }

    return {
        contents: [],
        id: `live-${run.id}`,
        runId: run.id,
        sessionId: run.sessionId,
        status: "pending",
    };
}

export function applyConversationRunEvent(current: ConversationLiveRun, event: AgentRunEvent): ConversationLiveRun {
    const data = eventData(event);

    switch (event.type) {
        case "run.started":
            return { ...current, status: "streaming" };
        case "message.start":
            return {
                ...current,
                contents: ensureTextBlock(current.contents, text(data.blockId)),
                status: "streaming",
            };
        case "message.delta":
            return {
                ...current,
                contents: appendText(current.contents, text(data.blockId), text(data.delta)),
                status: "streaming",
            };
        case "reasoning.delta":
            return {
                ...current,
                contents: appendThinking(current.contents, text(data.blockId), text(data.delta)),
                status: "streaming",
            };
        case "tool.start":
            return {
                ...current,
                contents: upsertToolCall(current.contents, {
                    state: "started",
                    toolCallId: text(data.toolCallId),
                    toolCallName: text(data.toolName),
                }),
                status: "streaming",
            };
        case "tool.approval_required":
            return {
                ...current,
                contents: upsertToolCall(current.contents, approvalToolCall(data.toolCall)),
                status: "streaming",
            };
        case "tool.result":
            return {
                ...current,
                contents: updateToolCall(current.contents, text(data.toolCallId), {
                    result: text(data.state),
                    toolCallName: text(data.toolName),
                    state: text(data.state),
                }),
                status: "streaming",
            };
        case "run.completed":
            return {
                ...current,
                contents: completeContents(ensureCompletedText(current.contents, text(data.blockId), text(data.text))),
                outputMessageId: text(data.messageId) || undefined,
                status: "complete",
            };
        case "run.failed":
            return {
                ...current,
                errorMessage: text(data.errorMessage) || "Agent 运行失败。",
                status: "error",
            };
        case "run.cancelled":
            return {
                ...current,
                contents: stopContents(current.contents),
                status: "stop",
            };
        default:
            return current;
    }
}

export function applyConversationApprovalDecision(
    current: ConversationLiveRun,
    toolCallId: string,
    decision: "approve" | "reject",
): ConversationLiveRun {
    return {
        ...current,
        contents: current.contents.map((content) =>
            content.type === "toolcall" &&
            content.data.toolCallId === toolCallId &&
            objectValue(content.ext).state === "pending"
                ? {
                      ...content,
                      ext: {
                          ...content.ext,
                          state: decision === "approve" ? "approved" : "rejected",
                      },
                  }
                : content,
        ),
    };
}

function ensureTextBlock(contents: AIMessageContent[], blockId: string): AIMessageContent[] {
    if (!blockId || contents.some((content) => content.id === blockId)) {
        return contents;
    }

    return [
        ...contents,
        {
            data: "",
            id: blockId,
            status: "streaming",
            type: "markdown",
        },
    ];
}

function appendText(contents: AIMessageContent[], blockId: string, delta: string): AIMessageContent[] {
    if (!delta) {
        return contents;
    }

    const prepared = ensureTextBlock(contents, blockId);
    const index = prepared.findIndex((content) => content.id === blockId);

    return prepared.map((content, contentIndex) =>
        contentIndex === index && content.type === "markdown"
            ? {
                  ...content,
                  data: `${content.data}${delta}`,
                  status: "streaming",
              }
            : content,
    );
}

function appendThinking(contents: AIMessageContent[], blockId: string, delta: string): AIMessageContent[] {
    if (!delta) {
        return contents;
    }

    const index = contents.findIndex((content) => content.id === blockId);

    if (index < 0) {
        return [
            ...contents,
            {
                data: { text: delta, title: "正在思考" },
                id: blockId,
                status: "streaming",
                type: "thinking",
            },
        ];
    }

    return contents.map((content, contentIndex) =>
        contentIndex === index && content.type === "thinking"
            ? {
                  ...content,
                  data: {
                      ...content.data,
                      text: `${content.data.text ?? ""}${delta}`,
                  },
                  status: "streaming",
              }
            : content,
    );
}

type ToolCallInput = Partial<ToolCall> & { approvalId?: string; state?: string };

function upsertToolCall(contents: AIMessageContent[], input: ToolCallInput): AIMessageContent[] {
    const toolCallId = input.toolCallId ?? "";

    if (!toolCallId) {
        return contents;
    }

    const index = contents.findIndex(
        (content) => content.type === "toolcall" && content.data.toolCallId === toolCallId,
    );
    const { approvalId, state, ...toolCallInput } = input;
    const toolCall: ToolCall = {
        toolCallId,
        toolCallName: input.toolCallName ?? "",
        ...toolCallInput,
    };

    if (index < 0) {
        return [
            ...contents,
            {
                data: toolCall,
                ext: toolCallExt(approvalId, state),
                id: `tool-${toolCallId}`,
                status: "streaming",
                type: "toolcall",
            },
        ];
    }

    return contents.map((content, contentIndex) =>
        contentIndex === index && content.type === "toolcall"
            ? {
                  ...content,
                  data: { ...content.data, ...toolCall },
                  ext: {
                      ...content.ext,
                      ...toolCallExt(approvalId, state),
                  },
                  status: "streaming",
              }
            : content,
    );
}

function updateToolCall(
    contents: AIMessageContent[],
    toolCallId: string,
    patch: Partial<ToolCall> & { state?: string },
): AIMessageContent[] {
    if (!toolCallId) {
        return contents;
    }

    const { state, ...toolPatch } = patch;

    return contents.map((content) =>
        content.type === "toolcall" && content.data.toolCallId === toolCallId
            ? {
                  ...content,
                  data: { ...content.data, ...toolPatch },
                  ext: {
                      ...content.ext,
                      ...(state ? { state } : {}),
                  },
                  status: "complete",
              }
            : content,
    );
}

function approvalToolCall(value: unknown): ToolCallInput {
    const input = objectValue(value);

    return {
        approvalId: text(input.approvalId),
        args: jsonValue(input.input),
        state: text(input.state) || "pending",
        toolCallId: text(input.id),
        toolCallName: text(input.name),
    };
}

function ensureCompletedText(contents: AIMessageContent[], blockId: string, fallbackText: string): AIMessageContent[] {
    const hasText = contents.some(
        (content) => (content.type === "markdown" || content.type === "text") && content.data.trim().length > 0,
    );

    if (hasText || !fallbackText) {
        return contents;
    }

    return [
        ...contents,
        {
            data: fallbackText,
            id: blockId || "completed-text",
            status: "complete",
            type: "markdown",
        },
    ];
}

function toolCallExt(approvalId?: string, state?: string): Record<string, string> | undefined {
    const ext: Record<string, string> = {};

    if (approvalId) {
        ext.approvalId = approvalId;
    }
    if (state) {
        ext.state = state;
    }

    return Object.keys(ext).length > 0 ? ext : undefined;
}

function completeContents(contents: AIMessageContent[]): AIMessageContent[] {
    return contents.map((content) => {
        if (content.type === "thinking") {
            return {
                ...content,
                data: { ...content.data, title: "已完成思考" },
                status: "complete" as const,
            };
        }
        return { ...content, status: "complete" as const };
    });
}

function stopContents(contents: AIMessageContent[]): AIMessageContent[] {
    return contents.map((content) => ({ ...content, status: "stop" as const }));
}

function eventData(event: AgentRunEvent): Record<string, unknown> {
    return objectValue(event.data);
}

function objectValue(value: unknown): Record<string, unknown> {
    return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function text(value: unknown): string {
    return typeof value === "string" ? value : "";
}

function jsonValue(value: unknown): string | undefined {
    return value === undefined ? undefined : JSON.stringify(value);
}
