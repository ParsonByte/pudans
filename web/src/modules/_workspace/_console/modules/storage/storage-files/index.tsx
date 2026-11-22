"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Modal, Space, type TableProps, Tag } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatBytes, formatDateTime } from "../../../composables/_format";
import {
    type StorageFile,
    createStorageFileDownloadUrl,
    listStorageFiles,
    purgeStorageFile,
    restoreStorageFile,
    trashStorageFile,
} from "../../../composables/_storage";
import { useConsoleTableState } from "../../../composables/_table";

function stateTag(state?: string) {
    if (state === "ACTIVE") {
        return <Tag color="green">可用</Tag>;
    }

    if (state === "TRASHED") {
        return <Tag>回收站</Tag>;
    }

    return <Tag color="blue">{state ?? "—"}</Tag>;
}

export function StorageFilesView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [selected, setSelected] = useState<StorageFile>();
    const filesQuery = useQuery({
        queryKey: ["console", "storage-files", table.page, table.pageSize],
        queryFn: async () => listStorageFiles({ page: table.page, size: table.pageSize }),
    });
    const changeState = useMutation({
        mutationFn: async ({ action, name }: { action: "purge" | "restore" | "trash"; name: string }) => {
            if (action === "trash") {
                await trashStorageFile(name);
                return;
            }

            if (action === "restore") {
                await restoreStorageFile(name);
                return;
            }

            await purgeStorageFile(name);
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("存储文件状态已更新。");
            void queryClient.invalidateQueries({ queryKey: ["console", "storage-files"] });
        },
    });

    const handleDownload = async (file: StorageFile) => {
        try {
            const url = await createStorageFileDownloadUrl(file.metadata.name);

            if (!url) {
                prompt.error("未获取到下载地址。");
                return;
            }

            window.open(url, "_blank", "noopener,noreferrer");
        } catch (error) {
            prompt.error(consoleErrorMessage(error));
        }
    };

    const confirmPurge = (file: StorageFile) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定彻底删除 ${file.spec.originalName ?? file.metadata.name} 吗？该操作不可恢复。`,
            okButtonProps: { danger: true },
            okText: "彻底删除",
            onOk: () => changeState.mutateAsync({ action: "purge", name: file.metadata.name }),
            title: "彻底删除文件",
        });
    };

    const columns: TableProps<StorageFile>["columns"] = [
        {
            key: "originalName",
            render: (_, file) => file.spec.originalName ?? file.metadata.name,
            title: "文件名",
        },
        {
            dataIndex: ["spec", "policy"],
            key: "policy",
            render: (value: string) => value ?? "—",
            title: "存储策略",
        },
        {
            dataIndex: ["spec", "size"],
            key: "size",
            render: (value: number) => formatBytes(value),
            title: "大小",
        },
        {
            dataIndex: ["spec", "state"],
            key: "state",
            render: (value: string) => stateTag(value),
            title: "状态",
        },
        {
            dataIndex: ["spec", "owner"],
            key: "owner",
            render: (value: string) => value ?? "—",
            title: "所有者",
        },
        {
            dataIndex: ["spec", "uploadedAt"],
            key: "uploadedAt",
            render: (value: string) => formatDateTime(value),
            title: "上传时间",
        },
        {
            key: "actions",
            render: (_, file) => (
                <Space>
                    <Button onClick={() => setSelected(file)} size="small">
                        详情
                    </Button>
                    {file.spec.state === "TRASHED" ? (
                        <>
                            <Button
                                loading={changeState.isPending}
                                onClick={() => changeState.mutate({ action: "restore", name: file.metadata.name })}
                                size="small"
                            >
                                恢复
                            </Button>
                            <Button danger onClick={() => confirmPurge(file)} size="small">
                                彻底删除
                            </Button>
                        </>
                    ) : (
                        <>
                            <Button onClick={() => void handleDownload(file)} size="small">
                                下载
                            </Button>
                            <Button
                                loading={changeState.isPending}
                                onClick={() => changeState.mutate({ action: "trash", name: file.metadata.name })}
                                size="small"
                            >
                                回收站
                            </Button>
                        </>
                    )}
                </Space>
            ),
            title: "操作",
        },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={filesQuery.data?.items}
                error={filesQuery.error}
                extra={
                    <Button onClick={() => void filesQuery.refetch()} size="small">
                        刷新
                    </Button>
                }
                loading={filesQuery.isPending}
                onRetry={() => void filesQuery.refetch()}
                pagination={{ ...table.pagination, total: filesQuery.data?.total ?? 0 }}
                rowKey={(file) => file.metadata.name}
                title="存储文件"
            />
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.spec.originalName ?? selected?.metadata.name}
                width={720}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </>
    );
}
