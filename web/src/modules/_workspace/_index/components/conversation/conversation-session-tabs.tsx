"use client";

import { AddIcon, CloseIcon } from "tdesign-icons-react";
import { Loading } from "tdesign-react";

import type { ConversationTabItem } from "./conversation-types";
import styles from "./conversation.module.css";

type ConversationSessionTabsProps = {
    activeSessionId: string | null;
    creating: boolean;
    onCreate: () => void;
    onClose: (sessionId: string) => void;
    onSelect: (sessionId: string) => void;
    sessions: ConversationTabItem[];
};

export function ConversationSessionTabs({
    activeSessionId,
    creating,
    onCreate,
    onClose,
    onSelect,
    sessions,
}: ConversationSessionTabsProps) {
    if (sessions.length === 0) {
        return null;
    }

    return (
        <div aria-label="已打开的会话" className={styles.tabs} role="tablist">
            {sessions.map((session) => {
                const active = session.id === activeSessionId;

                return (
                    <div
                        className={`${styles.tab} ${active ? styles.tabActive : ""}`}
                        key={session.id}
                        role="presentation"
                    >
                        <button
                            aria-label={`切换到会话：${session.title}`}
                            aria-selected={active}
                            className={styles.tabLabel}
                            onClick={() => onSelect(session.id)}
                            role="tab"
                            title={session.title}
                            type="button"
                        >
                            {session.title}
                        </button>
                        <button
                            aria-label={`关闭会话标签：${session.title}`}
                            className={styles.tabClose}
                            onClick={() => onClose(session.id)}
                            title="关闭"
                            type="button"
                        >
                            <CloseIcon size={14} />
                        </button>
                    </div>
                );
            })}
            <button
                aria-label="新建会话"
                className={styles.tabAdd}
                disabled={creating}
                onClick={onCreate}
                title="新建会话"
                type="button"
            >
                {creating ? <Loading size="14px" /> : <AddIcon size={18} />}
            </button>
        </div>
    );
}
