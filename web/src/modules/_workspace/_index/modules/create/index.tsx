"use client";

import { useMemo, useState } from "react";

import dynamic from "next/dynamic";

import type { AiCreationCatalog, AiCreationMediaMode } from "@pudans-dev/api-client";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Tabs } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../_console/composables/_errors";
import type {
    ComposerMediaMode,
    ComposerModelOption,
    ComposerSubmission,
} from "../../components/floating-chat-composer/composer-types";
import { CreateFeed } from "./components/create-feed";
import { CREATE_SECTIONS, type CreateSection, SECTION_FILTERS, isCreateSection } from "./composables/_create";
import {
    CREATION_CATALOG_QUERY_KEY,
    CREATION_TASKS_QUERY_KEY,
    creationSettings,
    getCreationCatalog,
    resolveCreationOperation,
    startCreationGeneration,
} from "./composables/_generation";
import styles from "./styles/create-view.module.css";

const FloatingChatComposer = dynamic(
    () => import("../../components/floating-chat-composer").then((module) => module.FloatingChatComposer),
    {
        loading: () => <div aria-hidden className={styles.composerPlaceholder} />,
        ssr: false,
    },
);

const CREATION_MEDIA_MODES: readonly ("image" | "video")[] = ["video", "image"];

function isCreationMediaMode(value: string): value is "image" | "video" {
    return value === "image" || value === "video";
}

function catalogModels(
    catalog: AiCreationCatalog | undefined,
): Partial<Record<ComposerMediaMode, readonly ComposerModelOption[]>> {
    const result: Partial<Record<ComposerMediaMode, readonly ComposerModelOption[]>> = {
        image: [],
        video: [],
    };

    for (const mode of catalog?.mediaModes ?? []) {
        if (!mode.id || !isCreationMediaMode(mode.id)) {
            continue;
        }

        result[mode.id] = (mode.models ?? []).flatMap((model) =>
            model.id
                ? [
                      {
                          description: model.logicalName ?? undefined,
                          id: model.id,
                          label: model.label || model.logicalName || model.id,
                      },
                  ]
                : [],
        );
    }

    return result;
}

function availableMediaModes(
    modesByMediaMode: Partial<Record<ComposerMediaMode, readonly ComposerModelOption[]>>,
    catalog: AiCreationCatalog | undefined,
): readonly ComposerMediaMode[] {
    const configured = new Set(
        (catalog?.mediaModes ?? []).flatMap((mode) => (mode.id && isCreationMediaMode(mode.id) ? [mode.id] : [])),
    );
    const available = CREATION_MEDIA_MODES.filter((mode) => (modesByMediaMode[mode]?.length ?? 0) > 0);

    if (available.length > 0) {
        return available;
    }

    const visible = CREATION_MEDIA_MODES.filter((mode) => configured.has(mode));

    return visible.length > 0 ? visible : CREATION_MEDIA_MODES;
}

export function CreateView() {
    const queryClient = useQueryClient();
    const [activeSection, setActiveSection] = useState<CreateSection>(CREATE_SECTIONS.discover);
    const [activeFilter, setActiveFilter] = useState<string | null>(
        SECTION_FILTERS[CREATE_SECTIONS.discover][0] ?? null,
    );
    const prompt = useMessagePrompt();
    const creationCatalog = useQuery({
        queryKey: CREATION_CATALOG_QUERY_KEY,
        queryFn: getCreationCatalog,
    });
    const modelsByMediaMode = useMemo(() => catalogModels(creationCatalog.data), [creationCatalog.data]);
    const mediaModes = useMemo(
        () => availableMediaModes(modelsByMediaMode, creationCatalog.data),
        [creationCatalog.data, modelsByMediaMode],
    );
    const modelCount = CREATION_MEDIA_MODES.reduce((count, mode) => count + (modelsByMediaMode[mode]?.length ?? 0), 0);
    const catalogNotice = creationCatalog.isError
        ? `创作目录加载失败：${consoleErrorMessage(creationCatalog.error)}`
        : !creationCatalog.isLoading && creationCatalog.data && modelCount === 0
          ? "还没有可用的创作模型，请先在「AI 服务商」中启用模型并绑定渠道。"
          : null;
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

    const handleSend = async (submission: ComposerSubmission) => {
        if (submission.mediaMode === "audio") {
            throw new Error("音频生成尚未接入生成任务接口。");
        }

        const mode: AiCreationMediaMode | undefined = creationCatalog.data?.mediaModes?.find(
            (item) => item.id === submission.mediaMode,
        );

        if (!mode) {
            throw new Error("当前工作区未配置该生成类型。");
        }

        const operation = resolveCreationOperation(mode, submission.toolMode, submission.files.length > 0);

        if (!operation) {
            throw new Error("当前生成方式不在模型的可用操作范围内。");
        }

        try {
            await startCreationGeneration({
                files: submission.files,
                mediaMode: submission.mediaMode,
                modelId: submission.modelId,
                operation,
                prompt: submission.prompt,
                settings: creationSettings(submission),
            });
            void queryClient.invalidateQueries({ queryKey: CREATION_TASKS_QUERY_KEY });
            prompt.success("生成任务已提交。");
        } catch (error) {
            const message = consoleErrorMessage(error, error instanceof Error ? error.message : undefined);

            prompt.error({
                content: message,
                title: "生成失败",
            });
            throw error;
        }
    };

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
                    <CreateFeed section={activeSection} />
                </div>
            </div>

            <div className={styles.composerDock}>
                {catalogNotice ? (
                    <div className={styles.composerNotice} role="status">
                        {catalogNotice}
                    </div>
                ) : null}
                {creationCatalog.isLoading ? (
                    <div aria-hidden className={styles.composerPlaceholder} />
                ) : (
                    <FloatingChatComposer
                        availableMediaModes={mediaModes}
                        defaultMediaMode={mediaModes[0]}
                        modelsByMediaMode={modelsByMediaMode}
                        modelsLoading={creationCatalog.isLoading}
                        onSend={handleSend}
                    />
                )}
            </div>
        </section>
    );
}
