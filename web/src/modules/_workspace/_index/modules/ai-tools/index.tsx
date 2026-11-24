"use client";

import { useRef, useState } from "react";

import { ToolsIcon } from "tdesign-icons-react";
import { MessagePlugin } from "tdesign-react";

import { AiToolCard } from "./components/ai-tool-card";
import { AI_TOOL_CATEGORIES, AI_TOOL_GROUPS, type AiToolCategory, CATEGORY_GROUPS } from "./composables/_ai-tools";
import styles from "./styles/ai-tools.module.css";

export function AiToolsView() {
    const [activeCategory, setActiveCategory] = useState<AiToolCategory>(AI_TOOL_CATEGORIES[0]);
    const scrollerRef = useRef<HTMLDivElement>(null);
    const visibleGroupKeys = CATEGORY_GROUPS[activeCategory];
    const visibleGroups = AI_TOOL_GROUPS.filter((group) => visibleGroupKeys.includes(group.key));

    const selectCategory = (category: AiToolCategory) => {
        setActiveCategory(category);
        scrollerRef.current?.scrollTo({ top: 0 });
    };

    return (
        <section aria-labelledby="ai-tools-page-title" className={styles.page}>
            <div className={styles.scroller} ref={scrollerRef}>
                <div className={styles.content}>
                    <header className={styles.hero}>
                        <h1 className={styles.title} id="ai-tools-page-title">
                            创作所需 一应俱全
                        </h1>
                        <div aria-label="AI 工具分类" className={styles.categories} role="group">
                            {AI_TOOL_CATEGORIES.map((category) => {
                                const active = category === activeCategory;

                                return (
                                    <button
                                        aria-pressed={active}
                                        className={`${styles.category} ${active ? styles.categoryActive : ""}`}
                                        key={category}
                                        onClick={() => selectCategory(category)}
                                        type="button"
                                    >
                                        {category}
                                    </button>
                                );
                            })}
                        </div>
                    </header>

                    {visibleGroups.length > 0 ? (
                        <div className={styles.groups}>
                            {visibleGroups.map((group) => (
                                <section aria-labelledby={`ai-tool-group-${group.key}`} key={group.key}>
                                    <div className={styles.groupHeading}>
                                        <h2 id={`ai-tool-group-${group.key}`}>{group.title}</h2>
                                        <span>接口待接入</span>
                                    </div>
                                    <div className={styles.grid}>
                                        {group.tools.map((tool) => (
                                            <AiToolCard
                                                coverTone={tool.coverTone}
                                                description={tool.description}
                                                icon={tool.icon}
                                                key={tool.id}
                                                onSelect={() => void MessagePlugin.info(`${tool.title}接口待接入`)}
                                                title={tool.title}
                                            />
                                        ))}
                                    </div>
                                </section>
                            ))}
                        </div>
                    ) : (
                        <div className={styles.empty} role="status">
                            <span className={styles.emptyIcon}>
                                <ToolsIcon size={24} />
                            </span>
                            <strong>{activeCategory}工具接口待接入</strong>
                            <span>接口接入后将在这里展示对应工具。</span>
                        </div>
                    )}
                </div>
            </div>
        </section>
    );
}
