import type { AgentMessage } from "@pudans-dev/api-client";
import type { AIMessageContent, ToolCall } from "@tdesign-react/chat";

import type { ConversationLiveRun } from "../../composables/_conversation-run";
import type { ConversationTimelineMessage } from "./conversation-types";

export function conversationMessageFromAgent(message: AgentMessage): ConversationTimelineMessage {
    const role = normalizeRole(message.role);
    const contents = contentMapToContents(message.content, role);
    const toolCalls = toolCallsToContents(message.toolCalls);
    const assetIds = (message.assetIds ?? []).filter(Boolean);

    return {
        assetIds,
        contents: [...contents, ...toolCalls],
        createdAt: message.createdAt,
        id: message.id ?? crypto.randomUUID(),
        persisted: message,
        role,
        status: "complete",
    };
}

export function conversationMessageFromLive(run: ConversationLiveRun): ConversationTimelineMessage {
    return {
        assetIds: [],
        contents: run.contents,
        errorMessage: run.errorMessage,
        id: run.id,
        outputMessageId: run.outputMessageId,
        role: "assistant",
        status: run.status,
    };
}

export function conversationTitleFromPrompt(prompt: string): string {
    const title = prompt.replace(/\s+/g, " ").trim();

    return title.length > 30 ? `${title.slice(0, 30)}…` : title;
}

export function conversationTimeLabel(value?: string): string {
    if (!value) {
        return "";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "";
    }

    return new Intl.DateTimeFormat("zh-CN", {
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        month: "2-digit",
    }).format(date);
}

export function conversationSidebarTime(value?: string): string {
    if (!value) {
        return "";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "";
    }

    const today = new Date();
    const sameDay =
        date.getFullYear() === today.getFullYear() &&
        date.getMonth() === today.getMonth() &&
        date.getDate() === today.getDate();

    return sameDay
        ? new Intl.DateTimeFormat("zh-CN", {
              hour: "2-digit",
              minute: "2-digit",
          }).format(date)
        : new Intl.DateTimeFormat("zh-CN", {
              day: "2-digit",
              month: "2-digit",
          }).format(date);
}

export function messageText(message: ConversationTimelineMessage): string {
    return message.contents
        .flatMap((content) => {
            if (content.type === "markdown" || content.type === "text") {
                return [content.data];
            }
            return [];
        })
        .join("\n\n")
        .trim();
}

function contentMapToContents(
    content: { [key: string]: object } | undefined,
    role: ConversationTimelineMessage["role"],
): AIMessageContent[] {
    if (!content) {
        return [];
    }

    const blocks = Array.isArray(content.blocks) ? content.blocks : null;

    if (blocks) {
        return blocks.flatMap((block) => blockToContent(block, role));
    }

    return blockToContent(content, role);
}

function blockToContent(value: unknown, role: ConversationTimelineMessage["role"]): AIMessageContent[] {
    const block = objectValue(value);
    const type = textValue(block.type);
    const thinking = objectValue(block.thinking);
    const contents: AIMessageContent[] = [];

    if (Object.keys(thinking).length > 0) {
        contents.push({
            data: {
                text: textValue(thinking.text),
                title: textValue(thinking.title) || "已完成思考",
            },
            status: "complete",
            type: "thinking",
        });
    }

    if (type === "thinking" || type === "reasoning") {
        contents.push({
            data: {
                text: textValue(block.text),
                title: textValue(block.title) || "思考过程",
            },
            status: "complete",
            type: "thinking",
        });
        return contents;
    }

    if (type === "search") {
        contents.push({
            data: {
                references: arrayValue(block.references).flatMap(referenceValue),
                title: textValue(block.title) || "搜索结果",
            },
            type: "search",
        });
        return contents;
    }

    if (type === "suggestion") {
        const suggestions = arrayValue(block.suggestions ?? block.items).flatMap((item) => {
            const suggestion = objectValue(item);
            const title = textValue(suggestion.title);

            return title
                ? [
                      {
                          prompt: textValue(suggestion.prompt) || title,
                          title,
                      },
                  ]
                : [];
        });

        if (suggestions.length > 0) {
            contents.push({ data: suggestions, type: "suggestion" });
        }
        return contents;
    }

    if (type === "attachment") {
        return contents;
    }

    if (type === "image") {
        const url = textValue(block.url);

        if (url) {
            contents.push({
                data: {
                    height: numberValue(block.height),
                    name: textValue(block.name),
                    url,
                    width: numberValue(block.width),
                },
                type: "image",
            });
        }
        return contents;
    }

    if (type === "toolcall") {
        const toolCall = toolCallValue(block);

        if (toolCall) {
            contents.push({
                data: toolCall,
                ext: objectValue(block.ext),
                id: toolCall.toolCallId,
                status: "complete",
                type: "toolcall",
            });
        }
        return contents;
    }

    if (type === "activity") {
        const activityType = textValue(block.activityType);
        const activityContent = objectValue(block.content);

        if (activityType) {
            contents.push({
                data: {
                    activityType,
                    content: activityContent,
                    messageId: textValue(block.messageId),
                },
                type: "activity",
            });
        }
        return contents;
    }

    const body = textValue(block.text) || textValue(block.content);

    if (body) {
        contents.push({
            data: body,
            status: "complete",
            type: role === "assistant" ? "markdown" : "text",
        });
    }

    return contents;
}

function toolCallsToContents(toolCalls: Array<{ [key: string]: object }> | undefined): AIMessageContent[] {
    return (toolCalls ?? []).flatMap((value) => {
        const toolCall = toolCallValue(value);

        return toolCall
            ? [
                  {
                      data: toolCall,
                      ext: objectValue(value.ext),
                      id: toolCall.toolCallId,
                      status: "complete" as const,
                      type: "toolcall" as const,
                  },
              ]
            : [];
    });
}

function toolCallValue(value: unknown): ToolCall | null {
    const toolCall = objectValue(value);
    const toolCallId = textValue(toolCall.toolCallId) || textValue(toolCall.id);
    const toolCallName = textValue(toolCall.toolCallName) || textValue(toolCall.toolName) || textValue(toolCall.name);

    if (!toolCallId) {
        return null;
    }

    return {
        args: jsonText(toolCall.args ?? toolCall.input),
        result: jsonText(toolCall.result),
        toolCallId,
        toolCallName,
    };
}

function referenceValue(value: unknown) {
    const reference = objectValue(value);
    const title = textValue(reference.title);

    return title
        ? [
              {
                  content: textValue(reference.content),
                  date: textValue(reference.date),
                  icon: textValue(reference.icon),
                  site: textValue(reference.site),
                  title,
                  type: textValue(reference.type),
                  url: textValue(reference.url),
              },
          ]
        : [];
}

export function attachmentFileType(
    fileType: string,
    name: string,
): "audio" | "doc" | "image" | "pdf" | "ppt" | "txt" | "video" {
    const normalized = fileType.toLowerCase();
    const extension = name.split(".").pop()?.toLowerCase() ?? "";

    if (normalized === "image" || ["gif", "jpeg", "jpg", "png", "webp"].includes(extension)) {
        return "image";
    }
    if (normalized === "video" || ["m4v", "mov", "mp4", "webm"].includes(extension)) {
        return "video";
    }
    if (normalized === "audio" || ["m4a", "mp3", "wav"].includes(extension)) {
        return "audio";
    }
    if (normalized === "pdf" || extension === "pdf") {
        return "pdf";
    }
    if (normalized === "ppt" || ["ppt", "pptx"].includes(extension)) {
        return "ppt";
    }
    if (normalized === "txt" || ["csv", "md", "txt"].includes(extension)) {
        return "txt";
    }

    return "doc";
}

function normalizeRole(role: string | undefined): ConversationTimelineMessage["role"] {
    if (role === "user" || role === "system") {
        return role;
    }

    return "assistant";
}

function objectValue(value: unknown): Record<string, unknown> {
    return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function textValue(value: unknown): string {
    return typeof value === "string" ? value : "";
}

function numberValue(value: unknown): number | undefined {
    return typeof value === "number" ? value : undefined;
}

function arrayValue(value: unknown): unknown[] {
    return Array.isArray(value) ? value : [];
}

function jsonText(value: unknown): string | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }
    if (typeof value === "string") {
        return value;
    }

    return JSON.stringify(value);
}
