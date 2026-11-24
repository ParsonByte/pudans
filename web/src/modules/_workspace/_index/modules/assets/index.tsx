"use client";

import { type ReactNode, useState } from "react";

import { ChatBubbleIcon, FolderIcon, ImageIcon, LightbulbIcon, UsercaseIcon, VideoIcon } from "tdesign-icons-react";
import { Tabs } from "tdesign-react";

import { AssetLibrary } from "./components/asset-library";
import { ChatLibrary } from "./components/chat-library";
import { MediaLibrary } from "./components/media-library";
import { MemoryLibrary } from "./components/memory-library";
import { SubjectLibrary } from "./components/subject-library";
import styles from "./styles/assets-view.module.css";

const LIBRARY_TABS = [
    { label: "图片", value: "images" },
    { label: "视频", value: "videos" },
    { label: "聊天", value: "chats" },
    { label: "资产", value: "assets" },
    { label: "记忆", value: "memories" },
    { label: "主体", value: "subjects" },
] as const;

type LibraryTab = (typeof LIBRARY_TABS)[number]["value"];

const TAB_ICONS = {
    images: <ImageIcon size={16} />,
    videos: <VideoIcon size={16} />,
    chats: <ChatBubbleIcon size={16} />,
    assets: <FolderIcon size={16} />,
    memories: <LightbulbIcon size={16} />,
    subjects: <UsercaseIcon size={16} />,
} as const;

const TAB_DESCRIPTIONS: Record<LibraryTab, string> = {
    images: "查看系统自动归档的图片，把它们变成下一次创作的参考。",
    videos: "浏览系统自动归档的视频，继续复盘或用于后续创作。",
    chats: "保留重要对话，随时回到上一次创作上下文。",
    assets: "集中管理音频、文档与其他文件，供 DANS、创作和 Agent 调用。",
    memories: "沉淀偏好、事实与项目背景，让 DANS 越来越懂你。",
    subjects: "维护角色、商品、场景和风格主体，让跨作品保持一致。",
};

function isLibraryTab(value: string): value is LibraryTab {
    return value in TAB_DESCRIPTIONS;
}

export function AssetsView() {
    const [activeTab, setActiveTab] = useState<LibraryTab>("images");
    const libraryViews = {
        images: <MediaLibrary key="images" kind="image" />,
        videos: <MediaLibrary key="videos" kind="video" />,
        chats: <ChatLibrary key="chats" />,
        assets: <AssetLibrary key="assets" />,
        memories: <MemoryLibrary key="memories" />,
        subjects: <SubjectLibrary key="subjects" />,
    } satisfies Record<LibraryTab, ReactNode>;

    return (
        <section aria-labelledby="assets-page-title" className={styles.page}>
            <div className={styles.scroller}>
                <div className={styles.content}>
                    <header className={styles.header}>
                        <div className={styles.headingGroup}>
                            <span className={styles.eyebrow}>创作资源</span>
                            <div className={styles.titleRow}>
                                <h1 className={styles.title} id="assets-page-title">
                                    资产和主体
                                </h1>
                                <span className={styles.hubTag}>资源中枢</span>
                            </div>
                            <p className={styles.description}>{TAB_DESCRIPTIONS[activeTab]}</p>
                        </div>
                        <div className={styles.archivePolicy}>
                            <span className={styles.archiveDot} />
                            <span>内容由系统自动生成并归档</span>
                        </div>
                    </header>

                    <div className={styles.tabBar}>
                        <Tabs
                            list={LIBRARY_TABS.map((tab) => ({
                                label: (
                                    <span className={styles.tabLabel}>
                                        {TAB_ICONS[tab.value]}
                                        {tab.label}
                                    </span>
                                ),
                                value: tab.value,
                            }))}
                            onChange={(value) => {
                                const tab = String(value);

                                if (isLibraryTab(tab)) {
                                    setActiveTab(tab);
                                }
                            }}
                            value={activeTab}
                        />
                    </div>

                    {libraryViews[activeTab]}
                </div>
            </div>
        </section>
    );
}
