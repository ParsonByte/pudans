"use client";

import { useState } from "react";

import { type AuthProvider, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Form, Input, Modal, Select, Space, Switch, type TableProps, Tag } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleTableState } from "../../../composables/_table";

const AUTH_TYPE_OPTIONS = [
    { label: "表单", value: "FORM" },
    { label: "OAuth2", value: "OAUTH2" },
];

type AuthProviderFormValues = {
    authType: "FORM" | "OAUTH2";
    authenticationUrl: string;
    bindingUrl?: string;
    description?: string;
    displayName: string;
    helpPage?: string;
    logo?: string;
    name: string;
    rememberMeSupport?: boolean;
    unbindUrl?: string;
    website?: string;
};

export function AuthProvidersView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<AuthProviderFormValues>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<AuthProvider>();
    const [selected, setSelected] = useState<AuthProvider>();
    const providersQuery = useQuery({
        queryKey: ["console", "auth-providers", table.page, table.pageSize],
        queryFn: async () =>
            (await apiClient.authProvider.listAuthProvider({ page: table.page, size: table.pageSize })).data,
    });
    const saveProvider = useMutation({
        mutationFn: async ({ record, values }: { record?: AuthProvider; values: AuthProviderFormValues }) => {
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                kind: "AuthProvider",
                metadata: { ...record?.metadata, name: record?.metadata.name ?? values.name },
                spec: {
                    authType: values.authType,
                    authenticationUrl: values.authenticationUrl,
                    bindingUrl: values.bindingUrl,
                    description: values.description,
                    displayName: values.displayName,
                    helpPage: values.helpPage,
                    logo: values.logo,
                    rememberMeSupport: values.rememberMeSupport ?? false,
                    unbindUrl: values.unbindUrl,
                    website: values.website,
                },
            };

            if (record) {
                await apiClient.authProvider.updateAuthProvider({
                    authProvider: payload,
                    name: record.metadata.name,
                });
                return;
            }

            await apiClient.authProvider.createAuthProvider({ authProvider: payload });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("身份提供商已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "auth-providers"] });
        },
    });
    const toggleProvider = useMutation({
        mutationFn: async ({ enable, name }: { enable: boolean; name: string }) => {
            if (enable) {
                await apiClient.authProvider.enableAuthProvider({ name });
                return;
            }

            await apiClient.authProvider.disableAuthProvider({ name });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("身份提供商状态已更新。");
            void queryClient.invalidateQueries({ queryKey: ["console", "auth-providers"] });
        },
    });
    const deleteProvider = useMutation({
        mutationFn: async (name: string) => apiClient.authProvider.deleteAuthProvider({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("身份提供商已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "auth-providers"] });
        },
    });

    const openCreate = () => {
        setEditing(undefined);
        form.resetFields();
        form.setFieldsValue({ authType: "OAUTH2", rememberMeSupport: false });
        setOpen(true);
    };

    const openEdit = (provider: AuthProvider) => {
        setEditing(provider);
        form.resetFields();
        form.setFieldsValue({
            authType: provider.spec.authType,
            authenticationUrl: provider.spec.authenticationUrl,
            bindingUrl: provider.spec.bindingUrl,
            description: provider.spec.description,
            displayName: provider.spec.displayName,
            helpPage: provider.spec.helpPage,
            logo: provider.spec.logo,
            name: provider.metadata.name,
            rememberMeSupport: provider.spec.rememberMeSupport ?? false,
            unbindUrl: provider.spec.unbindUrl,
            website: provider.spec.website,
        });
        setOpen(true);
    };

    const confirmDelete = (provider: AuthProvider) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除身份提供商 ${provider.spec.displayName} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteProvider.mutateAsync(provider.metadata.name),
            title: "删除身份提供商",
        });
    };

    const columns: TableProps<AuthProvider>["columns"] = [
        { dataIndex: ["spec", "displayName"], key: "displayName", title: "名称" },
        { dataIndex: ["metadata", "name"], key: "metadataName", title: "标识" },
        {
            dataIndex: ["spec", "authType"],
            key: "authType",
            render: (value: string) => <Tag>{value}</Tag>,
            title: "类型",
        },
        {
            dataIndex: ["spec", "authenticationUrl"],
            key: "authenticationUrl",
            render: (value: string) => (
                <span className="text-xs break-all text-neutral-500" title={value}>
                    {value}
                </span>
            ),
            title: "认证地址",
        },
        {
            dataIndex: ["spec", "description"],
            key: "description",
            render: (value: string) => value ?? "—",
            title: "描述",
        },
        {
            key: "actions",
            render: (_, provider) => (
                <Space>
                    <Button onClick={() => openEdit(provider)} size="small">
                        编辑
                    </Button>
                    <Button
                        loading={toggleProvider.isPending}
                        onClick={() => toggleProvider.mutate({ enable: true, name: provider.metadata.name })}
                        size="small"
                    >
                        启用
                    </Button>
                    <Button
                        loading={toggleProvider.isPending}
                        onClick={() => toggleProvider.mutate({ enable: false, name: provider.metadata.name })}
                        size="small"
                    >
                        禁用
                    </Button>
                    <Button danger onClick={() => confirmDelete(provider)} size="small">
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
                data={providersQuery.data?.items}
                error={providersQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建身份提供商
                        </Button>
                        <Button onClick={() => void providersQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={providersQuery.isPending}
                onRetry={() => void providersQuery.refetch()}
                pagination={{ ...table.pagination, total: providersQuery.data?.total ?? 0 }}
                rowKey={(provider) => provider.metadata.name}
                title="身份提供商"
            />
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveProvider.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title={editing ? "编辑身份提供商" : "新建身份提供商"}
                width={560}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveProvider.mutate({ record: editing, values })}
                    requiredMark={false}
                >
                    <Form.Item
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：feishu" />
                    </Form.Item>
                    <Form.Item
                        label="显示名称"
                        name="displayName"
                        rules={[{ message: "请输入显示名称。", required: true, whitespace: true }]}
                    >
                        <Input placeholder="例如：飞书" />
                    </Form.Item>
                    <Form.Item label="认证类型" name="authType">
                        <Select options={AUTH_TYPE_OPTIONS} />
                    </Form.Item>
                    <Form.Item
                        label="认证地址"
                        name="authenticationUrl"
                        rules={[
                            { message: "请输入认证地址。", required: true, whitespace: true },
                            { message: "请输入有效的 URL。", type: "url" },
                        ]}
                    >
                        <Input placeholder="https://example.com/oauth/authorize" />
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea placeholder="可选" rows={2} />
                    </Form.Item>
                    <Form.Item label="Logo 地址" name="logo">
                        <Input placeholder="可选" />
                    </Form.Item>
                    <Form.Item label="官网" name="website">
                        <Input placeholder="可选" />
                    </Form.Item>
                    <Form.Item label="帮助页" name="helpPage">
                        <Input placeholder="可选" />
                    </Form.Item>
                    <Form.Item label="绑定地址" name="bindingUrl">
                        <Input placeholder="可选" />
                    </Form.Item>
                    <Form.Item label="解绑地址" name="unbindUrl">
                        <Input placeholder="可选" />
                    </Form.Item>
                    <Form.Item label="支持记住登录" name="rememberMeSupport" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.spec.displayName}
                width={640}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </>
    );
}
