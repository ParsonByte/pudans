"use client";

import type { ReactNode } from "react";

import { Card, Skeleton, Typography } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import { formatBytes, formatDateTime } from "../../../../composables/_format";
import type { StorageFile } from "../../../../composables/_storage";
import type { StorageFilesTab } from "../composables/_storage-files";
import { storageFileName } from "../composables/_storage-files";
import { StorageFilePreview } from "./storage-file-preview";
import { StorageFilesEmpty } from "./storage-files-empty";

type StorageFilesGalleryProps = {
    canPreview: boolean;
    files: StorageFile[];
    loading: boolean;
    renderActions: (file: StorageFile) => ReactNode;
    tab: StorageFilesTab;
};

const SKELETON_CARD_COUNT = 4;

export function StorageFilesGallery({ canPreview, files, loading, renderActions, tab }: StorageFilesGalleryProps) {
    if (files.length === 0) {
        if (loading) {
            return (
                <div className="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
                    {Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => (
                        <Card key={`storage-file-skeleton-${index}`} shadow>
                            <Skeleton animation="gradient" theme="avatar-text" />
                        </Card>
                    ))}
                </div>
            );
        }

        return (
            <Card shadow>
                <StorageFilesEmpty tab={tab} />
            </Card>
        );
    }

    return (
        <div className="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
            {files.map((file) => (
                <StorageFileCard
                    canPreview={canPreview}
                    file={file}
                    key={file.metadata.name}
                    renderActions={renderActions}
                />
            ))}
        </div>
    );
}

function StorageFileCard({
    canPreview,
    file,
    renderActions,
}: {
    canPreview: boolean;
    file: StorageFile;
    renderActions: (file: StorageFile) => ReactNode;
}) {
    const token = useDesignTokens();
    const displayName = storageFileName(file);
    const isTrashed = file.spec.state === "TRASHED";
    const policy = file.spec.policy?.trim();

    return (
        <Card
            bodyStyle={{ display: "flex", flex: 1, flexDirection: "column", minHeight: 0, padding: 0 }}
            className="h-full overflow-hidden"
            hoverShadow
            shadow
        >
            <div className="relative aspect-[4/3] min-w-0">
                <StorageFilePreview canPreview={canPreview} className="h-full w-full" file={file} />
                <div
                    className="absolute top-2 right-2 rounded-full border"
                    style={{
                        background: token.colorBgContainer,
                        borderColor: token.colorBorderSecondary,
                    }}
                >
                    {renderActions(file)}
                </div>
            </div>
            <div className="flex min-w-0 flex-1 flex-col p-4">
                <Typography.Text ellipsis={{ row: 1, tooltipProps: { content: displayName } }} strong>
                    {displayName}
                </Typography.Text>
                <div className="mt-1.5 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs">
                    <span className="shrink-0 font-medium tabular-nums" style={{ color: token.colorTextSecondary }}>
                        {formatBytes(file.spec.size)}
                    </span>
                    {policy ? (
                        <span className="min-w-0 truncate" style={{ color: token.colorTextTertiary }}>
                            {policy}
                        </span>
                    ) : null}
                </div>
                <div className="mt-auto pt-3 text-[11px]" style={{ color: token.colorTextTertiary }}>
                    上传 {formatDateTime(file.spec.uploadedAt)}
                </div>
                {isTrashed ? (
                    <div
                        className="mt-2 flex flex-wrap gap-x-3 gap-y-1 border-t pt-2 text-[11px]"
                        style={{ borderColor: token.colorBorderSecondary, color: token.colorTextTertiary }}
                    >
                        <span>移入 {formatDateTime(file.spec.trashedAt)}</span>
                        <span style={{ color: token.colorWarning }}>
                            过期 {formatDateTime(file.spec.trashExpiresAt)}
                        </span>
                    </div>
                ) : null}
            </div>
        </Card>
    );
}
