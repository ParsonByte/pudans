"use client";

import { type ReactNode, useState } from "react";

import { FolderOpen, Layers, Monitor, Upload } from "lucide-react";
import { Button, Empty, Input } from "tdesign-react";

import styles from "../../styles/ai-chat-composer.module.css";
import type { AiChatComposerAddAction } from "./chat-input-types";

type AddActionDefinition = {
    action: AiChatComposerAddAction;
    description?: string;
    icon: ReactNode;
    section: "add" | "device";
    title: string;
};

const ADD_ACTIONS: readonly AddActionDefinition[] = [
    {
        action: "upload",
        icon: <Upload size={18} strokeWidth={1.8} />,
        section: "add",
        title: "上传文件或图片",
    },
    {
        action: "library",
        icon: <FolderOpen size={18} strokeWidth={1.8} />,
        section: "add",
        title: "选择资料库文件",
    },
    {
        action: "create-material",
        description: "使用创建的素材、历史生成的图片和视频",
        icon: <Layers size={18} strokeWidth={1.8} />,
        section: "add",
        title: "创作资料",
    },
    {
        action: "cloud-computer",
        icon: <Monitor size={18} strokeWidth={1.8} />,
        section: "device",
        title: "云电脑",
    },
];

type ChatInputAddPanelProps = {
    onSelect: (action: AiChatComposerAddAction) => void;
};

function matchesQuery(action: AddActionDefinition, query: string): boolean {
    const normalizedQuery = query.trim().toLocaleLowerCase();

    return (
        !normalizedQuery || `${action.title} ${action.description ?? ""}`.toLocaleLowerCase().includes(normalizedQuery)
    );
}

export function ChatInputAddPanel({ onSelect }: ChatInputAddPanelProps) {
    const [query, setQuery] = useState("");
    const visibleActions = ADD_ACTIONS.filter((action) => matchesQuery(action, query));
    const addActions = visibleActions.filter((action) => action.section === "add");
    const deviceActions = visibleActions.filter((action) => action.section === "device");

    return (
        <div className={styles.addPanel}>
            <div className={styles.addScroll}>
                {addActions.length > 0 ? (
                    <>
                        <span className={styles.sectionLabel}>添加</span>
                        {addActions.map((action) => (
                            <Button
                                className={styles.panelItem}
                                key={action.action}
                                onClick={() => onSelect(action.action)}
                                type="button"
                                variant="text"
                            >
                                <span className={styles.itemIcon}>{action.icon}</span>
                                <span className={styles.itemCopy}>
                                    <span className={styles.itemTitle}>{action.title}</span>
                                    {action.description ? (
                                        <span className={styles.itemDescription}>{action.description}</span>
                                    ) : null}
                                </span>
                            </Button>
                        ))}
                    </>
                ) : null}

                {deviceActions.length > 0 ? (
                    <>
                        <div className={styles.panelDivider} />
                        <span className={styles.sectionLabel}>设备</span>
                        {deviceActions.map((action) => (
                            <Button
                                className={styles.panelItem}
                                key={action.action}
                                onClick={() => onSelect(action.action)}
                                type="button"
                                variant="text"
                            >
                                <span className={styles.itemIcon}>{action.icon}</span>
                                <span className={styles.itemCopy}>
                                    <span className={styles.itemTitle}>{action.title}</span>
                                </span>
                                <span aria-hidden className={styles.statusDot} />
                            </Button>
                        ))}
                    </>
                ) : null}

                {visibleActions.length === 0 ? (
                    <Empty className={styles.skillEmpty} description="没有找到相关操作" />
                ) : null}
            </div>

            <div className={styles.skillSearch}>
                <Input onChange={(value) => setQuery(String(value))} placeholder="搜索..." value={query} />
            </div>
        </div>
    );
}
