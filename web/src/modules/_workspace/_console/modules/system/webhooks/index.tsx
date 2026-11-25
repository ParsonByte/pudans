"use client";

import { useState } from "react";

import { type Webhook, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, HelpCircleIcon, MoreIcon, RefreshIcon } from "tdesign-icons-react";
import {
    Button,
    Dropdown,
    Empty,
    Form,
    Input,
    InputNumber,
    Select,
    Skeleton,
    Switch,
    Tag,
    Textarea,
    Tooltip,
} from "tdesign-react";
import type { DropdownOption, PaginationProps, TableProps } from "tdesign-react";

import { GlobalDialog, confirmGlobalDialog } from "@/components/ui";
import { usePagination } from "@/hooks/table/use-pagination";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { ConsoleAddCard } from "../../../components/add-card";
import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { ObjectDetails } from "../../../components/object-details";
import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { useConsoleSession } from "../../../composables/_session";
import { IdentityDialogFooter } from "../../identity/components/identity-dialog-footer";

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

type WebhookStatusFilter = "all" | "disabled" | "enabled" | "failed";

const WEBHOOK_PERMISSIONS = {
    create: "webhooks:create",
    delete: "webhooks:delete",
    manage: "webhooks:manage",
    update: "webhooks:update",
    view: "webhooks:view",
};

// 扩展列表缺少专用分页接口，一次取足够条目后在前端筛选与分页。
const WEBHOOK_LIST_SIZE = 1000;
const WEBHOOK_PAGE_SIZE = 20;
const DEFAULT_MAX_ATTEMPTS = 3;
const DEFAULT_TIMEOUT_MILLIS = 5000;

const WEBHOOK_EVENTS = [
    { label: "全部事件", value: "*" },
    { label: "用户创建", value: "user.created" },
    { label: "用户更新", value: "user.updated" },
    { label: "用户删除", value: "user.deleted" },
    { label: "用户组变更", value: "user.group.changed" },
    { label: "临时角色授权", value: "temprole.applied" },
    { label: "临时角色到期", value: "temprole.expired" },
    { label: "直播源状态变更", value: "live.source.state.changed" },
];

const WEBHOOK_STATUS_FILTERS = [
    { label: "全部状态", value: "all" },
    { label: "已启用", value: "enabled" },
    { label: "已停用", value: "disabled" },
    { label: "最近投递失败", value: "failed" },
];

const DELIVERY_STATUS_LABELS: Record<string, string> = {
    FAILURE: "失败",
    SUCCESS: "成功",
};

const EVENTS_HINT = "全部事件表示订阅所有类型；未列出的事件名可在后端补充后在此选择。";
const SECRET_HINT = "签名密钥加密保存，留空表示沿用已保存的密钥。";
const TIMEOUT_HINT = "单次投递等待响应的最长时间。";

function webhookEventLabel(event: string) {
    return WEBHOOK_EVENTS.find((item) => item.value === event)?.label ?? event;
}

function webhookEnabled(record: WebhookRecord) {
    return record.spec?.enabled !== false;
}

function buildWebhookPayload(name: string, spec: WebhookSpec): Webhook {
    return {
        apiVersion: "pudans.tolim.cn/v1alpha1",
        kind: "Webhook",
        metadata: { name },
        spec,
    } as unknown as Webhook;
}

export function WebhooksView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        WEBHOOK_PERMISSIONS.view,
    );
    const canManage = can(WEBHOOK_PERMISSIONS.manage);
    const canCreate = can(WEBHOOK_PERMISSIONS.create);
    const canUpdate = can(WEBHOOK_PERMISSIONS.update);
    const canDelete = can(WEBHOOK_PERMISSIONS.delete);
    const [keyword, setKeyword] = useState("");
    const [statusFilter, setStatusFilter] = useState<WebhookStatusFilter>("all");
    const [editor, setEditor] = useState<WebhookEditorState>();
    const [detailName, setDetailName] = useState<string>();
    const [selectedKeys, setSelectedKeys] = useState<(string | number)[]>();
    const webhooksQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        queryFn: async () => (await apiClient.webhook.listWebhook({ page: 1, size: WEBHOOK_LIST_SIZE })).data,
        queryKey: ["console", "webhooks", "list"],
    });
    const records = (webhooksQuery.data?.items ?? []) as unknown as WebhookRecord[];
    const trimmedKeyword = keyword.trim().toLowerCase();
    const filteredRecords = records.filter((record) => {
        if (statusFilter === "enabled" && !webhookEnabled(record)) {
            return false;
        }

        if (statusFilter === "disabled" && webhookEnabled(record)) {
            return false;
        }

        if (statusFilter === "failed" && record.spec?.lastDeliveryStatus !== "FAILURE") {
            return false;
        }

        if (!trimmedKeyword) {
            return true;
        }

        return [record.metadata.name, record.spec?.url ?? "", record.spec?.description ?? ""].some((value) =>
            value.toLowerCase().includes(trimmedKeyword),
        );
    });
    const pagination = usePagination({ initialPageSize: WEBHOOK_PAGE_SIZE, total: filteredRecords.length });
    const pagedRecords = filteredRecords.slice(
        (pagination.page - 1) * pagination.pageSize,
        pagination.page * pagination.pageSize,
    );
    const invalidate = () => void queryClient.invalidateQueries({ queryKey: ["console", "webhooks"] });
    const saveWebhook = useMutation({
        mutationFn: async (payload: { record?: WebhookRecord; values: WebhookFormValues }) => {
            const spec: WebhookSpec = {
                description: payload.values.description,
                enabled: payload.values.enabled ?? true,
                events: payload.values.events ?? [],
                failureCount: payload.record?.spec?.failureCount ?? 0,
                headers: payload.record?.spec?.headers ?? {},
                lastDeliveredAt: payload.record?.spec?.lastDeliveredAt,
                lastDeliveryStatus: payload.record?.spec?.lastDeliveryStatus,
                maxAttempts: payload.values.maxAttempts ?? DEFAULT_MAX_ATTEMPTS,
                timeoutMillis: payload.values.timeoutMillis ?? DEFAULT_TIMEOUT_MILLIS,
                url: payload.values.url,
                ...(payload.values.secret ? { secret: payload.values.secret } : {}),
            };
            const name = payload.record?.metadata.name ?? payload.values.name;

            if (payload.record) {
                await apiClient.webhook.updateWebhook({ name, webhook: buildWebhookPayload(name, spec) });
                return;
            }

            await apiClient.webhook.createWebhook({ webhook: buildWebhookPayload(name, spec) });
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "Webhook 已保存。" });
            invalidate();
            setEditor(undefined);
        },
    });
    const toggleWebhook = useMutation({
        mutationFn: async (record: WebhookRecord) => {
            const name = record.metadata.name;

            await apiClient.webhook.updateWebhook({
                name,
                webhook: buildWebhookPayload(name, { ...record.spec, enabled: !webhookEnabled(record) }),
            });
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "状态已更新。" });
            invalidate();
        },
    });
    const deleteWebhook = useMutation({
        mutationFn: async (name: string) => apiClient.webhook.deleteWebhook({ name }),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "Webhook 已删除。" });
            invalidate();
        },
    });
    const batchDeleteWebhooks = useMutation({
        mutationFn: async (names: string[]) => {
            const results = await Promise.allSettled(names.map((name) => apiClient.webhook.deleteWebhook({ name })));

            return { failed: results.filter((result) => result.status === "rejected").length, total: names.length };
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: ({ failed, total }) => {
            if (failed === 0) {
                prompt.success({ content: `已删除 ${total} 个 Webhook。` });
            } else {
                prompt.warning({ content: `已删除 ${total - failed} 个，${failed} 个删除失败。` });
            }

            setSelectedKeys(undefined);
            invalidate();
        },
    });
    const refresh = () => void webhooksQuery.refetch();
    const confirmDelete = (record: WebhookRecord) => {
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除 ${record.metadata.name} 吗？删除后不再向该地址投递事件。`,
            danger: true,
            onConfirm: () => void deleteWebhook.mutateAsync(record.metadata.name),
            title: "删除 Webhook",
        });
    };
    const confirmBatchDelete = (names: string[]) => {
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除选中的 ${names.length} 个 Webhook 吗？删除后不再向这些地址投递事件。`,
            danger: true,
            onConfirm: () => batchDeleteWebhooks.mutate(names),
            title: "批量删除 Webhook",
        });
    };
    const rowActions = (record: WebhookRecord): DropdownOption[] => {
        const actions: DropdownOption[] = [{ content: "查看详情", value: "detail" }];

        if (canUpdate || canManage) {
            actions.push({ content: "编辑", value: "edit" });
            actions.push({ content: webhookEnabled(record) ? "停用" : "启用", value: "toggle" });
        }

        if (canDelete || canManage) {
            actions.push({ divider: true, value: "divider" }, { content: "删除", theme: "error", value: "delete" });
        }

        return actions;
    };
    const handleRowAction = (record: WebhookRecord, action: string) => {
        if (action === "detail") {
            setDetailName(record.metadata.name);
            return;
        }

        if (action === "edit") {
            setEditor({ record });
            return;
        }

        if (action === "toggle") {
            toggleWebhook.mutate(record);
            return;
        }

        if (action === "delete") {
            confirmDelete(record);
        }
    };
    const columns: TableProps<WebhookRecord>["columns"] = [
        {
            cell: ({ row }) => <span className="font-medium">{row.metadata.name}</span>,
            colKey: "name",
            title: "名称",
            width: 180,
        },
        {
            cell: ({ row }) => {
                const url = row.spec?.url;

                if (!url) {
                    return <EmptyValue />;
                }

                return (
                    <Tooltip content={url}>
                        <span className="block max-w-[280px] truncate font-mono text-xs">{url}</span>
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

                if (events.length === 0) {
                    return <EmptyValue text="未订阅" />;
                }

                return (
                    <div className="flex flex-wrap gap-1">
                        {events.slice(0, 3).map((event) => (
                            <Tag key={event} size="small" variant="light">
                                {webhookEventLabel(event)}
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
                <Tag theme={webhookEnabled(row) ? "success" : "default"} variant="light">
                    {webhookEnabled(row) ? "启用" : "停用"}
                </Tag>
            ),
            colKey: "enabled",
            title: "状态",
            width: 100,
        },
        {
            cell: ({ row }) => {
                const status = row.spec?.lastDeliveryStatus;

                if (!status) {
                    return <EmptyValue text="尚未投递" />;
                }

                return (
                    <div className="flex flex-col gap-0.5 text-xs">
                        <Tag theme={status === "FAILURE" ? "danger" : "success"} variant="light">
                            {DELIVERY_STATUS_LABELS[status] ?? status}
                        </Tag>
                        <span className="text-[var(--td-text-color-placeholder)]">
                            {formatDateTime(row.spec?.lastDeliveredAt)}
                        </span>
                    </div>
                );
            },
            colKey: "delivery",
            title: "最近投递",
            width: 160,
        },
        {
            cell: ({ row }) => <span className="tabular-nums">{row.spec?.failureCount ?? 0}</span>,
            colKey: "failureCount",
            title: "失败次数",
            width: 100,
        },
        {
            align: "right",
            cell: ({ row }) => {
                const actions = rowActions(row);

                if (actions.length === 0) {
                    return <EmptyValue text="无可用操作" />;
                }

                return (
                    <div className="flex justify-end">
                        <Dropdown
                            onClick={(option) => handleRowAction(row, String(option.value))}
                            options={actions}
                            placement="bottom-right"
                            trigger="click"
                        >
                            <Button
                                aria-label={`${row.metadata.name} 操作`}
                                icon={<MoreIcon />}
                                shape="square"
                                size="small"
                                variant="text"
                            />
                        </Dropdown>
                    </div>
                );
            },
            colKey: "operations",
            fixed: "right",
            title: "操作",
            width: 100,
        },
    ];

    if (sessionQuery.isPending) {
        return <Skeleton animation="gradient" theme="paragraph" />;
    }

    if (sessionQuery.error) {
        return (
            <Empty
                action={
                    <Button onClick={() => void sessionQuery.refetch()} variant="outline">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(sessionQuery.error)}
                type="network-error"
            />
        );
    }

    if (!canView) {
        return <Empty description="当前账号没有查看 Webhook 的权限。" type="fail" />;
    }

    const listEmpty = webhooksQuery.isSuccess && records.length === 0;

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                    <PageBreadcrumb current="Webhook" parents={["系统设置"]} />
                    <div className="flex items-center gap-2">
                        <Button
                            icon={<RefreshIcon />}
                            loading={webhooksQuery.isFetching}
                            onClick={refresh}
                            variant="outline"
                        >
                            刷新
                        </Button>
                        {canCreate || canManage ? (
                            <Button icon={<AddIcon />} onClick={() => setEditor({})} theme="primary">
                                新建 Webhook
                            </Button>
                        ) : null}
                    </div>
                </div>
                {listEmpty ? (
                    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                        <ConsoleAddCard onClick={() => setEditor({})} title="新建 Webhook" />
                    </div>
                ) : (
                    <ConsoleTable<WebhookRecord>
                        columns={columns}
                        data={pagedRecords}
                        error={webhooksQuery.error}
                        loading={webhooksQuery.isPending}
                        onRetry={refresh}
                        pagination={webhookPaginationConfig(pagination, filteredRecords.length)}
                        rowKey="metadata.name"
                        tableProps={{
                            onSelectChange: (keys) => setSelectedKeys(keys as (string | number)[]),
                            selectedRowKeys: selectedKeys,
                        }}
                        title="Webhook 列表"
                        toolbar={
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                                    <Input
                                        clearable
                                        onChange={(value) => {
                                            setKeyword(value);
                                            pagination.setPage(1);
                                        }}
                                        placeholder="搜索名称、地址或描述"
                                        style={{ width: 280 }}
                                        value={keyword}
                                    />
                                    <Select
                                        clearable={false}
                                        onChange={(value) => {
                                            setStatusFilter(String(value) as WebhookStatusFilter);
                                            pagination.setPage(1);
                                        }}
                                        options={WEBHOOK_STATUS_FILTERS}
                                        style={{ width: 160 }}
                                        value={statusFilter}
                                    />
                                </div>
                                <div className="flex items-center gap-3">
                                    {canDelete || canManage ? (
                                        <Dropdown
                                            disabled={!selectedKeys || selectedKeys.length === 0}
                                            onClick={(option) => {
                                                if (
                                                    String(option.value) === "delete" &&
                                                    selectedKeys &&
                                                    selectedKeys.length > 0
                                                ) {
                                                    confirmBatchDelete(selectedKeys.map((key) => String(key)));
                                                }
                                            }}
                                            options={[{ content: "删除所选", theme: "error", value: "delete" }]}
                                            placement="bottom-right"
                                            trigger="click"
                                        >
                                            <Button
                                                disabled={!selectedKeys || selectedKeys.length === 0}
                                                variant="outline"
                                            >
                                                批量操作
                                            </Button>
                                        </Dropdown>
                                    ) : null}
                                    <span className="text-xs text-[var(--td-text-color-placeholder)]">
                                        共 {filteredRecords.length} 条
                                    </span>
                                </div>
                            </div>
                        }
                    />
                )}
            </div>
            {editor ? (
                <WebhookDialog
                    key={editor.record?.metadata.name ?? "new"}
                    onClose={() => setEditor(undefined)}
                    onSubmit={(values) => saveWebhook.mutate({ record: editor.record, values })}
                    pending={saveWebhook.isPending}
                    record={editor.record}
                />
            ) : null}
            {detailName ? <WebhookDetailDialog name={detailName} onClose={() => setDetailName(undefined)} /> : null}
        </>
    );
}

type WebhookDetailDialogProps = {
    name: string;
    onClose: () => void;
};

// 详情走 GET 单条接口，避免列表缓存过期时展示旧数据。
function WebhookDetailDialog({ name, onClose }: WebhookDetailDialogProps) {
    const detailQuery = useQuery({
        queryFn: async () => (await apiClient.webhook.getWebhook({ name })).data as unknown as WebhookRecord,
        queryKey: ["console", "webhooks", "detail", name],
    });

    return (
        <GlobalDialog footer={false} onClose={onClose} open title="Webhook 详情" width={720}>
            {detailQuery.isPending ? <Skeleton animation="gradient" theme="paragraph" /> : null}
            {detailQuery.error ? (
                <Empty
                    action={
                        <Button onClick={() => void detailQuery.refetch()} variant="outline">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(detailQuery.error)}
                    type="network-error"
                />
            ) : null}
            {detailQuery.data ? <ObjectDetails emptyText="未填写" value={detailQuery.data} /> : null}
        </GlobalDialog>
    );
}

function webhookPaginationConfig(pagination: ReturnType<typeof usePagination>, total: number): PaginationProps {
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
        total,
        totalContent: `共 ${total} 条`,
    };
}

type WebhookDialogProps = {
    onClose: () => void;
    onSubmit: (values: WebhookFormValues) => void;
    pending: boolean;
    record?: WebhookRecord;
};

function WebhookDialog({ onClose, onSubmit, pending, record }: WebhookDialogProps) {
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

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={onClose}
                    onSubmit={() => form.submit()}
                    pending={pending}
                    submitLabel="保存"
                />
            }
            onClose={() => {
                if (!pending) {
                    onClose();
                }
            }}
            open
            title={record ? `编辑 Webhook · ${record.metadata.name}` : "新建 Webhook"}
            width={640}
        >
            <Form
                form={form}
                initialData={initialData}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        onSubmit(form.getFieldsValue(true) as WebhookFormValues);
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
                    <Input placeholder="接收事件的地址" />
                </Form.FormItem>
                <div className="grid gap-x-4 sm:grid-cols-2">
                    <Form.FormItem
                        label={
                            <span className="flex items-center gap-1">
                                最大尝试次数
                                <Tooltip content="投递失败后重试的总次数上限。" placement="top-left">
                                    <HelpCircleIcon />
                                </Tooltip>
                            </span>
                        }
                        name="maxAttempts"
                    >
                        <InputNumber decimalPlaces={0} min={1} style={{ width: "100%" }} />
                    </Form.FormItem>
                    <Form.FormItem
                        label={
                            <span className="flex items-center gap-1">
                                超时
                                <Tooltip content={TIMEOUT_HINT} placement="top-left">
                                    <HelpCircleIcon />
                                </Tooltip>
                            </span>
                        }
                        name="timeoutMillis"
                    >
                        <InputNumber decimalPlaces={0} min={1000} step={1000} style={{ width: "100%" }} suffix="毫秒" />
                    </Form.FormItem>
                </div>
                <Form.FormItem
                    label={
                        <span className="flex items-center gap-1">
                            订阅事件
                            <Tooltip content={EVENTS_HINT} placement="top-left">
                                <HelpCircleIcon />
                            </Tooltip>
                        </span>
                    }
                    name="events"
                >
                    <Select filterable multiple options={WEBHOOK_EVENTS} placeholder="选择要订阅的事件" />
                </Form.FormItem>
                <Form.FormItem label="描述" name="description">
                    <Textarea autosize={{ maxRows: 4, minRows: 2 }} placeholder="用途说明" />
                </Form.FormItem>
                <Form.FormItem
                    label={
                        <span className="flex items-center gap-1">
                            签名密钥
                            <Tooltip content={SECRET_HINT} placement="top-left">
                                <HelpCircleIcon />
                            </Tooltip>
                        </span>
                    }
                    name="secret"
                >
                    <Input autocomplete="new-password" type="password" />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
