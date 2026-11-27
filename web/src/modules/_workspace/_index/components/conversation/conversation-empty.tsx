"use client";

import { ChatBubbleSmileIcon } from "tdesign-icons-react";

import styles from "./conversation.module.css";

type ConversationEmptyProps = {
    hasSession: boolean;
};

export function ConversationEmpty({ hasSession }: ConversationEmptyProps) {
    return (
        <div className={styles.empty}>
            <div className={styles.emptyIcon}>
                <ChatBubbleSmileIcon size={28} />
            </div>
            <h2 className={styles.emptyTitle}>{hasSession ? "开始这段对话" : "开始一段新对话"}</h2>
            <p className={styles.emptyDescription}>
                {hasSession
                    ? "输入消息或上传图片、文档，Pudans AI 会根据当前工作区的能力真实处理。"
                    : "输入消息后会自动创建会话；也可以在左侧选择已有会话继续。"}
            </p>
        </div>
    );
}
