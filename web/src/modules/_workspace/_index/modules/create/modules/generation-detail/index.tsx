"use client";

import { useState } from "react";
import type { CSSProperties, ReactNode } from "react";

import Image from "next/image";
import Link from "next/link";

import type { AiCreationCatalog, AiCreationModel } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { AiImageIcon, ChevronLeftIcon, DownloadIcon, ShareIcon } from "tdesign-icons-react";
import { Loading } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { DescriptionGrid } from "@/modules/_workspace/_console/components/description-grid";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { formatDateTime } from "@/modules/_workspace/_console/composables/_format";
import { useConsoleSession } from "@/modules/_workspace/_console/composables/_session";
import { createStorageFileDownloadUrl } from "@/modules/_workspace/_console/composables/_storage";
import { UserAvatar } from "@/modules/_workspace/_console/modules/identity/users/components/user-avatar";
import {
    type ComposerBackground,
    getComposerBackgroundLabel,
} from "@/modules/_workspace/_index/components/floating-chat-composer/composer-types";
import { INDEX_PAGE_KEYS, indexPageHref } from "@/routes/(_workspace)/(_index)/paths";

import {
    CREATION_CATALOG_QUERY_KEY,
    getCreationCatalog,
    getGenerationTaskResult,
    getGenerationTaskStatus,
    resolveGenerationAspectRatio,
} from "../../composables/_generation";
import styles from "../work-detail/styles/work-detail-view.module.css";

const DETAIL_IMAGE_SIZES = "(max-width: 767px) 100vw, 70vw";
const DOWNLOAD_URL_STALE_TIME = 4 * 60 * 1000;
const OPERATION_LABELS: Record<string, string> = {
    continue: "视频续写",
    depth_map: "深度图",
    edit: "视频编辑",
    frame: "首尾帧",
    lip_sync: "口型同步",
    motion: "动作迁移",
    reference: "参考生成",
    sound_effect: "音效生成",
    text: "文生内容",
    upscale: "画质增强",
    watermark: "移除水印",
};
const PARAMETER_LABELS: Record<string, string> = {
    audio: "生成音频",
    audioSpeed: "语速",
    audioVolume: "音量",
    background: "背景",
    duration: "时长",
    multiCamera: "多机位",
    previewMode: "预览模式",
    quantity: "生成数量",
    ratio: "画面比例",
    resolution: "分辨率",
    seed: "随机种子",
    stagger: "交错生成",
};
const PARAMETER_ORDER = [
    "duration",
    "ratio",
    "resolution",
    "quantity",
    "background",
    "seed",
    "audio",
    "audioSpeed",
    "audioVolume",
    "multiCamera",
    "stagger",
    "previewMode",
];
const BOOLEAN_PARAMETERS = new Set(["audio", "multiCamera", "previewMode", "stagger"]);
const BACKGROUND_VALUES = new Set<ComposerBackground>(["auto", "opaque", "transparent"]);

type GenerationDetailViewProps = {
    name: string;
};

export function GenerationDetailView({ name }: GenerationDetailViewProps) {
    const prompt = useMessagePrompt();
    const statusQuery = useQuery({
        queryFn: () => getGenerationTaskStatus(name),
        queryKey: ["index", "creation", "generation-status", name],
    });
    const resultQuery = useQuery({
        queryFn: () => getGenerationTaskResult(name),
        queryKey: ["index", "creation", "generation-result", name],
    });
    const catalogQuery = useQuery({
        queryFn: getCreationCatalog,
        queryKey: CREATION_CATALOG_QUERY_KEY,
    });
    const sessionQuery = useConsoleSession();
    const task = statusQuery.data?.task;
    const settings = statusQuery.data?.settings;
    const storageFile = (resultQuery.data?.storageFiles ?? []).find((file) => Boolean(file.fileId));
    const fileId = storageFile?.fileId;
    const urlQuery = useQuery({
        enabled: Boolean(fileId),
        queryFn: () => createStorageFileDownloadUrl(fileId ?? ""),
        queryKey: ["index", "creation", "generation-file-url", fileId],
        staleTime: DOWNLOAD_URL_STALE_TIME,
    });
    const downloadUrl = urlQuery.data;
    const error = statusQuery.error ?? resultQuery.error ?? urlQuery.error;
    const loading = statusQuery.isPending || resultQuery.isPending || (Boolean(fileId) && urlQuery.isPending);
    const [mediaDimensions, setMediaDimensions] = useState<{
        height: number;
        taskId: string;
        width: number;
    }>();

    const copyText = async (value: string, successMessage: string) => {
        try {
            await navigator.clipboard.writeText(value);
            prompt.success(successMessage);
        } catch {
            prompt.error("复制失败，请手动选择内容后复制。");
        }
    };

    if (loading) {
        return (
            <section aria-labelledby="generation-detail-title" className={styles.page}>
                <h1 className="sr-only" id="generation-detail-title">
                    生成详情
                </h1>
                <div className={styles.scroller}>
                    <div className={styles.detailState}>
                        <Loading loading />
                    </div>
                </div>
            </section>
        );
    }
    if (error || !task || !storageFile || !downloadUrl) {
        return (
            <section aria-labelledby="generation-detail-title" className={styles.page}>
                <h1 className="sr-only" id="generation-detail-title">
                    生成详情
                </h1>
                <div className={styles.scroller}>
                    <div className={styles.topBar}>
                        <BackLink />
                    </div>
                    <div className={styles.detailState}>
                        <span>{error ? consoleErrorMessage(error) : "生成结果尚未归档。"}</span>
                    </div>
                </div>
            </section>
        );
    }

    const image = isImageResult(storageFile.contentType, storageFile.mediaType);
    const video = isVideoResult(storageFile.contentType, storageFile.mediaType);
    const fileName = storageFile.originalName ?? storageFile.fileId ?? "生成结果";
    const model = findCreationModel(catalogQuery.data, task.modelId);
    const modelName = model?.label?.trim() || model?.logicalName?.trim() || task.modelId || "未知模型";
    const modelKey =
        model?.logicalName?.trim() && model.logicalName.trim() !== modelName ? model.logicalName.trim() : undefined;
    const creatorName = sessionQuery.data?.displayName || sessionQuery.data?.username || "当前用户";
    const creatorMeta = [
        sessionQuery.data?.username ? `@${sessionQuery.data.username}` : null,
        formatDateTime(task.finishedAt ?? task.createdAt),
    ]
        .filter(Boolean)
        .join(" · ");
    const parameterItems = generationParameterItems(settings, task.mediaType, task.operation);
    const measuredDimensions = mediaDimensions?.taskId === name ? mediaDimensions : undefined;
    const configuredRatio = resolveGenerationAspectRatio(settings, task.mediaType);
    const mediaRatio = measuredDimensions
        ? measuredDimensions.width / measuredDimensions.height
        : configuredRatio.width / configuredRatio.height;
    const mediaFrameStyle = { "--generation-media-ratio": mediaRatio } as CSSProperties;

    return (
        <section aria-labelledby="generation-detail-title" className={styles.page}>
            <h1 className="sr-only" id="generation-detail-title">
                生成详情
            </h1>
            <div className={styles.scroller}>
                <div className={`${styles.topBar} ${styles.generationTopBar}`}>
                    <BackLink />
                    <div className={`${styles.creatorRow} ${styles.generationCreatorRow}`}>
                        <UserAvatar name={creatorName} size={42} src={sessionQuery.data?.avatar} />
                        <span className={styles.creatorCopy}>
                            <span className={styles.creatorName}>{creatorName}</span>
                            <span className={styles.creatorMeta}>{creatorMeta}</span>
                        </span>
                        <span className={styles.aiBadge}>
                            <AiImageIcon size={14} />
                            AI 生成
                        </span>
                    </div>
                </div>

                <div className={styles.generationLayout}>
                    <div aria-label="生成结果操作" className={styles.generationActionRail} role="group">
                        <a
                            aria-label="下载"
                            className={`${styles.reactionButton} ${styles.reactionLink}`}
                            download={fileName}
                            href={downloadUrl}
                            title="下载"
                        >
                            <DownloadIcon size={17} />
                        </a>
                        <button
                            aria-label="分享"
                            className={`${styles.reactionButton} ${styles.reactionLink}`}
                            onClick={() => void copyText(window.location.href, "详情链接已复制")}
                            title="分享"
                            type="button"
                        >
                            <ShareIcon size={17} />
                        </button>
                        <Link
                            aria-label="做同款"
                            className={`${styles.reactionButton} ${styles.reactionLink} ${styles.reactionPrimary}`}
                            href={indexPageHref(INDEX_PAGE_KEYS.create)}
                            title="做同款"
                        >
                            <AiImageIcon size={17} />
                        </Link>
                    </div>

                    <div
                        className={`${styles.primaryColumn} ${styles.generationPrimaryColumn}`}
                        style={mediaFrameStyle}
                    >
                        <div className={styles.generationMediaFrame}>
                            {image ? (
                                <Image
                                    alt=""
                                    className={styles.generationMediaImage}
                                    fill
                                    onLoad={(event) => {
                                        const { naturalHeight, naturalWidth } = event.currentTarget;

                                        if (naturalWidth > 0 && naturalHeight > 0) {
                                            setMediaDimensions({
                                                height: naturalHeight,
                                                taskId: name,
                                                width: naturalWidth,
                                            });
                                        }
                                    }}
                                    sizes={DETAIL_IMAGE_SIZES}
                                    src={downloadUrl}
                                    unoptimized
                                />
                            ) : null}
                            {video ? (
                                <video
                                    className={styles.generationMediaVideo}
                                    controls
                                    onLoadedMetadata={(event) => {
                                        const { videoHeight, videoWidth } = event.currentTarget;

                                        if (videoWidth > 0 && videoHeight > 0) {
                                            setMediaDimensions({
                                                height: videoHeight,
                                                taskId: name,
                                                width: videoWidth,
                                            });
                                        }
                                    }}
                                    playsInline
                                    preload="metadata"
                                    src={downloadUrl}
                                />
                            ) : null}
                            {!image && !video ? (
                                <span className={styles.generationMediaState}>该文件暂不支持在线预览。</span>
                            ) : null}
                        </div>
                    </div>

                    <aside aria-label="生成信息" className={`${styles.infoColumn} ${styles.generationInfoColumn}`}>
                        <section aria-label="使用模型" className={styles.modelCard}>
                            <span className={styles.modelCardIcon}>
                                <AiImageIcon size={18} />
                            </span>
                            <span className={styles.modelCardCopy}>
                                <span className={styles.modelCardCaption}>使用模型</span>
                                <strong className={styles.modelCardName}>{modelName}</strong>
                                {modelKey ? <span className={styles.modelCardKey}>{modelKey}</span> : null}
                            </span>
                        </section>

                        <section className={styles.promptCard}>
                            <div className={styles.promptHeader}>
                                <strong>提示词</strong>
                                <button
                                    className={`${styles.copyButton} ${styles.copyButtonInteractive}`}
                                    onClick={() => void copyText(task.prompt ?? "", "提示词已复制")}
                                    type="button"
                                >
                                    复制
                                </button>
                            </div>
                            <p className={styles.promptText}>{task.prompt || "未填写提示词"}</p>
                        </section>

                        <section className={styles.parameterCard}>
                            <div className={styles.promptHeader}>
                                <strong>生成参数</strong>
                            </div>
                            <DescriptionGrid className={styles.parameterGrid} columns={2} items={parameterItems} />
                        </section>
                    </aside>
                </div>
            </div>
        </section>
    );
}

function findCreationModel(catalog: AiCreationCatalog | undefined, modelId?: string): AiCreationModel | undefined {
    if (!catalog || !modelId) {
        return undefined;
    }

    for (const mediaMode of catalog.mediaModes ?? []) {
        const model = mediaMode.models?.find((item) => item.id === modelId);

        if (model) {
            return model;
        }
    }

    return undefined;
}

function generationParameterItems(
    settings: Record<string, object> | undefined,
    mediaType: string | undefined,
    operation: string | undefined,
): Array<{ label: string; value: ReactNode }> {
    const entries: Record<string, unknown> = settings ?? {};
    const orderedKeys = PARAMETER_ORDER.filter((key) => key in entries);
    const extraKeys = Object.keys(entries).filter((key) => !PARAMETER_ORDER.includes(key));

    return [
        {
            label: "生成方式",
            value: operationLabel(operation, mediaType),
        },
        { label: "输出类型", value: mediaType === "video" ? "视频" : "图片" },
        ...orderedKeys.map((key) => ({
            label: PARAMETER_LABELS[key] ?? key,
            value: formatParameterValue(key, entries[key], mediaType),
        })),
        ...extraKeys.map((key) => ({
            label: PARAMETER_LABELS[key] ?? key,
            value: formatParameterValue(key, entries[key], mediaType),
        })),
    ];
}

function operationLabel(operation: string | undefined, mediaType: string | undefined): string {
    if (operation === "text") {
        return mediaType === "video" ? "文生视频" : "文生图";
    }

    return OPERATION_LABELS[operation ?? ""] ?? (mediaType === "video" ? "视频生成" : "图片生成");
}

function formatParameterValue(key: string, value: unknown, mediaType: string | undefined): ReactNode {
    if (key === "background" && typeof value === "string" && BACKGROUND_VALUES.has(value as ComposerBackground)) {
        return getComposerBackgroundLabel(value as ComposerBackground);
    }
    if (key === "duration" && typeof value === "number") {
        return `${value} 秒`;
    }
    if (key === "quantity" && typeof value === "number") {
        return `${value} ${mediaType === "video" ? "段" : "张"}`;
    }
    if (key === "seed") {
        return Number(value) > 0 ? String(value) : "随机";
    }
    if (key === "audioSpeed" && typeof value === "number") {
        return `${value}×`;
    }
    if (key === "audioVolume" && typeof value === "number") {
        return `${value}%`;
    }
    if (BOOLEAN_PARAMETERS.has(key)) {
        return value === true ? "开启" : "关闭";
    }
    if (typeof value === "object") {
        return JSON.stringify(value) ?? String(value);
    }

    return String(value);
}

function BackLink() {
    return (
        <Link aria-label="返回创作页" className={styles.backButton} href={indexPageHref(INDEX_PAGE_KEYS.create)}>
            <ChevronLeftIcon size={22} />
        </Link>
    );
}

function isImageResult(contentType: string | undefined, mediaType: string | undefined): boolean {
    return contentType?.startsWith("image/") === true || mediaType === "image";
}

function isVideoResult(contentType: string | undefined, mediaType: string | undefined): boolean {
    return contentType?.startsWith("video/") === true || mediaType === "video";
}
