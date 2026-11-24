"use client";

import { useState } from "react";

import { FileIcon, LightbulbIcon, SearchErrorIcon, StarIcon, TaskIcon } from "tdesign-icons-react";

import styles from "../styles/assets-view.module.css";
import { LibraryChoiceCard } from "./library-choice-card";
import { LibraryEmptyState } from "./library-empty-state";
import { LibraryHeader } from "./library-header";
import { LibraryToolbar } from "./library-toolbar";

const MEMORY_FILTERS = ["全部", "偏好", "事实", "项目"] as const;

type MemoryFilter = (typeof MEMORY_FILTERS)[number];

const MEMORY_TYPES = [
    {
        description: "记录常用风格、语气与输出习惯",
        icon: <StarIcon size={20} />,
        label: "偏好",
        tone: "warm" as const,
    },
    {
        description: "保存身份、品牌与业务事实",
        icon: <FileIcon size={20} />,
        label: "事实",
        tone: "green" as const,
    },
    {
        description: "保留正在推进的目标与项目约束",
        icon: <TaskIcon size={20} />,
        label: "项目",
        tone: "violet" as const,
    },
];

export function MemoryLibrary() {
    const [activeFilter, setActiveFilter] = useState<MemoryFilter>(MEMORY_FILTERS[0]);
    const [keyword, setKeyword] = useState("");
    const filtered = activeFilter !== MEMORY_FILTERS[0] || keyword.trim().length > 0;
    const resetFilters = () => {
        setActiveFilter(MEMORY_FILTERS[0]);
        setKeyword("");
    };

    return (
        <section aria-labelledby="memory-library-title" className={styles.library}>
            <LibraryHeader
                description="DANS 会从对话中自动提炼偏好、事实与项目背景，并在后续创作中参考。"
                icon={<LightbulbIcon size={20} />}
                id="memory-library-title"
                pendingLabel="记忆接口待接入"
                title="DANS 记忆"
                tone="warm"
            />

            <LibraryToolbar
                filterLabel="记忆类型"
                filterOptions={MEMORY_FILTERS}
                filterValue={activeFilter}
                keyword={keyword}
                onFilterChange={setActiveFilter}
                onKeywordChange={setKeyword}
                searchPlaceholder="搜索记忆内容"
            />

            {filtered ? (
                <LibraryEmptyState
                    actionLabel="清空筛选"
                    description="当前分类或关键词下还没有记忆，清空筛选后可以重新查看。"
                    icon={<SearchErrorIcon size={28} />}
                    onAction={resetFilters}
                    title="没有找到匹配的记忆"
                />
            ) : (
                <LibraryEmptyState
                    description="当 DANS 判断信息值得长期保留时，会自动归档为记忆，不需要手动添加。"
                    icon={<LightbulbIcon size={30} />}
                    iconTone="warm"
                    title="记忆会自动沉淀在这里"
                >
                    <div className={styles.choiceGrid}>
                        {MEMORY_TYPES.map((type) => (
                            <LibraryChoiceCard
                                description={type.description}
                                icon={type.icon}
                                key={type.label}
                                label={type.label}
                                tone={type.tone}
                            />
                        ))}
                    </div>
                </LibraryEmptyState>
            )}
        </section>
    );
}
