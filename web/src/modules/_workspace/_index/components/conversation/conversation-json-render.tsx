"use client";

import { JsonRenderActivityRenderer, tdesignRegistry } from "@tdesign-react/chat";

type ConversationJsonRenderProps = {
    activityType: string;
    content: Record<string, unknown>;
    messageId?: string;
};

type JsonRenderContent = Parameters<typeof JsonRenderActivityRenderer>[0]["content"];

export function ConversationJsonRender({ activityType, content, messageId }: ConversationJsonRenderProps) {
    return (
        <JsonRenderActivityRenderer
            activityType={activityType}
            content={content as unknown as JsonRenderContent}
            messageId={messageId ?? ""}
            registry={tdesignRegistry}
        />
    );
}
