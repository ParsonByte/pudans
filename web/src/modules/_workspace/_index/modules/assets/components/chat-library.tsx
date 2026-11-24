"use client";

import { useState } from "react";

import { BookmarkIcon, ChatBubbleIcon, ChatIcon, HistoryIcon, SearchErrorIcon } from "tdesign-icons-react";

import styles from "../styles/assets-view.module.css";
import { LibraryEmptyState } from "./library-empty-state";
import { LibraryHeader } from "./library-header";
import { LibraryToolbar } from "./library-toolbar";

const CHAT_FILTERS = ["全部", "最近", "收藏"] as const;

type ChatFilter = (typeof CHAT_FILTERS)[number];

const CHAT_GUIDES = [
    {
        description: "自动归档 DANS 与创作过程中的对话",
        icon: <HistoryIcon size={18} />,
        title: "对话归档",
    },
    {
        description: "从历史对话继续追问和生成",
        icon: <ChatIcon size={18} />,
        title: "延续上下文",
    },
    {
        description: "收藏重要对话，按关键词快速检索",
        icon: <BookmarkIcon size={18} />,
        title: "收藏与检索",
    },
];

export function ChatLibrary() {
    const [activeFilter, setActiveFilter] = useState<ChatFilter>(CHAT_FILTERS[0]);
    const [keyword, setKeyword] = useState("");
    const filtered = activeFilter !== CHAT_FILTERS[0] || keyword.trim().length > 0;
    const resetFilters = () => {
        setActiveFilter(CHAT_FILTERS[0]);
        setKeyword("");
    };

    return (
        <section aria-labelledby="chat-library-title" className={styles.library}>
            <LibraryHeader
                description="DANS 与创作过程中的对话会自动归档，方便继续上次的上下文。"
                icon={<ChatIcon size={20} />}
                id="chat-library-title"
                pendingLabel="会话同步接口待接入"
                title="聊天记录"
                tone="violet"
            />

            <LibraryToolbar
                filterLabel="聊天分类"
                filterOptions={CHAT_FILTERS}
                filterValue={activeFilter}
                keyword={keyword}
                onFilterChange={setActiveFilter}
                onKeywordChange={setKeyword}
                searchPlaceholder="搜索聊天内容"
            />

            {filtered ? (
                <LibraryEmptyState
                    actionLabel="清空筛选"
                    description="当前分类或关键词下还没有聊天，清空筛选后可以重新查看。"
                    icon={<SearchErrorIcon size={28} />}
                    onAction={resetFilters}
                    title="没有找到匹配的聊天"
                />
            ) : (
                <LibraryEmptyState
                    description="当 DANS 或创作任务产生对话后，会话会自动进入这里，无需手动保存。"
                    icon={<ChatBubbleIcon size={30} />}
                    iconTone="violet"
                    title="对话会自动沉淀在这里"
                >
                    <div className={styles.guideGrid}>
                        {CHAT_GUIDES.map((guide) => (
                            <div className={styles.guideItem} key={guide.title}>
                                <span className={styles.guideIcon}>{guide.icon}</span>
                                <span className={styles.guideCopy}>
                                    <strong>{guide.title}</strong>
                                    <small>{guide.description}</small>
                                </span>
                            </div>
                        ))}
                    </div>
                </LibraryEmptyState>
            )}
        </section>
    );
}
