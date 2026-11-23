"use client";

import { useState } from "react";

import { type Webhook, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Form, Input, InputNumber, Select, Switch, Tag, Textarea, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { GlobalDialog, confirmGlobalDialog, useDesignTokens } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

type WebhookSpec = {
    description?: string;
    enabled?: boolean;
    events?: string[];
    failureCount?: number;
    headers?: Record<string, string>;
    lastDeliveredAt?: string;
    lastDeliveryStatus?: string;
    maxAttempts?: number;
    timeoutMillis?: number;
    url?: string;
};

type WebhookRecord = Omit<Webhook, "spec"> & {
    spec?: WebhookSpec;
};

type WebhookFormValues = {
    description?: string;
    enabled?: boolean;
    events?: string[];
    maxAttempts?: number;
    name: string;
    secret?: string;
    timeoutMillis?: number;
    url: string;
};

type WebhookEditorState = {
    record?: WebhookRecord;
};

const DEFAULT_MAX_ATTEMPTS = 3;
const DEFAULT_TIMEOUT_MILLIS = 5000;

export function WebhooksView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const token = useDesignTokens();
    const [editor, setEditor] = useState<WebhookEditorState>();
    const webhooksQuery = useQuery({
        queryKey: ["console", "webhooks", table.page, table.pageSize],
        queryFn: async () => (await apiClient.webhook.listWebhook({ page: table.page, size: table.pageSize })).data,
    });
    const records = (webhooksQuery.data?.items ?? []) as unknown as WebhookRecord[];
    const deleteWebhook = useMutation({
        mutationFn: async (name: string) => apiClient.webhook.deleteWebhook({ name }),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "Webhook 已删除。" });
            void queryClient.invalidateQueries({ queryKey: ["console", "webhooks"] });
        },
    });

    const confirmDelete = (record: WebhookRecord) => {
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除 ${record.metadata.name} 吗？`,
            danger: true,
            onConfirm: () => void deleteWebhook.mutateAsync(record.metadata.name),
            title: "删除 Webhook",
        });
    };

    const columns: TableProps<WebhookRecord>["columns"] = [
        {
            cell: ({ row }) => row.metadata.name,
            colKey: "name",
            title: "名称",
            width: 180,
        },
        {
            cell: ({ row }) => {
                const url = row.spec?.url ?? "—";

                return (
                    <Tooltip content={url}>
                        <span
                            className="block max-w-[300px] truncate font-mono text-xs"
                            style={{ color: token.colorTextSecondary }}
                        >
                            {url}
                        </span>
                    </Tooltip>
                );
            },
            colKey: "url",
            title: "地址",
            width: 300,
        },
        {
            cell: ({ row }) => {
                const events = row.spec?.events ?? [];

                return (
                    <div className="flex flex-wrap gap-1">
                        {events.slice(0, 3).map((event) => (
                            <Tag key={event} size="small" variant="light">
                                {event}
                            </Tag>
                        ))}
                        {events.length > 3 ? (
                            <Tag size="small" variant="light">
                                +{events.length - 3}
                            </Tag>
                        ) : null}
                    </div>
                );
            },
            colKey: "events",
            title: "事件",
            width: 240,
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.spec?.enabled === false ? "default" : "success"} variant="light">
                    {row.spec?.enabled === false ? "停用" : "启用"}
                </Tag>
            ),
            colKey: "enabled",
            title: "状态",
            width: 100,
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-col gap-0.5 text-xs">
                    <span>{row.spec?.lastDeliveryStatus ?? "—"}</span>
                    <span style={{ color: token.colorTextTertiary }}>{formatDateTime(row.spec?.lastDeliveredAt)}</span>
                </div>
            ),
            colKey: "delivery",
            title: "最近投递",
            width: 180,
        },
        {
            cell: ({ row }) => <span className="tabular-nums">{row.spec?.failureCount ?? 0}</span>,
            colKey: "failureCount",
            title: "失败次数",
            width: 110,
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
            width: 140,
        },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={records}
                error={webhooksQuery.error}
                extra={
                    <div className="flex items-center gap-2">
                        <Button icon={<AddIcon />} onClick={() => setEditor({})} size="small" theme="primary">
                            新建 Webhook
                        </Button>
                        <Button
                            icon={<RefreshIcon />}
                            onClick={() => void webhooksQuery.refetch()}
                            size="small"
                            variant="outline"
                        >
                            刷新
                        </Button>
                    </div>
                }
                loading={webhooksQuery.isPending}
                onRetry={() => void webhooksQuery.refetch()}
                pagination={{ ...table.pagination, total: webhooksQuery.data?.total ?? 0 }}
                rowKey="metadata.name"
                title="Webhook"
            />
            {editor ? (
                <WebhookDialog
                    key={editor.record?.metadata.name ?? "new"}
                    onClose={() => setEditor(undefined)}
                    record={editor.record}
                />
            ) : null}
        </>
    );
}

function WebhookDialog({ onClose, record }: { onClose: () => void; record?: WebhookRecord }) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const initialData: WebhookFormValues = {
        description: record?.spec?.description,
        enabled: record?.spec?.enabled ?? true,
        events: record?.spec?.events ?? [],
        maxAttempts: record?.spec?.maxAttempts ?? DEFAULT_MAX_ATTEMPTS,
        name: record?.metadata.name ?? "",
        timeoutMillis: record?.spec?.timeoutMillis ?? DEFAULT_TIMEOUT_MILLIS,
        url: record?.spec?.url ?? "",
    };
    const eventOptions = (initialData.events ?? []).map((event) => ({ label: event, value: event }));
    const saveWebhook = useMutation({
        mutationFn: async (values: WebhookFormValues) => {
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                kind: "Webhook",
                metadata: { name: record?.metadata.name ?? values.name },
                spec: {
                    description: values.description,
                    enabled: values.enabled ?? true,
                    events: values.events ?? [],
                    failureCount: record?.spec?.failureCount ?? 0,
                    headers: record?.spec?.headers ?? {},
                    lastDeliveredAt: record?.spec?.lastDeliveredAt,
                    lastDeliveryStatus: record?.spec?.lastDeliveryStatus,
                    maxAttempts: values.maxAttempts ?? DEFAULT_MAX_ATTEMPTS,
                    timeoutMillis: values.timeoutMillis ?? DEFAULT_TIMEOUT_MILLIS,
                    url: values.url,
                    ...(values.secret ? { secret: values.secret } : {}),
                },
            };

            if (record) {
                await apiClient.webhook.updateWebhook({
                    name: record.metadata.name,
                    webhook: payload as unknown as Webhook,
                });
                return;
            }

            await apiClient.webhook.createWebhook({ webhook: payload as unknown as Webhook });
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "Webhook 已保存。" });
            void queryClient.invalidateQueries({ queryKey: ["console", "webhooks"] });
            onClose();
        },
    });
    const close = () => {
        if (!saveWebhook.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            confirmLoading={saveWebhook.isPending}
            footer={
                <div className="flex justify-end gap-2">
                    <Button disabled={saveWebhook.isPending} onClick={close} variant="outline">
                        取消
                    </Button>
                    <Button loading={saveWebhook.isPending} onClick={() => form.submit()} theme="primary">
                        保存
                    </Button>
                </div>
            }
            onClose={close}
            open
            title={record ? "编辑 Webhook" : "新建 Webhook"}
            width={640}
        >
            <Form
                form={form}
                initialData={initialData}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        saveWebhook.mutate(form.getFieldsValue(true) as WebhookFormValues);
                    }
                }}
            >
                <div className="grid gap-x-4 sm:grid-cols-2">
                    <Form.FormItem
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(record)} />
                    </Form.FormItem>
                    <Form.FormItem label="启用" name="enabled">
                        <Switch />
                    </Form.FormItem>
                </div>
                <Form.FormItem
                    label="地址"
                    name="url"
                    rules={[
                        { message: "请输入地址。", required: true, whitespace: true },
                        { message: "请输入有效的 URL。", url: true },
                    ]}
                >
                    <Input />
                </Form.FormItem>
                <Form.FormItem label="描述" name="description">
                    <Textarea autosize={{ maxRows: 4, minRows: 2 }} />
                </Form.FormItem>
                <Form.FormItem label="事件" name="events">
                    <Select
                        creatable
                        filterable
                        multiple
                        options={eventOptions}
                        placeholder="输入事件名后回车，* 表示全部"
                    />
                </Form.FormItem>
                <div className="grid gap-x-4 sm:grid-cols-2">
                    <Form.FormItem label="最大尝试次数" name="maxAttempts">
                        <InputNumber decimalPlaces={0} min={1} style={{ width: "100%" }} />
                    </Form.FormItem>
                    <Form.FormItem label="超时（毫秒）" name="timeoutMillis">
                        <InputNumber decimalPlaces={0} min={1000} step={1000} style={{ width: "100%" }} />
                    </Form.FormItem>
                </div>
                <Form.FormItem label="签名密钥" name="secret">
                    <Input autocomplete="new-password" type="password" />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
