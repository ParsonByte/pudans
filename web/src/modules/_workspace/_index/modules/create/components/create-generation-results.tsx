"use client";

import { useRef, useState } from "react";

import Image from "next/image";
import Link from "next/link";

import type { ArchivedResult, GenerationTaskSummary } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Loading, Tag } from "tdesign-react";

import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { createStorageFileDownloadUrl } from "@/modules/_workspace/_console/composables/_storage";
import { indexGenerationHref } from "@/routes/(_workspace)/(_index)/paths";

import {
    getGenerationTaskResult,
    getGenerationTaskStatus,
    isActiveGenerationTask,
    resolveGenerationAspectRatio,
} from "../composables/_generation";
import styles from "../styles/create-view.module.css";

const STATUS_LABELS: Record<string, string> = {
    archiving: "归档中",
    cancelled: "已取消",
    dispatching: "提交中",
    downloading: "下载中",
    failed: "失败",
    provider_running: "生成中",
    queued: "排队中",
    retry_queued: "重试排队",
    succeeded: "已完成",
};

const DOWNLOAD_URL_STALE_TIME = 4 * 60 * 1000;
const PREVIEW_IMAGE_SIZES = "(max-width: 767px) 50vw, (max-width: 1279px) 33vw, 25vw";
const DEFAULT_MEDIA_ASPECT_RATIO = "3 / 4";

type StoredResult = ArchivedResult & { fileId: string };
type CompletedTask = GenerationTaskSummary & { id: string };

type CreateGenerationResultsProps = {
    tasks: GenerationTaskSummary[];
};

export function CreateGenerationStatus({ tasks }: CreateGenerationResultsProps) {
    const problemTasks = tasks.filter((task) => task.status === "failed" || task.status === "cancelled");

    if (problemTasks.length === 0) {
        return null;
    }

    return (
        <div aria-live="polite" className={styles.generationStatusList}>
            {problemTasks.map((task) => (
                <GenerationStatusItem key={task.id} task={task} />
            ))}
        </div>
    );
}

function GenerationStatusItem({ task }: { task: GenerationTaskSummary }) {
    const failed = task.status === "failed";
    const theme = failed ? "danger" : task.status === "cancelled" ? "default" : "warning";

    return (
        <div className={styles.generationStatus}>
            <Tag size="small" theme={theme} variant="light">
                {STATUS_LABELS[task.status ?? ""] ?? task.status ?? "未知"}
            </Tag>
            <span className={styles.generationStatusCopy}>
                <span className={styles.generationStatusPrompt}>{task.prompt || "未填写提示词"}</span>
                {failed && task.errorMessage ? (
                    <span className={styles.generationStatusError} title={task.errorMessage}>
                        {task.errorMessage}
                    </span>
                ) : null}
            </span>
        </div>
    );
}

export function CreateGenerationTiles({ tasks }: CreateGenerationResultsProps) {
    return (
        <>
            {tasks.map((task) => {
                if (!task.id) {
                    return null;
                }
                if (isCompletedTask(task)) {
                    return <GenerationTaskTiles key={task.id} task={task} />;
                }
                if (isActiveGenerationTask(task)) {
                    return <GenerationSkeletonTile key={task.id} task={task} />;
                }

                return null;
            })}
        </>
    );
}

function GenerationSkeletonTile({ task }: { task: GenerationTaskSummary }) {
    const statusQuery = useQuery({
        enabled: Boolean(task.id),
        queryFn: () => getGenerationTaskStatus(task.id ?? ""),
        queryKey: ["index", "creation", "generation-status", task.id],
        staleTime: 30_000,
    });
    const statusLabel = STATUS_LABELS[task.status ?? ""] ?? task.status ?? "生成中";
    const ratio = resolveGenerationAspectRatio(statusQuery.data?.settings, task.mediaType);

    return (
        <div
            aria-label={`${statusLabel}：${task.prompt || "生成任务"}`}
            className={`${styles.feedTile} ${styles.generationTile} ${styles.generationSkeletonTile}`}
            role="status"
            style={{ aspectRatio: `${ratio.width} / ${ratio.height}` }}
        >
            <span aria-hidden className={styles.generationSkeletonShimmer} />
            <span className={styles.generationSkeletonBadge}>
                <Loading loading size="small" />
                {statusLabel}
            </span>
        </div>
    );
}

function GenerationTaskTiles({ task }: { task: CompletedTask }) {
    const resultQuery = useQuery({
        queryFn: () => getGenerationTaskResult(task.id),
        queryKey: ["index", "creation", "generation-result", task.id],
    });
    const files = (resultQuery.data?.storageFiles ?? []).filter((file): file is StoredResult => Boolean(file.fileId));

    if (resultQuery.isPending) {
        return <GenerationTilePlaceholder />;
    }
    if (resultQuery.isError) {
        return (
            <GenerationTileState
                message={consoleErrorMessage(resultQuery.error)}
                onRetry={() => void resultQuery.refetch()}
            />
        );
    }
    if (files.length === 0) {
        return <GenerationTileState message="结果文件尚未归档。" onRetry={() => void resultQuery.refetch()} />;
    }

    return (
        <>
            {files.map((file) => (
                <GenerationMediaTile file={file} key={file.fileId} taskId={task.id} />
            ))}
        </>
    );
}

function GenerationMediaTile({ file, taskId }: { file: StoredResult; taskId: string }) {
    const videoRef = useRef<HTMLVideoElement>(null);
    const [aspectRatio, setAspectRatio] = useState(DEFAULT_MEDIA_ASPECT_RATIO);
    const urlQuery = useQuery({
        queryFn: () => createStorageFileDownloadUrl(file.fileId),
        queryKey: ["index", "creation", "generation-file-url", file.fileId],
        staleTime: DOWNLOAD_URL_STALE_TIME,
    });
    const url = urlQuery.data;

    if (urlQuery.isPending) {
        return <GenerationTilePlaceholder />;
    }
    if (urlQuery.isError || !url) {
        return <GenerationTileState message="结果地址读取失败。" onRetry={() => void urlQuery.refetch()} />;
    }

    const href = indexGenerationHref(taskId);
    const image = isImageResult(file);
    const video = isVideoResult(file);
    const applyNaturalSize = (width: number, height: number) => {
        if (width > 0 && height > 0) {
            setAspectRatio(`${width} / ${height}`);
        }
    };
    const startPreview = () => {
        const videoElement = videoRef.current;

        if (!videoElement) {
            return;
        }

        void videoElement.play().catch(() => undefined);
    };
    const stopPreview = () => {
        videoRef.current?.pause();
    };

    return (
        <Link
            aria-label="打开生成详情"
            className={`${styles.feedTile} ${styles.generationTile}`}
            href={href}
            onBlur={stopPreview}
            onFocus={startPreview}
            onMouseEnter={startPreview}
            onMouseLeave={stopPreview}
            style={{ aspectRatio }}
        >
            {image ? (
                <Image
                    alt=""
                    className={styles.generationTileMedia}
                    fill
                    onLoad={(event) =>
                        applyNaturalSize(event.currentTarget.naturalWidth, event.currentTarget.naturalHeight)
                    }
                    sizes={PREVIEW_IMAGE_SIZES}
                    src={url}
                    unoptimized
                />
            ) : null}
            {video ? (
                <video
                    aria-hidden
                    className={styles.generationTileMedia}
                    loop
                    muted
                    onLoadedMetadata={(event) =>
                        applyNaturalSize(event.currentTarget.videoWidth, event.currentTarget.videoHeight)
                    }
                    playsInline
                    preload="metadata"
                    ref={videoRef}
                    src={url}
                />
            ) : null}
            {!image && !video ? <div aria-hidden className={styles.generationTileFallback} /> : null}
            <div className={styles.generationTileHover}>
                <span className={styles.generationTileAction}>做同款</span>
            </div>
        </Link>
    );
}

function GenerationTilePlaceholder() {
    return (
        <div className={`${styles.feedTile} ${styles.generationTile}`}>
            <div className={styles.generationTileState}>
                <Loading loading size="small" />
            </div>
        </div>
    );
}

function GenerationTileState({ message, onRetry }: { message: string; onRetry?: () => void }) {
    return (
        <div className={`${styles.feedTile} ${styles.generationTile}`}>
            <div className={styles.generationTileState}>
                <span>{message}</span>
                {onRetry ? (
                    <Button onClick={onRetry} size="small" variant="outline">
                        重试
                    </Button>
                ) : null}
            </div>
        </div>
    );
}

function isImageResult(file: StoredResult): boolean {
    return file.contentType?.startsWith("image/") === true || file.mediaType === "image";
}

function isVideoResult(file: StoredResult): boolean {
    return file.contentType?.startsWith("video/") === true || file.mediaType === "video";
}

function isCompletedTask(task: GenerationTaskSummary): task is CompletedTask {
    return task.status === "succeeded" && Boolean(task.id);
}
