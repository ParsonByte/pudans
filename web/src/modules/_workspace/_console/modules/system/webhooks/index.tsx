"use client";

import { useState } from "react";

import { type Webhook, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, type TableProps, Tag } from "antd";

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

export function WebhooksView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<WebhookFormValues>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<WebhookRecord>();
    const webhooksQuery = useQuery({
        queryKey: ["console", "webhooks", table.page, table.pageSize],
        queryFn: async () => (await apiClient.webhook.listWebhook({ page: table.page, size: table.pageSize })).data,
    });
    const records = (webhooksQuery.data?.items ?? []) as unknown as WebhookRecord[];
    const saveWebhook = useMutation({
        mutationFn: async ({ record, values }: { record?: WebhookRecord; values: WebhookFormValues }) => {
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
                    maxAttempts: values.maxAttempts ?? 3,
                    timeoutMillis: values.timeoutMillis ?? 5000,
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
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("Webhook 已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "webhooks"] });
        },
    });
    const deleteWebhook = useMutation({
        mutationFn: async (name: string) => apiClient.webhook.deleteWebhook({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("Webhook 已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "webhooks"] });
        },
    });

    const openCreate = () => {
        setEditing(undefined);
        form.resetFields();
        form.setFieldsValue({ enabled: true, events: [], maxAttempts: 3, timeoutMillis: 5000 });
        setOpen(true);
    };

    const openEdit = (record: WebhookRecord) => {
        setEditing(record);
        form.resetFields();
        form.setFieldsValue({
            description: record.spec?.description,
            enabled: record.spec?.enabled ?? true,
            events: record.spec?.events ?? [],
            maxAttempts: record.spec?.maxAttempts ?? 3,
            name: record.metadata.name,
            timeoutMillis: record.spec?.timeoutMillis ?? 5000,
            url: record.spec?.url ?? "",
        });
        setOpen(true);
    };

    const confirmDelete = (record: WebhookRecord) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除 ${record.metadata.name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteWebhook.mutateAsync(record.metadata.name),
            title: "删除 Webhook",
        });
    };

    const columns: TableProps<WebhookRecord>["columns"] = [
        { dataIndex: ["metadata", "name"], key: "name", title: "名称" },
        {
            key: "url",
            render: (_, record) => (
                <span className="text-xs break-all text-neutral-500">{record.spec?.url ?? "—"}</span>
            ),
            title: "地址",
        },
        {
            key: "events",
            render: (_, record) => (
                <Space size={4} wrap>
                    {(record.spec?.events ?? []).slice(0, 3).map((event) => (
                        <Tag key={event}>{event}</Tag>
                    ))}
                    {(record.spec?.events ?? []).length > 3 ? (
                        <Tag>+{(record.spec?.events?.length ?? 0) - 3}</Tag>
                    ) : null}
                </Space>
            ),
            title: "事件",
        },
        {
            key: "enabled",
            render: (_, record) => (
                <Tag color={record.spec?.enabled === false ? "default" : "green"}>
                    {record.spec?.enabled === false ? "停用" : "启用"}
                </Tag>
            ),
            title: "状态",
        },
        {
            key: "delivery",
            render: (_, record) => (
                <div className="text-xs">
                    <div>{record.spec?.lastDeliveryStatus ?? "—"}</div>
                    <div className="text-neutral-500">{formatDateTime(record.spec?.lastDeliveredAt)}</div>
                </div>
            ),
            title: "最近投递",
        },
        {
            key: "failureCount",
            render: (_, record) => record.spec?.failureCount ?? 0,
            title: "失败次数",
        },
        {
            key: "actions",
            render: (_, record) => (
                <Space>
                    <Button onClick={() => openEdit(record)} size="small">
                        编辑
                    </Button>
                    <Button danger onClick={() => confirmDelete(record)} size="small">
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
                data={records}
                error={webhooksQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建 Webhook
                        </Button>
                        <Button onClick={() => void webhooksQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={webhooksQuery.isPending}
                onRetry={() => void webhooksQuery.refetch()}
                pagination={{ ...table.pagination, total: webhooksQuery.data?.total ?? 0 }}
                rowKey={(record) => record.metadata.name}
                title="Webhook"
            />
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveWebhook.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title={editing ? "编辑 Webhook" : "新建 Webhook"}
                width={560}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveWebhook.mutate({ record: editing, values })}
                    requiredMark={false}
                >
                    <Form.Item
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：audit-forward" />
                    </Form.Item>
                    <Form.Item
                        label="地址"
                        name="url"
                        rules={[
                            { message: "请输入地址。", required: true, whitespace: true },
                            { message: "请输入有效的 URL。", type: "url" },
                        ]}
                    >
                        <Input placeholder="https://example.com/webhook" />
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea placeholder="可选" rows={2} />
                    </Form.Item>
                    <Form.Item label="事件" name="events">
                        <Select mode="tags" placeholder="输入事件名后回车，* 表示全部" tokenSeparators={[","]} />
                    </Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <Form.Item className="!mb-0" label="最大尝试次数" name="maxAttempts">
                            <InputNumber className="w-full" min={1} precision={0} />
                        </Form.Item>
                        <Form.Item className="!mb-0" label="超时（毫秒）" name="timeoutMillis">
                            <InputNumber className="w-full" min={1000} precision={0} step={1000} />
                        </Form.Item>
                    </div>
                    <Form.Item extra="编辑时留空表示保持原密钥。" label="签名密钥" name="secret">
                        <Input.Password placeholder="可选" />
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
}
