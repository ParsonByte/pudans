"use client";

import { useState } from "react";

import {
    ChatBubbleIcon,
    HistoryIcon,
    ImageIcon,
    ImageSearchIcon,
    SearchErrorIcon,
    VideoIcon,
    VideoLibraryIcon,
} from "tdesign-icons-react";

import styles from "../styles/assets-view.module.css";
import { LibraryEmptyState } from "./library-empty-state";
import { LibraryHeader } from "./library-header";
import { LibraryToolbar } from "./library-toolbar";

type MediaLibraryProps = {
    kind: "image" | "video";
};

const MEDIA_LIBRARY_CONFIG = {
    image: {
        badges: "图片归档接口待接入",
        description: "创作、工具与对话中生成的图片会自动归档，方便随时复用。",
        emptyTitle: "图片会自动归档到这里",
        filterLabel: "图片来源",
        filters: ["全部", "创作", "对话"],
        sourceHint: "系统会保留生成记录与版本，无需手动整理",
        iconTone: "brand" as const,
        guides: [
            {
                description: "创作页与图片工具产生的图片自动进入资源库",
                icon: <ImageSearchIcon size={18} />,
                title: "创作生成",
            },
            {
                description: "对话中生成或引用的图片自动沉淀，随会话归档",
                icon: <ChatBubbleIcon size={18} />,
                title: "对话产出",
            },
            {
                description: "保留同一提示词的多轮结果，方便回溯与复用",
                icon: <HistoryIcon size={18} />,
                title: "版本记录",
            },
        ],
        searchPlaceholder: "搜索图片名称",
        title: "图片库",
    },
    video: {
        badges: "视频归档接口待接入",
        description: "视频工具、创作任务与对话产出的视频会自动汇总到一起。",
        emptyTitle: "视频会自动归档到这里",
        filterLabel: "视频来源",
        filters: ["全部", "创作", "对话"],
        sourceHint: "生成、复刻、延长与补帧的版本会自动保留",
        iconTone: "green" as const,
        guides: [
            {
                description: "视频生成、复刻与编辑结果自动进入资源库",
                icon: <VideoLibraryIcon size={18} />,
                title: "视频工具",
            },
            {
                description: "DANS 对话中生成或引用的视频随会话归档",
                icon: <ChatBubbleIcon size={18} />,
                title: "对话产出",
            },
            {
                description: "保留生成链路上的多个版本，便于比较和复用",
                icon: <HistoryIcon size={18} />,
                title: "版本记录",
            },
        ],
        searchPlaceholder: "搜索视频名称",
        title: "视频库",
    },
} as const;

export function MediaLibrary({ kind }: MediaLibraryProps) {
    const config = MEDIA_LIBRARY_CONFIG[kind];
    const [activeFilter, setActiveFilter] = useState<string>(config.filters[0]);
    const [keyword, setKeyword] = useState("");
    const filtered = activeFilter !== config.filters[0] || keyword.trim().length > 0;
    const resetFilters = () => {
        setActiveFilter(config.filters[0]);
        setKeyword("");
    };

    return (
        <section aria-labelledby={`media-library-${kind}-title`} className={styles.library}>
            <LibraryHeader
                description={config.description}
                icon={kind === "image" ? <ImageIcon size={20} /> : <VideoIcon size={20} />}
                id={`media-library-${kind}-title`}
                pendingLabel={config.badges}
                title={config.title}
                tone={config.iconTone}
            />

            <LibraryToolbar
                filterLabel={config.filterLabel}
                filterOptions={config.filters}
                filterValue={activeFilter}
                keyword={keyword}
                onFilterChange={setActiveFilter}
                onKeywordChange={setKeyword}
                searchPlaceholder={config.searchPlaceholder}
            />

            {filtered ? (
                <LibraryEmptyState
                    actionLabel="清空筛选"
                    description="当前来源或关键词下还没有内容，清空筛选后可以重新查看。"
                    icon={<SearchErrorIcon size={28} />}
                    onAction={resetFilters}
                    title={`没有找到匹配的${kind === "image" ? "图片" : "视频"}`}
                />
            ) : (
                <LibraryEmptyState
                    dashed
                    description={config.description}
                    icon={kind === "image" ? <ImageIcon size={30} /> : <VideoIcon size={30} />}
                    iconTone={config.iconTone}
                    title={config.emptyTitle}
                >
                    <div className={styles.guideGrid}>
                        {config.guides.map((guide) => (
                            <div className={styles.guideItem} key={guide.title}>
                                <span className={styles.guideIcon}>{guide.icon}</span>
                                <span className={styles.guideCopy}>
                                    <strong>{guide.title}</strong>
                                    <small>{guide.description}</small>
                                </span>
                            </div>
                        ))}
                    </div>
                    <p className={styles.formatHint}>{config.sourceHint}</p>
                </LibraryEmptyState>
            )}
        </section>
    );
}
