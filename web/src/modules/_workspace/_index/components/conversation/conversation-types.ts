import type { AgentMessage } from "@pudans-dev/api-client";
import type { AIMessageContent, ChatMessageStatus } from "@tdesign-react/chat";

export type ConversationTimelineMessage = {
    assetIds: string[];
    contents: AIMessageContent[];
    createdAt?: string;
    errorMessage?: string;
    id: string;
    outputMessageId?: string;
    persisted?: AgentMessage;
    role: "assistant" | "system" | "user";
    status: ChatMessageStatus;
};

export type ConversationTabItem = {
    id: string;
    title: string;
};
