"use client";

import { useState } from "react";

import dynamic from "next/dynamic";

import { Tabs } from "tdesign-react";

import { CreateFeedPlaceholder } from "./components/create-feed-placeholder";
import { CREATE_SECTIONS, type CreateSection, SECTION_FILTERS, isCreateSection } from "./composables/_create";
import styles from "./styles/create-view.module.css";

const FloatingChatComposer = dynamic(
    () => import("../../components/floating-chat-composer").then((module) => module.FloatingChatComposer),
    {
        loading: () => <div aria-hidden className={styles.composerPlaceholder} />,
        ssr: false,
    },
);

export function CreateView() {
    const [activeSection, setActiveSection] = useState<CreateSection>(CREATE_SECTIONS.discover);
    const [activeFilter, setActiveFilter] = useState<string | null>(
        SECTION_FILTERS[CREATE_SECTIONS.discover][0] ?? null,
    );
    const sectionTabs = [
        {
            label: "发现",
            value: CREATE_SECTIONS.discover,
        },
        {
            label: "AI 模板",
            value: CREATE_SECTIONS.templates,
        },
        {
            label: "创作活动",
            value: CREATE_SECTIONS.activities,
        },
    ];
    const filters = SECTION_FILTERS[activeSection];

    return (
        <section aria-labelledby="create-page-title" className={styles.page}>
            <h1 className="sr-only" id="create-page-title">
                创作
            </h1>
            <div className={styles.scroller}>
                <div className={styles.tabBar}>
                    <Tabs
                        list={sectionTabs}
                        onChange={(value) => {
                            const section = String(value);

                            if (isCreateSection(section)) {
                                setActiveSection(section);
                                setActiveFilter(SECTION_FILTERS[section][0] ?? null);
                            }
                        }}
                        value={activeSection}
                    />
                </div>
                {filters.length > 0 ? (
                    <div aria-label="内容筛选" className={styles.filters} role="group">
                        {filters.map((filter) => (
                            <button
                                aria-pressed={activeFilter === filter}
                                className={`${styles.filter} ${activeFilter === filter ? styles.filterActive : ""}`}
                                key={filter}
                                onClick={() => setActiveFilter(filter)}
                                type="button"
                            >
                                {filter}
                            </button>
                        ))}
                    </div>
                ) : null}
                <div className={styles.feed}>
                    <CreateFeedPlaceholder section={activeSection} />
                </div>
            </div>

            <div className={styles.composerDock}>
                <FloatingChatComposer />
            </div>
        </section>
    );
}
