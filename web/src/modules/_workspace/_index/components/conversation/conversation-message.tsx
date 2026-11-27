"use client";

import dynamic from "next/dynamic";

import type { ActivityContent, TdChatMessageActionHandlers } from "@tdesign-react/chat";
import { CircleAlert, Sparkles } from "lucide-react";

import { ConversationActivityInline } from "./conversation-activity-inline";
import { ConversationAttachments } from "./conversation-attachments";
import { ConversationMessageActions } from "./conversation-message-actions";
import { ConversationToolCall } from "./conversation-tool-call";
import type { ConversationTimelineMessage } from "./conversation-types";
import { conversationTimeLabel, messageText } from "./conversation-utils";
import styles from "./conversation.module.css";

const ChatMessage = dynamic(() => import("@tdesign-react/chat").then((module) => module.ChatMessage), { ssr: false });

export type ConversationViewer = {
    avatar?: string;
    name: string;
};

type ConversationMessageProps = {
    canReplay: boolean;
    message: ConversationTimelineMessage;
    onApproval: (approvalId: string, toolCallId: string, decision: "approve" | "reject") => Promise<void>;
    onOpenActivity: (content: ActivityContent) => void;
    onReplay: () => void;
    onSendPrompt: (prompt: string) => void;
    viewer: ConversationViewer;
};

export function ConversationMessage({
    canReplay,
    message,
    onApproval,
    onOpenActivity,
    onReplay,
    onSendPrompt,
    viewer,
}: ConversationMessageProps) {
    const assistant = message.role === "assistant";
    const user = message.role === "user";
    const copyText = assistant ? messageText(message) : "";
    const displayContents =
        assistant && message.status === "error" && message.contents.length === 0 && message.errorMessage
            ? [{ data: message.errorMessage, type: "text" as const }]
            : message.contents;
    const handleActions: TdChatMessageActionHandlers = {
        searchItem: (data) => {
            if (data.content.url) {
                window.open(data.content.url, "_blank", "noopener,noreferrer");
            }
        },
        suggestion: (data) => {
            onSendPrompt(data.content.prompt || data.content.title);
        },
    };

    return (
        <div className={`${styles.message} ${user ? styles.messageUser : ""}`}>
            <ChatMessage
                actions={false}
                animation="moving"
                avatar={
                    assistant ? (
                        <span className={styles.assistantAvatar}>
                            <Sparkles size={16} />
                        </span>
                    ) : user ? (
                        viewer.avatar
                    ) : undefined
                }
                chatContentProps={{
                    search: { collapsed: false, useCollapse: true },
                    suggestion: { directSend: false },
                    thinking: { animation: "moving", collapsed: true, layout: "block", maxHeight: 220 },
                }}
                content={displayContents}
                datetime={conversationTimeLabel(message.createdAt)}
                handleActions={handleActions}
                id={message.id}
                name={assistant ? "Pudans AI" : user ? viewer.name : undefined}
                placement={user ? "right" : "left"}
                role={message.role}
                status={message.status}
                variant={user ? "base" : "text"}
            >
                {assistant
                    ? message.contents.map((content, index) => {
                          if (content.type === "toolcall") {
                              return (
                                  <div key={`${message.id}-tool-${index}`} slot={`toolcall-${index}`}>
                                      <ConversationToolCall content={content} onApproval={onApproval} />
                                  </div>
                              );
                          }

                          if (content.type === "activity") {
                              return (
                                  <div key={`${message.id}-activity-${index}`} slot={`activity-${index}`}>
                                      <ConversationActivityInline
                                          content={content}
                                          onOpen={() => onOpenActivity(content)}
                                      />
                                  </div>
                              );
                          }

                          return null;
                      })
                    : null}
                {assistant && copyText && (message.status === "complete" || message.status === "stop") ? (
                    <div slot="actionbar">
                        <ConversationMessageActions canReplay={canReplay} onReplay={onReplay} text={copyText} />
                    </div>
                ) : null}
            </ChatMessage>
            {message.assetIds.length > 0 ? (
                <div className={`${styles.attachmentBlock} ${user ? styles.attachmentBlockUser : ""}`}>
                    <ConversationAttachments assetIds={message.assetIds} />
                </div>
            ) : null}
            {message.status === "error" && message.errorMessage && message.contents.length > 0 ? (
                <div className={styles.errorNotice} role="alert">
                    <CircleAlert aria-hidden size={15} />
                    <span>{message.errorMessage}</span>
                </div>
            ) : null}
        </div>
    );
}
