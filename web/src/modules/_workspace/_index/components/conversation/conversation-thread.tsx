"use client";

import { useLayoutEffect, useRef } from "react";

import dynamic from "next/dynamic";

import type { ActivityContent } from "@tdesign-react/chat";
import { Button, Empty } from "tdesign-react";

import { ConversationEmpty } from "./conversation-empty";
import { ConversationMessage, type ConversationViewer } from "./conversation-message";
import type { ConversationTimelineMessage } from "./conversation-types";
import { messageText } from "./conversation-utils";
import styles from "./conversation.module.css";

const ChatLoading = dynamic(() => import("@tdesign-react/chat").then((module) => module.ChatLoading), { ssr: false });

type ConversationThreadProps = {
    activeSessionId: string | null;
    errorMessage?: string;
    hasOlder: boolean;
    isFetchingOlder: boolean;
    isLoading: boolean;
    messages: ConversationTimelineMessage[];
    onApproval: (approvalId: string, toolCallId: string, decision: "approve" | "reject") => Promise<void>;
    onLoadOlder: () => Promise<unknown>;
    onOpenActivity: (content: ActivityContent) => void;
    onReplay: (prompt: string) => void;
    onRetry: () => void;
    onSendPrompt: (prompt: string) => void;
    viewer: ConversationViewer;
};

export function ConversationThread({
    activeSessionId,
    errorMessage,
    hasOlder,
    isFetchingOlder,
    isLoading,
    messages,
    onApproval,
    onLoadOlder,
    onOpenActivity,
    onReplay,
    onRetry,
    onSendPrompt,
    viewer,
}: ConversationThreadProps) {
    const scrollRef = useRef<HTMLDivElement>(null);
    const nearBottomRef = useRef(true);
    const initializedSessionRef = useRef<string | null>(null);
    const prependHeightRef = useRef<number | null>(null);
    const loadingOlderRef = useRef(false);
    const streamSignature = messages
        .map((message) => `${message.id}:${message.status}:${message.contents.map(contentSignature).join(",")}`)
        .join("|");

    useLayoutEffect(() => {
        const element = scrollRef.current;

        if (!element) {
            return;
        }
        if (initializedSessionRef.current !== activeSessionId) {
            element.scrollTop = element.scrollHeight;
            initializedSessionRef.current = activeSessionId;
            nearBottomRef.current = true;
            prependHeightRef.current = null;
            return;
        }
        if (prependHeightRef.current !== null) {
            element.scrollTop += element.scrollHeight - prependHeightRef.current;
            prependHeightRef.current = null;
            return;
        }
        if (nearBottomRef.current) {
            element.scrollTop = element.scrollHeight;
        }
    }, [activeSessionId, messages.length, streamSignature]);

    const loadOlder = () => {
        const element = scrollRef.current;

        if (!element || loadingOlderRef.current) {
            return;
        }

        loadingOlderRef.current = true;
        prependHeightRef.current = element.scrollHeight;
        void onLoadOlder()
            .catch(() => {
                prependHeightRef.current = null;
            })
            .finally(() => {
                loadingOlderRef.current = false;
            });
    };

    const handleScroll = () => {
        const element = scrollRef.current;

        if (!element) {
            return;
        }

        nearBottomRef.current = element.scrollHeight - element.scrollTop - element.clientHeight < 96;

        if (element.scrollTop < 72 && hasOlder && !isFetchingOlder) {
            loadOlder();
        }
    };

    if (isLoading && messages.length === 0) {
        return (
            <div className={styles.thread}>
                <div className={styles.threadState}>
                    <ChatLoading animation="moving" text="正在加载会话..." />
                </div>
            </div>
        );
    }

    if (errorMessage) {
        return (
            <div className={styles.thread}>
                <div className={styles.threadState}>
                    <Empty
                        action={
                            <Button onClick={onRetry} theme="primary">
                                重试
                            </Button>
                        }
                        description={errorMessage}
                    />
                </div>
            </div>
        );
    }

    return (
        <div
            aria-label="会话消息"
            aria-live="polite"
            className={styles.thread}
            onScroll={handleScroll}
            ref={scrollRef}
            role="log"
        >
            {messages.length === 0 ? (
                <ConversationEmpty hasSession={Boolean(activeSessionId)} />
            ) : (
                <div className={styles.threadInner}>
                    {hasOlder ? (
                        <div className={styles.loadArea}>
                            <Button loading={isFetchingOlder} onClick={loadOlder} size="small" variant="text">
                                加载更早消息
                            </Button>
                        </div>
                    ) : null}
                    {messages.map((message, index) => {
                        const previousPrompt = previousUserPrompt(messages, index);

                        return (
                            <ConversationMessage
                                canReplay={
                                    message.role === "assistant" &&
                                    message.status === "complete" &&
                                    Boolean(previousPrompt)
                                }
                                key={message.id}
                                message={message}
                                onApproval={onApproval}
                                onOpenActivity={onOpenActivity}
                                onReplay={() => {
                                    if (previousPrompt) {
                                        onReplay(previousPrompt);
                                    }
                                }}
                                onSendPrompt={onSendPrompt}
                                viewer={viewer}
                            />
                        );
                    })}
                </div>
            )}
        </div>
    );
}

function contentSignature(content: ConversationTimelineMessage["contents"][number]): string {
    if (content.type === "markdown" || content.type === "text") {
        return String(content.data.length);
    }
    if (content.type === "thinking") {
        return String(content.data.text?.length ?? 0);
    }
    if (content.type === "toolcall") {
        return `${content.data.toolCallId}:${content.data.result ?? ""}`;
    }

    return "";
}

function previousUserPrompt(messages: ConversationTimelineMessage[], index: number): string {
    for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
        const message = messages[cursor];

        if (message.role === "user") {
            return messageText(message);
        }
    }

    return "";
}
