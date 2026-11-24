"use client";

import { useState } from "react";

import { FileIcon, FolderIcon, SearchErrorIcon, SoundIcon, ToolsIcon } from "tdesign-icons-react";

import styles from "../styles/assets-view.module.css";
import { LibraryEmptyState } from "./library-empty-state";
import { LibraryHeader } from "./library-header";
import { LibraryToolbar } from "./library-toolbar";

const ASSET_FILTERS = ["全部", "音频", "文档", "其他"] as const;

type AssetFilter = (typeof ASSET_FILTERS)[number];

const ASSET_GUIDES = [
    {
        description: "旁白、配乐与音效，作为视频和对话创作的输入。",
        icon: <SoundIcon size={18} />,
        title: "音频素材",
    },
    {
        description: "剧本、品牌手册与产品资料，为 DANS 提供可靠上下文。",
        icon: <FileIcon size={18} />,
        title: "文档资料",
    },
    {
        description: "分发包、数据与其他文件，供 Agent、插件与 CLI 调用。",
        icon: <ToolsIcon size={18} />,
        title: "工作文件",
    },
];

export function AssetLibrary() {
    const [activeFilter, setActiveFilter] = useState<AssetFilter>(ASSET_FILTERS[0]);
    const [keyword, setKeyword] = useState("");
    const filtered = activeFilter !== ASSET_FILTERS[0] || keyword.trim().length > 0;
    const resetFilters = () => {
        setActiveFilter(ASSET_FILTERS[0]);
        setKeyword("");
    };

    return (
        <section aria-labelledby="asset-library-title" className={styles.library}>
            <LibraryHeader
                description="系统会把 Agent、创作任务与工作区产生的文件自动归档到这里。"
                icon={<FolderIcon size={20} />}
                id="asset-library-title"
                pendingLabel="文件接口待接入"
                title="文件资产"
                tone="brand"
            />

            <LibraryToolbar
                filterLabel="文件类型"
                filterOptions={ASSET_FILTERS}
                filterValue={activeFilter}
                keyword={keyword}
                onFilterChange={setActiveFilter}
                onKeywordChange={setKeyword}
                searchPlaceholder="搜索文件名"
            />

            {filtered ? (
                <LibraryEmptyState
                    actionLabel="清空筛选"
                    description="当前分类或关键词下还没有文件，清空筛选后可以重新查看。"
                    icon={<SearchErrorIcon size={28} />}
                    onAction={resetFilters}
                    title="没有找到匹配的文件"
                />
            ) : (
                <LibraryEmptyState
                    dashed
                    description="音频、文档和 Agent 产物会自动进入资源库，供后续对话、创作与自动化任务直接引用。"
                    icon={<FolderIcon size={30} />}
                    title="文件会自动归档到这里"
                >
                    <div className={styles.guideGrid}>
                        {ASSET_GUIDES.map((guide) => (
                            <div className={styles.guideItem} key={guide.title}>
                                <span className={styles.guideIcon}>{guide.icon}</span>
                                <span className={styles.guideCopy}>
                                    <strong>{guide.title}</strong>
                                    <small>{guide.description}</small>
                                </span>
                            </div>
                        ))}
                    </div>
                    <p className={styles.formatHint}>文件会按来源与类型自动归集</p>
                </LibraryEmptyState>
            )}
        </section>
    );
}
