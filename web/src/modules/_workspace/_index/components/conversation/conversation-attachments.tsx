"use client";

import { useMemo } from "react";

import dynamic from "next/dynamic";

import { useQueries } from "@tanstack/react-query";
import type { TdAttachmentItem } from "@tdesign-react/chat";
import { Skeleton } from "tdesign-react";

import {
    conversationAssetQueryKey,
    conversationDownloadUrlQueryKey,
    getConversationAsset,
    getConversationAssetDownloadUrl,
} from "../../composables/_conversation";
import { attachmentFileType } from "./conversation-utils";
import styles from "./conversation.module.css";

const Attachments = dynamic(() => import("@tdesign-react/chat").then((module) => module.Attachments), { ssr: false });

type ConversationAttachmentsProps = {
    assetIds: string[];
};

export function ConversationAttachments({ assetIds }: ConversationAttachmentsProps) {
    const ids = useMemo(() => [...new Set(assetIds.filter(Boolean))], [assetIds]);
    const assetQueries = useQueries({
        queries: ids.map((assetId) => ({
            queryFn: () => getConversationAsset(assetId),
            queryKey: conversationAssetQueryKey(assetId),
            staleTime: 60_000,
        })),
    });
    const storageFileIds = assetQueries.map((query) => query.data?.asset.storageFileId ?? "");
    const urlQueries = useQueries({
        queries: storageFileIds.map((storageFileId) => ({
            enabled: Boolean(storageFileId),
            queryFn: () => getConversationAssetDownloadUrl(storageFileId),
            queryKey: conversationDownloadUrlQueryKey(storageFileId),
            staleTime: 4 * 60_000,
        })),
    });
    const loading = assetQueries.some((query) => query.isPending);

    if (ids.length === 0) {
        return null;
    }
    if (loading) {
        return (
            <div className={styles.attachmentLoading}>
                <Skeleton animation="gradient" theme="text" />
            </div>
        );
    }

    const items: TdAttachmentItem[] = ids.map((assetId, index) => {
        const asset = assetQueries[index]?.data?.asset;
        const name = asset?.name?.trim() || assetId;
        const url = urlQueries[index]?.data ?? undefined;

        return {
            fileType: attachmentFileType(asset?.kind ?? "", name),
            key: assetId,
            name,
            size: asset?.size,
            url,
        };
    });

    return (
        <Attachments
            imageViewer
            items={items}
            onFileClick={(event) => {
                if (event.detail.url) {
                    window.open(event.detail.url, "_blank", "noopener,noreferrer");
                }
            }}
            overflow="wrap"
            removable={false}
        />
    );
}
