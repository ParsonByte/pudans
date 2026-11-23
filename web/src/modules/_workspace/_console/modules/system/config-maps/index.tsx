"use client";

import { useState } from "react";

import { type ConfigMap, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, DeleteIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Form, Input } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { GlobalDialog, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { type KeyValueEntry, toKeyValueEntries, toKeyValueRecord } from "../../../composables/_key-value";
import { useConsoleTableState } from "../../../composables/_table";

type ConfigMapFormValues = {
    data?: KeyValueEntry[];
    name: string;
};

type ConfigMapEditorState = {
    record?: ConfigMap;
};

export function ConfigMapsView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [editor, setEditor] = useState<ConfigMapEditorState>();
    const configMapsQuery = useQuery({
        queryKey: ["console", "config-maps", table.page, table.pageSize],
        queryFn: async () => (await apiClient.configMap.listConfigMap({ page: table.page, size: table.pageSize })).data,
    });
    const deleteConfigMap = useMutation({
        mutationFn: async (name: string) => apiClient.configMap.deleteConfigMap({ name }),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "配置字典已删除。" });
            void queryClient.invalidateQueries({ queryKey: ["console", "config-maps"] });
        },
    });

    const confirmDelete = (record: ConfigMap) => {
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除 ${record.metadata.name} 吗？`,
            danger: true,
            onConfirm: () => void deleteConfigMap.mutateAsync(record.metadata.name),
            title: "删除配置字典",
        });
    };

    const columns: TableProps<ConfigMap>["columns"] = [
        {
            cell: ({ row }) => row.metadata.name,
            colKey: "name",
            title: "名称",
        },
        {
            cell: ({ row }) => <span className="tabular-nums">{Object.keys(row.data ?? {}).length}</span>,
            colKey: "keys",
            title: "键数量",
        },
        {
            cell: ({ row }) => formatDateTime(row.metadata.creationTimestamp),
            colKey: "creationTimestamp",
            title: "创建时间",
        },
        {
            align: "right",
            cell: ({ row }) => (
                <div className="flex justify-end gap-1">
                    <Button onClick={() => setEditor({ record: row })} size="small" theme="primary" variant="text">
                        编辑
                    </Button>
                    <Button onClick={() => confirmDelete(row)} size="small" theme="danger" variant="text">
                        删除
                    </Button>
                </div>
            ),
            colKey: "actions",
            title: "操作",
        },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={configMapsQuery.data?.items}
                error={configMapsQuery.error}
                extra={
                    <div className="flex items-center gap-2">
                        <Button icon={<AddIcon />} onClick={() => setEditor({})} size="small" theme="primary">
                            新建配置字典
                        </Button>
                        <Button
                            icon={<RefreshIcon />}
                            onClick={() => void configMapsQuery.refetch()}
                            size="small"
                            variant="outline"
                        >
                            刷新
                        </Button>
                    </div>
                }
                loading={configMapsQuery.isPending}
                onRetry={() => void configMapsQuery.refetch()}
                pagination={{ ...table.pagination, total: configMapsQuery.data?.total ?? 0 }}
                rowKey="metadata.name"
                title="配置字典"
            />
            {editor ? (
                <ConfigMapDialog
                    key={editor.record?.metadata.name ?? "new"}
                    onClose={() => setEditor(undefined)}
                    record={editor.record}
                />
            ) : null}
        </>
    );
}

function ConfigMapDialog({ onClose, record }: { onClose: () => void; record?: ConfigMap }) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const initialData: ConfigMapFormValues = {
        data: toKeyValueEntries(record?.data),
        name: record?.metadata.name ?? "",
    };
    const saveConfigMap = useMutation({
        mutationFn: async (values: ConfigMapFormValues) => {
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                data: toKeyValueRecord(values.data),
                kind: "ConfigMap",
                metadata: { name: record?.metadata.name ?? values.name },
            };

            if (record) {
                await apiClient.configMap.updateConfigMap({ configMap: payload, name: record.metadata.name });
                return;
            }

            await apiClient.configMap.createConfigMap({ configMap: payload });
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "配置字典已保存。" });
            void queryClient.invalidateQueries({ queryKey: ["console", "config-maps"] });
            onClose();
        },
    });
    const close = () => {
        if (!saveConfigMap.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            confirmLoading={saveConfigMap.isPending}
            footer={
                <div className="flex justify-end gap-2">
                    <Button disabled={saveConfigMap.isPending} onClick={close} variant="outline">
                        取消
                    </Button>
                    <Button loading={saveConfigMap.isPending} onClick={() => form.submit()} theme="primary">
                        保存
                    </Button>
                </div>
            }
            onClose={close}
            open
            title={record ? "编辑配置字典" : "新建配置字典"}
            width={640}
        >
            <Form
                form={form}
                initialData={initialData}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        saveConfigMap.mutate(form.getFieldsValue(true) as ConfigMapFormValues);
                    }
                }}
            >
                <Form.FormItem
                    label="名称"
                    name="name"
                    rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                >
                    <Input disabled={Boolean(record)} />
                </Form.FormItem>
                <Form.FormList initialData={initialData.data} name="data">
                    {(fields, { add, remove }) => (
                        <div className="flex flex-col gap-3">
                            {fields.map((field) => (
                                <div
                                    className="grid min-w-0 grid-cols-1 items-start gap-2 sm:grid-cols-[1fr_2fr_auto]"
                                    key={field.key}
                                >
                                    <Form.FormItem
                                        name={[field.name, "key"]}
                                        rules={[{ message: "请输入键。", required: true, whitespace: true }]}
                                    >
                                        <Input placeholder="键" />
                                    </Form.FormItem>
                                    <Form.FormItem name={[field.name, "value"]}>
                                        <Input placeholder="值" />
                                    </Form.FormItem>
                                    <Button
                                        aria-label="删除键值"
                                        icon={<DeleteIcon />}
                                        onClick={() => remove(field.name)}
                                        theme="danger"
                                        variant="text"
                                    />
                                </div>
                            ))}
                            <Button icon={<AddIcon />} onClick={() => add({ key: "", value: "" })} variant="dashed">
                                添加键值
                            </Button>
                        </div>
                    )}
                </Form.FormList>
            </Form>
        </GlobalDialog>
    );
}
