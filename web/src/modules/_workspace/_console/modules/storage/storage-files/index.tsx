"use client";

import { useEffect, useState } from "react";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BrowseGalleryIcon, RefreshIcon, ViewListIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Pagination, RadioGroup, Skeleton, Tabs } from "tdesign-react";
import type { PaginationProps } from "tdesign-react";

import { ScrollArea, SectionCard, confirmGlobalDialog } from "@/components/ui";
import { usePagination } from "@/hooks/table/use-pagination";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useLocalStorage } from "@/hooks/utils/use-local-storage";
import { usePermission } from "@/hooks/utils/use-permission";

import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import {
    type StorageFile,
    listStorageFiles,
    purgeStorageFile,
    restoreStorageFile,
    trashStorageFile,
} from "../../../composables/_storage";
import { StorageFileActions } from "./components/storage-file-actions";
import { StorageFileDetailsModal } from "./components/storage-file-details-modal";
import { StorageFilesGallery } from "./components/storage-files-gallery";
import { StorageFilesMetricGrid } from "./components/storage-files-metric-grid";
import { StorageFilesTable } from "./components/storage-files-table";
import {
    STORAGE_FILES_DOWNLOAD_URL_QUERY_ROOT,
    STORAGE_FILES_LIST_QUERY_ROOT,
    STORAGE_FILES_TOTAL_QUERY_ROOT,
    STORAGE_FILES_VIEW_MODE_KEY,
    STORAGE_FILE_PERMISSIONS,
    type StorageFilesTab,
    type StorageFilesViewMode,
    storageFileDownloadUrlQueryOptions,
    storageFileName,
} from "./composables/_storage-files";

type FileStateAction = "purge" | "restore" | "trash";
type FileStateChange = {
    action: FileStateAction;
    file: StorageFile;
};

const STORAGE_FILES_LIST_STALE_TIME_MS = 30_000;
const FILE_STATE_ACTION_SUCCESS: Record<FileStateAction, string> = {
    purge: "文件已彻底删除。",
    restore: "文件已恢复。",
    trash: "文件已移入回收站。",
};

export function StorageFilesView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const isMobile = useMobile();
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        STORAGE_FILE_PERMISSIONS.view,
    );
    const canDelete = can(STORAGE_FILE_PERMISSIONS.delete);
    const canDownload = can(STORAGE_FILE_PERMISSIONS.download);
    const canPurge = can(STORAGE_FILE_PERMISSIONS.purge);
    const canRestore = can(STORAGE_FILE_PERMISSIONS.restore);
    const [activeTab, setActiveTab] = useState<StorageFilesTab>("active");
    const [storedViewMode, setViewMode] = useLocalStorage<StorageFilesViewMode>(STORAGE_FILES_VIEW_MODE_KEY, "gallery");
    const viewMode = storedViewMode === "list" ? "list" : "gallery";
    const [selected, setSelected] = useState<StorageFile>();
    const [downloadingName, setDownloadingName] = useState<string>();
    const queriesEnabled = sessionQuery.isSuccess && canView;
    const activeTotalQuery = useQuery({
        enabled: queriesEnabled,
        queryFn: async () => listStorageFiles({ page: 1, size: 1, state: "ACTIVE" }),
        queryKey: [...STORAGE_FILES_TOTAL_QUERY_ROOT, "active"],
        staleTime: STORAGE_FILES_LIST_STALE_TIME_MS,
    });
    const trashedTotalQuery = useQuery({
        enabled: queriesEnabled,
        queryFn: async () => listStorageFiles({ page: 1, size: 1, state: "TRASHED" }),
        queryKey: [...STORAGE_FILES_TOTAL_QUERY_ROOT, "trashed"],
        staleTime: STORAGE_FILES_LIST_STALE_TIME_MS,
    });
    const activePagination = usePagination({ total: activeTotalQuery.data?.total ?? 0 });
    const trashedPagination = usePagination({ total: trashedTotalQuery.data?.total ?? 0 });
    const activeQuery = useQuery({
        enabled: queriesEnabled,
        placeholderData: keepPreviousData,
        queryFn: async () =>
            listStorageFiles({
                page: activePagination.page,
                size: activePagination.pageSize,
                state: "ACTIVE",
            }),
        queryKey: [...STORAGE_FILES_LIST_QUERY_ROOT, "active", activePagination.page, activePagination.pageSize],
        staleTime: STORAGE_FILES_LIST_STALE_TIME_MS,
    });
    const trashedQuery = useQuery({
        enabled: queriesEnabled,
        placeholderData: keepPreviousData,
        queryFn: async () =>
            listStorageFiles({
                page: trashedPagination.page,
                size: trashedPagination.pageSize,
                state: "TRASHED",
            }),
        queryKey: [...STORAGE_FILES_LIST_QUERY_ROOT, "trashed", trashedPagination.page, trashedPagination.pageSize],
        staleTime: STORAGE_FILES_LIST_STALE_TIME_MS,
    });
    const currentQuery = activeTab === "active" ? activeQuery : trashedQuery;

    useEffect(() => {
        if (viewMode !== "gallery" || !currentQuery.error) {
            return;
        }

        prompt.error({ content: consoleErrorMessage(currentQuery.error) });
    }, [currentQuery.error, prompt, viewMode]);

    const changeState = useMutation({
        mutationFn: async ({ action, file }: FileStateChange) => {
            if (action === "trash") {
                await trashStorageFile(file.metadata.name);
                return;
            }
            if (action === "restore") {
                await restoreStorageFile(file.metadata.name);
                return;
            }

            await purgeStorageFile(file.metadata.name);
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (_, variables) => {
            prompt.success({ content: FILE_STATE_ACTION_SUCCESS[variables.action] });
            void queryClient.invalidateQueries({ queryKey: STORAGE_FILES_LIST_QUERY_ROOT });
            void queryClient.invalidateQueries({ queryKey: STORAGE_FILES_TOTAL_QUERY_ROOT });
        },
    });

    const isSyncing =
        activeQuery.isFetching ||
        trashedQuery.isFetching ||
        activeTotalQuery.isFetching ||
        trashedTotalQuery.isFetching;
    const refreshFiles = () => {
        void queryClient.invalidateQueries({ queryKey: STORAGE_FILES_LIST_QUERY_ROOT });
        void queryClient.invalidateQueries({ queryKey: STORAGE_FILES_TOTAL_QUERY_ROOT });
        void queryClient.invalidateQueries({ queryKey: STORAGE_FILES_DOWNLOAD_URL_QUERY_ROOT });
    };
    const handleDownload = async (file: StorageFile) => {
        if (!canDownload) {
            return;
        }

        const downloadWindow = window.open("", "_blank");
        if (!downloadWindow) {
            prompt.error({ content: "浏览器拦截了下载窗口，请允许弹出窗口后重试。" });
            return;
        }
        downloadWindow.opener = null;
        setDownloadingName(file.metadata.name);
        try {
            const url = await queryClient.fetchQuery(storageFileDownloadUrlQueryOptions(file.metadata.name));

            if (!url) {
                downloadWindow.close();
                prompt.error({ content: "未获取到下载地址。" });
                return;
            }

            downloadWindow.location.replace(url);
        } catch (error) {
            downloadWindow.close();
            prompt.error({ content: consoleErrorMessage(error) });
        } finally {
            setDownloadingName(undefined);
        }
    };
    const confirmTrash = (file: StorageFile) => {
        if (!canDelete) {
            return;
        }

        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "移入回收站",
            content: (
                <>
                    确定将 <strong>{storageFileName(file)}</strong> 移入回收站吗？
                </>
            ),
            danger: true,
            onConfirm: () => void changeState.mutateAsync({ action: "trash", file }),
            title: "移入回收站",
        });
    };
    const confirmPurge = (file: StorageFile) => {
        if (!canPurge) {
            return;
        }

        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "彻底删除",
            content: (
                <>
                    确定彻底删除 <strong>{storageFileName(file)}</strong> 吗？该操作不可恢复。
                </>
            ),
            danger: true,
            onConfirm: () => void changeState.mutateAsync({ action: "purge", file }),
            title: "彻底删除文件",
        });
    };
    const handleRestore = (file: StorageFile) => {
        if (!canRestore) {
            return;
        }

        changeState.mutate({ action: "restore", file });
    };
    const renderActions = (file: StorageFile) => {
        const mutatingFile = changeState.isPending && changeState.variables?.file.metadata.name === file.metadata.name;

        return (
            <StorageFileActions
                canDelete={canDelete}
                canDownload={canDownload}
                canPurge={canPurge}
                canRestore={canRestore}
                disabled={changeState.isPending}
                file={file}
                loading={mutatingFile || downloadingName === file.metadata.name}
                onDelete={confirmTrash}
                onDetails={setSelected}
                onDownload={(selectedFile) => void handleDownload(selectedFile)}
                onPurge={confirmPurge}
                onRestore={handleRestore}
            />
        );
    };
    const renderFilesPanel = (tab: StorageFilesTab) => {
        const query = tab === "active" ? activeQuery : trashedQuery;
        const totalQuery = tab === "active" ? activeTotalQuery : trashedTotalQuery;
        const files = query.data?.items ?? [];
        const pagination = tab === "active" ? activePagination : trashedPagination;
        const total = totalQuery.data?.total ?? query.data?.total;
        const paginationConfig =
            total === undefined ? false : storageFilesPaginationConfig(pagination, total, isMobile);

        if (viewMode === "gallery") {
            return (
                <div className="flex min-w-0 flex-col gap-3">
                    {query.error ? (
                        <Card bordered={false}>
                            <Empty
                                action={<Button onClick={() => void query.refetch()}>重试</Button>}
                                description={consoleErrorMessage(query.error)}
                                type="network-error"
                            />
                        </Card>
                    ) : null}
                    {!query.error || files.length > 0 ? (
                        <StorageFilesGallery
                            canPreview={canDownload}
                            files={files}
                            loading={query.isPending}
                            renderActions={renderActions}
                            tab={tab}
                        />
                    ) : null}
                    {files.length > 0 && paginationConfig !== false ? (
                        <ScrollArea axis="x" className="flex min-w-0 justify-end">
                            <Pagination {...paginationConfig} />
                        </ScrollArea>
                    ) : null}
                </div>
            );
        }

        return (
            <SectionCard title={tab === "active" ? "文件列表" : "回收站列表"}>
                <StorageFilesTable
                    error={query.error}
                    files={files}
                    loading={query.isFetching}
                    onRetry={query.error ? () => void query.refetch() : undefined}
                    pagination={paginationConfig}
                    renderActions={renderActions}
                    tab={tab}
                />
            </SectionCard>
        );
    };

    if (sessionQuery.isPending) {
        return <Skeleton animation="gradient" theme="paragraph" />;
    }

    if (sessionQuery.error) {
        return (
            <Empty
                action={<Button onClick={() => void sessionQuery.refetch()}>重试</Button>}
                description={consoleErrorMessage(sessionQuery.error)}
                type="network-error"
            />
        );
    }

    if (!canView) {
        return <Empty description="当前账号没有查看存储文件的权限。" type="fail" />;
    }

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                    <PageBreadcrumb current="存储文件" parents={["存储"]} />
                    <Button icon={<RefreshIcon />} loading={isSyncing} onClick={refreshFiles} variant="outline">
                        刷新
                    </Button>
                </div>
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                    <StorageFilesMetricGrid
                        activeTotal={activeTotalQuery.data?.total}
                        currentFiles={currentQuery.data?.items}
                        loading={activeTotalQuery.isPending || trashedTotalQuery.isPending || currentQuery.isPending}
                        trashedTotal={trashedTotalQuery.data?.total}
                    />
                </div>
                <Tabs
                    action={
                        <RadioGroup
                            onChange={(value) => setViewMode(value === "list" ? "list" : "gallery")}
                            options={[
                                {
                                    label: (
                                        <span className="inline-flex items-center gap-1.5">
                                            <BrowseGalleryIcon />
                                            图片
                                        </span>
                                    ),
                                    value: "gallery",
                                },
                                {
                                    label: (
                                        <span className="inline-flex items-center gap-1.5">
                                            <ViewListIcon />
                                            列表
                                        </span>
                                    ),
                                    value: "list",
                                },
                            ]}
                            size="small"
                            theme="button"
                            value={viewMode}
                            variant="default-filled"
                        />
                    }
                    list={[
                        { label: "文件", panel: renderFilesPanel("active"), value: "active" },
                        { label: "回收站", panel: renderFilesPanel("trashed"), value: "trashed" },
                    ]}
                    onChange={(value) => setActiveTab(value === "trashed" ? "trashed" : "active")}
                    value={activeTab}
                />
            </div>
            <StorageFileDetailsModal
                canPreview={canDownload}
                file={selected}
                onClose={() => setSelected(undefined)}
                open={Boolean(selected)}
            />
        </>
    );
}

function storageFilesPaginationConfig(
    pagination: ReturnType<typeof usePagination>,
    total: number,
    isMobile: boolean,
): PaginationProps {
    return {
        current: pagination.page,
        onChange: ({ current, pageSize }) => {
            if (pageSize !== pagination.pageSize) {
                pagination.setPageSize(pageSize);
                return;
            }

            pagination.setPage(current);
        },
        pageSize: pagination.pageSize,
        showJumper: false,
        showPageSize: !isMobile,
        size: isMobile ? "small" : "medium",
        total,
        totalContent: `共 ${total} 条`,
    };
}
