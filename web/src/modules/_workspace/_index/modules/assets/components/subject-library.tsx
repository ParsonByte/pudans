"use client";

import { useState } from "react";

import {
    CartIcon,
    MapCollectionIcon,
    PaletteIcon,
    SearchErrorIcon,
    UserAvatarIcon,
    UsercaseIcon,
} from "tdesign-icons-react";

import styles from "../styles/assets-view.module.css";
import { LibraryChoiceCard } from "./library-choice-card";
import { LibraryEmptyState } from "./library-empty-state";
import { LibraryHeader } from "./library-header";
import { LibraryToolbar } from "./library-toolbar";

const SUBJECT_FILTERS = ["全部", "角色", "商品", "场景", "风格"] as const;

type SubjectFilter = (typeof SUBJECT_FILTERS)[number];

const SUBJECT_TYPES = [
    {
        description: "锁定人物外观、服饰与气质",
        icon: <UserAvatarIcon size={20} />,
        label: "角色",
        tone: "brand" as const,
    },
    {
        description: "保持商品造型、材质与包装一致",
        icon: <CartIcon size={20} />,
        label: "商品",
        tone: "warm" as const,
    },
    {
        description: "复用空间、环境与光线氛围",
        icon: <MapCollectionIcon size={20} />,
        label: "场景",
        tone: "green" as const,
    },
    {
        description: "统一色彩、构图与镜头语言",
        icon: <PaletteIcon size={20} />,
        label: "风格",
        tone: "violet" as const,
    },
];

export function SubjectLibrary() {
    const [activeFilter, setActiveFilter] = useState<SubjectFilter>(SUBJECT_FILTERS[0]);
    const [keyword, setKeyword] = useState("");
    const filtered = activeFilter !== SUBJECT_FILTERS[0] || keyword.trim().length > 0;
    const resetFilters = () => {
        setActiveFilter(SUBJECT_FILTERS[0]);
        setKeyword("");
    };

    return (
        <section aria-labelledby="subject-library-title" className={styles.library}>
            <LibraryHeader
                description="DANS 会从创作与对话中自动识别可复用的角色、商品、场景与风格。"
                icon={<UsercaseIcon size={20} />}
                id="subject-library-title"
                pendingLabel="主体接口待接入"
                title="主体库"
                tone="brand"
            />

            <LibraryToolbar
                filterLabel="主体类型"
                filterOptions={SUBJECT_FILTERS}
                filterValue={activeFilter}
                keyword={keyword}
                onFilterChange={setActiveFilter}
                onKeywordChange={setKeyword}
                searchPlaceholder="搜索主体名称"
            />

            {filtered ? (
                <LibraryEmptyState
                    actionLabel="清空筛选"
                    description="当前分类或关键词下还没有主体，清空筛选后可以重新查看。"
                    icon={<SearchErrorIcon size={28} />}
                    onAction={resetFilters}
                    title="没有找到匹配的主体"
                />
            ) : (
                <LibraryEmptyState
                    description="系统会从生成结果中提取稳定的人物、商品、场景与风格，并自动建立主体档案。"
                    icon={<UsercaseIcon size={30} />}
                    title="主体会自动沉淀在这里"
                >
                    <div aria-label="主体类型" className={styles.choiceGrid}>
                        {SUBJECT_TYPES.map((type) => (
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
