"use client";

import { useState } from "react";

import Link from "next/link";

import { BrowseGalleryIcon, PlayCircleIcon, RefreshIcon, SearchIcon } from "tdesign-icons-react";

import { indexExploreWorkHref } from "@/routes/(_workspace)/(_index)/paths";

import { EXPLORE_CATEGORIES, type ExploreCategory, WORK_CARD_KEYS, exploreWorkTypes } from "./composables/_explore";
import styles from "./styles/explore-view.module.css";

export function ExploreView() {
    const [activeCategory, setActiveCategory] = useState<ExploreCategory>(EXPLORE_CATEGORIES[0]);
    const workTypes = exploreWorkTypes(activeCategory);
    const [activeWorkType, setActiveWorkType] = useState<string>(workTypes[0]);

    return (
        <section aria-labelledby="explore-page-title" className={styles.page}>
            <h1 className="sr-only" id="explore-page-title">
                探索
            </h1>
            <div className={styles.scroller}>
                <div className={styles.headerBar}>
                    <div aria-label="作品分类" className={styles.categoryRail} role="tablist">
                        {EXPLORE_CATEGORIES.map((category) => {
                            const active = category === activeCategory;

                            return (
                                <button
                                    aria-selected={active}
                                    className={`${styles.category} ${active ? styles.categoryActive : ""}`}
                                    key={category}
                                    onClick={() => {
                                        setActiveCategory(category);
                                        setActiveWorkType(exploreWorkTypes(category)[0] ?? "");
                                    }}
                                    role="tab"
                                    type="button"
                                >
                                    {category}
                                </button>
                            );
                        })}
                    </div>
                    <div className={styles.headerActions}>
                        <button
                            aria-disabled="true"
                            aria-label="搜索作品"
                            className={styles.iconButton}
                            title="搜索接口待接入"
                            type="button"
                        >
                            <SearchIcon size={17} />
                        </button>
                        <button
                            aria-disabled="true"
                            aria-label="刷新作品"
                            className={styles.iconButton}
                            title="刷新接口待接入"
                            type="button"
                        >
                            <RefreshIcon size={17} />
                        </button>
                    </div>
                </div>

                <div className={styles.subnavRow}>
                    <div aria-label="作品类型" className={styles.subnav} role="tablist">
                        {workTypes.map((workType) => {
                            const active = workType === activeWorkType;

                            return (
                                <button
                                    aria-selected={active}
                                    className={`${styles.subnavButton} ${active ? styles.subnavButtonActive : ""}`}
                                    key={workType}
                                    onClick={() => setActiveWorkType(workType)}
                                    role="tab"
                                    type="button"
                                >
                                    {workType}
                                </button>
                            );
                        })}
                    </div>
                    <span className={styles.pendingBadge}>作品接口待接入</span>
                </div>

                <div className={styles.pendingNotice} role="status">
                    <span className={styles.pendingIcon}>
                        <BrowseGalleryIcon size={18} />
                    </span>
                    <span className={styles.pendingCopy}>
                        <strong>探索作品接口待接入</strong>
                        <span>接入后展示分类作品、封面、标题与作者信息。</span>
                    </span>
                    <span className={styles.pendingTag}>版式占位</span>
                </div>

                <div className={styles.workGrid}>
                    {WORK_CARD_KEYS.map((key) => (
                        <article className={styles.workCard} key={key}>
                            <Link
                                aria-label="打开作品播放页"
                                className={styles.workCardLink}
                                href={indexExploreWorkHref("preview")}
                            />
                            <div aria-hidden className={styles.cover}>
                                <span className={styles.coverIcon}>
                                    <PlayCircleIcon size={26} />
                                </span>
                                <span className={styles.coverLabel}>封面待接入</span>
                            </div>
                            <div aria-hidden className={styles.cardBody}>
                                <span className={styles.titleSkeleton} />
                                <span className={styles.authorRow}>
                                    <span className={styles.authorAvatar} />
                                    <span className={styles.authorSkeleton} />
                                </span>
                            </div>
                        </article>
                    ))}
                </div>

                <div className={styles.endState}>作品接口接入后展示更多内容</div>
            </div>
        </section>
    );
}
