"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { isAxiosError } from "axios";

import { useLocalStorage } from "@/hooks/utils/use-local-storage";

import { consoleErrorMessage } from "../../../../composables/_errors";
import {
    type StorageTransferTask,
    type StorageUploadDirectCredential,
    type StorageUploadTicket,
    abortStorageUpload,
    completeStorageUpload,
    getStorageUploadStatus,
    initiateStorageUpload,
    issueStorageUploadToken,
    presignStoragePart,
    uploadDirectStorageFile,
    uploadPresignedStoragePart,
    uploadStoragePart,
} from "../../../../composables/_storage";

const STORAGE_UPLOAD_SESSIONS_KEY = "console.storage-files.upload-sessions";

const STORAGE_UPLOAD_MODULE = "console";

export type StorageUploadItemStatus =
    "queued" | "preparing" | "uploading" | "paused" | "completed" | "failed" | "canceled";

export type StorageUploadSessionRecord = {
    chunkSize: number;
    contentType?: string;
    createdAt: number;
    expiresAt?: string;
    fileId: string;
    originalName: string;
    partCount: number;
    policy: string;
    presigned: boolean;
    size: number;
};

export type StorageUploadItem = {
    chunkSize?: number;
    contentType?: string;
    createdAt: number;
    directUpload?: StorageUploadDirectCredential;
    error?: string;
    expiresAt?: string;
    file?: File;
    id: string;
    loadedBytes: number;
    originalName: string;
    partCount?: number;
    policy: string;
    presigned?: boolean;
    serverFileId?: string;
    size: number;
    status: StorageUploadItemStatus;
    uploadToken?: string;
};

function recordToItem(record: StorageUploadSessionRecord): StorageUploadItem {
    return {
        chunkSize: record.chunkSize,
        contentType: record.contentType,
        createdAt: record.createdAt,
        expiresAt: record.expiresAt,
        id: `session-${record.fileId}`,
        loadedBytes: 0,
        originalName: record.originalName,
        partCount: record.partCount,
        policy: record.policy,
        presigned: record.presigned,
        serverFileId: record.fileId,
        size: record.size,
        status: "paused",
    };
}

function isStorageUploadAbort(error: unknown): boolean {
    return error instanceof DOMException && error.name === "AbortError";
}

function storageUploadErrorMessage(error: unknown): string {
    if (isAxiosError(error)) {
        return consoleErrorMessage(error);
    }

    return error instanceof Error && error.message ? error.message : "上传失败。";
}

export function useStorageUploads(options: { onCompleted?: () => void } = {}) {
    const { onCompleted } = options;
    const [items, setItems] = useState<StorageUploadItem[]>([]);
    const [sessions, setSessions] = useLocalStorage<StorageUploadSessionRecord[]>(STORAGE_UPLOAD_SESSIONS_KEY, []);
    const itemsRef = useRef<StorageUploadItem[]>([]);
    const runningRef = useRef(false);
    const taskRef = useRef(new Map<string, StorageTransferTask<unknown>>());
    const pauseRef = useRef(new Set<string>());
    const cancelRef = useRef(new Set<string>());
    const onCompletedRef = useRef(onCompleted);
    const restoredRef = useRef(false);

    useEffect(() => {
        onCompletedRef.current = onCompleted;
    }, [onCompleted]);

    const applyItems = useCallback((updater: (current: StorageUploadItem[]) => StorageUploadItem[]) => {
        itemsRef.current = updater(itemsRef.current);
        setItems(itemsRef.current);
    }, []);

    const updateItem = useCallback(
        (id: string, patch: Partial<StorageUploadItem>) => {
            applyItems((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)));
        },
        [applyItems],
    );

    const persistSession = useCallback(
        (ticket: StorageUploadTicket, file: File) => {
            if (ticket.directUpload) {
                return;
            }
            setSessions((previous) => [
                ...previous.filter((record) => record.fileId !== ticket.fileId),
                {
                    chunkSize: ticket.chunkSize,
                    contentType: file.type || undefined,
                    createdAt: Date.now(),
                    expiresAt: ticket.expiresAt,
                    fileId: ticket.fileId,
                    originalName: file.name,
                    partCount: ticket.partCount,
                    policy: ticket.policy,
                    presigned: ticket.presigned,
                    size: file.size,
                },
            ]);
        },
        [setSessions],
    );

    const removeSession = useCallback(
        (fileId?: string) => {
            if (!fileId) {
                return;
            }
            setSessions((previous) => previous.filter((record) => record.fileId !== fileId));
        },
        [setSessions],
    );

    const discard = useCallback(
        async (id: string) => {
            const item = itemsRef.current.find((entry) => entry.id === id);
            if (!item) {
                return;
            }
            applyItems((current) => current.filter((entry) => entry.id !== id));
            removeSession(item.serverFileId);
            if (!item.serverFileId || item.status === "completed" || item.status === "canceled") {
                return;
            }
            try {
                const token = item.uploadToken ?? (await issueStorageUploadToken(item.serverFileId));
                await abortStorageUpload(item.serverFileId, token);
            } catch {
                // 已失效的上传会话由服务端超时清理。
            }
        },
        [applyItems, removeSession],
    );

    const completeItem = useCallback(
        (id: string, fileId: string, size: number) => {
            removeSession(fileId);
            updateItem(id, {
                directUpload: undefined,
                file: undefined,
                loadedBytes: size,
                status: "completed",
                uploadToken: undefined,
            });
            onCompletedRef.current?.();
        },
        [removeSession, updateItem],
    );

    const runItem = useCallback(
        async (item: StorageUploadItem) => {
            const id = item.id;
            const file = item.file;
            if (!file) {
                return;
            }
            const progressFor = (offset: number) => (loaded: number) => {
                updateItem(id, { loadedBytes: Math.min(file.size, offset + loaded) });
            };
            try {
                let serverFileId = item.serverFileId;
                let uploadToken = item.uploadToken;
                let directUpload = item.directUpload;
                let chunkSize = item.chunkSize ?? 0;
                let partCount = item.partCount ?? 0;
                let presigned = item.presigned ?? false;
                const uploadedParts = new Set<number>();

                if (!serverFileId) {
                    updateItem(id, { error: undefined, status: "preparing" });
                    const ticket = await initiateStorageUpload({
                        contentType: file.type || undefined,
                        module: STORAGE_UPLOAD_MODULE,
                        originalName: file.name,
                        policy: item.policy,
                        size: file.size,
                    });
                    serverFileId = ticket.fileId;
                    uploadToken = ticket.uploadToken;
                    directUpload = ticket.directUpload;
                    chunkSize = ticket.chunkSize;
                    partCount = ticket.partCount;
                    presigned = ticket.presigned;
                    updateItem(id, {
                        chunkSize,
                        directUpload,
                        expiresAt: ticket.expiresAt,
                        partCount,
                        presigned,
                        serverFileId,
                        uploadToken,
                    });
                    persistSession(ticket, file);
                    if (cancelRef.current.has(id)) {
                        await abortStorageUpload(serverFileId, uploadToken).catch(() => undefined);
                        removeSession(serverFileId);
                        updateItem(id, { status: "canceled", directUpload: undefined, uploadToken: undefined });
                        return;
                    }
                } else if (!directUpload) {
                    updateItem(id, { error: undefined, status: "preparing" });
                    if (!uploadToken) {
                        uploadToken = await issueStorageUploadToken(serverFileId);
                        updateItem(id, { uploadToken });
                    }
                    const status = await getStorageUploadStatus(serverFileId, uploadToken);
                    chunkSize = status.chunkSize;
                    partCount = status.partCount;
                    presigned = status.presigned;
                    for (const part of status.uploadedParts ?? []) {
                        if (presigned) {
                            uploadedParts.add(part.partNumber);
                        }
                    }
                    updateItem(id, { chunkSize, expiresAt: status.expiresAt, partCount, presigned });
                }

                if (directUpload) {
                    updateItem(id, { status: "uploading" });
                    const task = uploadDirectStorageFile(directUpload, file, (loaded) =>
                        updateItem(id, { loadedBytes: Math.min(loaded, file.size) }),
                    );
                    taskRef.current.set(id, task);
                    try {
                        await task.promise;
                    } finally {
                        taskRef.current.delete(id);
                    }
                    await completeStorageUpload(serverFileId, uploadToken ?? "", []);
                    completeItem(id, serverFileId, file.size);
                    return;
                }

                if (!serverFileId || !uploadToken || partCount < 1 || (partCount > 1 && chunkSize <= 0)) {
                    throw new Error("上传会话参数无效。");
                }
                updateItem(id, { status: "uploading" });
                for (let part = 1; part <= partCount; part += 1) {
                    if (cancelRef.current.has(id)) {
                        updateItem(id, { status: "canceled" });
                        return;
                    }
                    if (pauseRef.current.has(id)) {
                        updateItem(id, { status: "paused" });
                        return;
                    }
                    const offset = (part - 1) * chunkSize;
                    const length = Math.min(chunkSize, file.size - offset);
                    const body = file.slice(offset, offset + length);
                    if (!uploadedParts.has(part)) {
                        if (presigned) {
                            const partUrl = await presignStoragePart(serverFileId, uploadToken, part);
                            const task = uploadPresignedStoragePart(partUrl.url, body, progressFor(offset));
                            taskRef.current.set(id, task);
                            try {
                                await task.promise;
                            } finally {
                                taskRef.current.delete(id);
                            }
                        } else {
                            const task = uploadStoragePart(serverFileId, uploadToken, part, body, progressFor(offset));
                            taskRef.current.set(id, task);
                            try {
                                await task.promise;
                            } finally {
                                taskRef.current.delete(id);
                            }
                        }
                    }
                    updateItem(id, { loadedBytes: Math.min(file.size, offset + length) });
                }
                if (cancelRef.current.has(id) || pauseRef.current.has(id)) {
                    updateItem(id, { status: cancelRef.current.has(id) ? "canceled" : "paused" });
                    return;
                }
                await completeStorageUpload(serverFileId, uploadToken, []);
                completeItem(id, serverFileId, file.size);
            } catch (error) {
                if (isStorageUploadAbort(error)) {
                    updateItem(id, { status: cancelRef.current.has(id) ? "canceled" : "paused" });
                    return;
                }
                if (cancelRef.current.has(id)) {
                    updateItem(id, { status: "canceled" });
                    return;
                }
                updateItem(id, { error: storageUploadErrorMessage(error), status: "failed" });
            }
        },
        [completeItem, persistSession, removeSession, updateItem],
    );

    const runQueue = useCallback(async () => {
        for (;;) {
            const next = itemsRef.current.find((item) => item.status === "queued" && item.file);
            if (!next) {
                return;
            }
            await runItem(next);
        }
    }, [runItem]);

    const kick = useCallback(() => {
        if (runningRef.current) {
            return;
        }
        runningRef.current = true;
        void runQueue().finally(() => {
            runningRef.current = false;
        });
    }, [runQueue]);

    useEffect(() => {
        if (restoredRef.current) {
            return;
        }
        restoredRef.current = true;
        const now = Date.now();
        const valid = sessions.filter((record) => !record.expiresAt || new Date(record.expiresAt).getTime() > now);
        const expiredFileIds = sessions
            .filter((record) => record.expiresAt && new Date(record.expiresAt).getTime() <= now)
            .map((record) => record.fileId);
        if (expiredFileIds.length > 0) {
            setSessions((previous) => previous.filter((record) => !expiredFileIds.includes(record.fileId)));
        }
        if (valid.length > 0) {
            applyItems((current) => {
                const existing = new Set(current.map((item) => item.serverFileId).filter(Boolean));
                return [...valid.filter((record) => !existing.has(record.fileId)).map(recordToItem), ...current];
            });
        }
    }, [applyItems, sessions, setSessions]);

    const addFiles = useCallback(
        (files: File[], policy: string) => {
            if (files.length === 0) {
                return;
            }
            const createdAt = Date.now();
            applyItems((current) => [
                ...current,
                ...files.map((file) => ({
                    contentType: file.type || undefined,
                    createdAt,
                    file,
                    id: crypto.randomUUID(),
                    loadedBytes: 0,
                    originalName: file.name,
                    policy,
                    size: file.size,
                    status: "queued" as const,
                })),
            ]);
            kick();
        },
        [applyItems, kick],
    );

    const pause = useCallback(
        (id: string) => {
            const item = itemsRef.current.find((entry) => entry.id === id);
            if (!item || item.status !== "uploading") {
                return;
            }
            pauseRef.current.add(id);
            if (item.directUpload) {
                taskRef.current.get(id)?.abort();
            }
            updateItem(id, { status: "paused" });
        },
        [updateItem],
    );

    const resume = useCallback(
        (id: string) => {
            const item = itemsRef.current.find((entry) => entry.id === id);
            if (!item || !item.file || item.status !== "paused") {
                return;
            }
            pauseRef.current.delete(id);
            updateItem(id, { error: undefined, status: "queued" });
            kick();
        },
        [kick, updateItem],
    );

    const attachFile = useCallback(
        (id: string, file: File) => {
            const item = itemsRef.current.find((entry) => entry.id === id);
            if (!item || file.name !== item.originalName || file.size !== item.size) {
                return false;
            }
            pauseRef.current.delete(id);
            updateItem(id, { error: undefined, file, status: "queued" });
            kick();
            return true;
        },
        [kick, updateItem],
    );

    const retry = useCallback(
        (id: string) => {
            const item = itemsRef.current.find((entry) => entry.id === id);
            if (!item || item.status !== "failed" || !item.file) {
                return;
            }
            updateItem(id, { error: undefined, status: "queued" });
            kick();
        },
        [kick, updateItem],
    );

    const cancel = useCallback(
        async (id: string) => {
            const item = itemsRef.current.find((entry) => entry.id === id);
            if (!item) {
                return;
            }
            cancelRef.current.add(id);
            taskRef.current.get(id)?.abort();
            if (!item.serverFileId) {
                applyItems((current) => current.filter((entry) => entry.id !== id));
                return;
            }
            updateItem(id, { status: "canceled" });
            removeSession(item.serverFileId);
            try {
                const token = item.uploadToken ?? (await issueStorageUploadToken(item.serverFileId));
                await abortStorageUpload(item.serverFileId, token);
            } catch {
                // 已失效的上传会话由服务端超时清理。
            }
        },
        [applyItems, removeSession, updateItem],
    );

    return { addFiles, attachFile, cancel, discard, items, pause, resume, retry };
}

export type StorageUploadController = ReturnType<typeof useStorageUploads>;
