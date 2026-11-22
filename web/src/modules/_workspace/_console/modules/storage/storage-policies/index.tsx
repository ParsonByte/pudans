"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Modal, Space, type TableProps, Tag } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { type StoragePolicy, deleteStoragePolicy, listStoragePolicies } from "../../../composables/_storage";
import { useConsoleTableState } from "../../../composables/_table";

export function StoragePoliciesView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [selected, setSelected] = useState<StoragePolicy>();
    const policiesQuery = useQuery({
        queryKey: ["console", "storage-policies", table.page, table.pageSize],
        queryFn: async () => listStoragePolicies({ page: table.page, size: table.pageSize }),
    });
    const deletePolicy = useMutation({
        mutationFn: async (name: string) => deleteStoragePolicy(name),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("存储策略已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "storage-policies"] });
        },
    });

    const confirmDelete = (policy: StoragePolicy) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除存储策略 ${policy.spec.displayName ?? policy.metadata.name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deletePolicy.mutateAsync(policy.metadata.name),
            title: "删除存储策略",
        });
    };

    const columns: TableProps<StoragePolicy>["columns"] = [
        {
            key: "displayName",
            render: (_, policy) => policy.spec.displayName ?? policy.metadata.name,
            title: "名称",
        },
        { dataIndex: ["metadata", "name"], key: "name", title: "标识" },
        {
            dataIndex: ["spec", "type"],
            key: "type",
            render: (value: string) => <Tag>{value ?? "—"}</Tag>,
            title: "类型",
        },
        {
            key: "bucket",
            render: (_, policy) => [policy.spec.server, policy.spec.bucketName].filter(Boolean).join(" / ") || "—",
            title: "服务 / 桶",
        },
        {
            key: "ready",
            render: (_, policy) => (
                <Tag color={policy.status?.ready ? "green" : "default"}>{policy.status?.ready ? "就绪" : "未就绪"}</Tag>
            ),
            title: "状态",
        },
        {
            key: "default",
            render: (_, policy) => (policy.spec.defaultPolicy ? <Tag color="blue">默认</Tag> : "—"),
            title: "默认",
        },
        {
            dataIndex: ["metadata", "creationTimestamp"],
            key: "creationTimestamp",
            render: (value: string) => formatDateTime(value),
            title: "创建时间",
        },
        {
            key: "actions",
            render: (_, policy) => (
                <Space>
                    <Button onClick={() => setSelected(policy)} size="small">
                        详情
                    </Button>
                    <Button danger onClick={() => confirmDelete(policy)} size="small">
                        删除
                    </Button>
                </Space>
            ),
            title: "操作",
        },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={policiesQuery.data?.items}
                error={policiesQuery.error}
                extra={
                    <Button onClick={() => void policiesQuery.refetch()} size="small">
                        刷新
                    </Button>
                }
                loading={policiesQuery.isPending}
                onRetry={() => void policiesQuery.refetch()}
                pagination={{ ...table.pagination, total: policiesQuery.data?.total ?? 0 }}
                rowKey={(policy) => policy.metadata.name}
                title="存储策略"
            />
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.spec.displayName ?? selected?.metadata.name}
                width={720}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </>
    );
}
