"use client";

import { useState } from "react";

import { type ListedUser, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Form, Input, Modal, Select, Space, type TableProps, Tag } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

const STATUS_OPTIONS = [
    { label: "正常", value: "active" },
    { label: "已禁用", value: "disabled" },
];

type UserFormValues = {
    bio?: string;
    displayName?: string;
    email?: string;
    password?: string;
    phone?: string;
    userGroup?: string;
    username: string;
};

export function UsersView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<UserFormValues>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<ListedUser>();
    const [detailLoading, setDetailLoading] = useState(false);
    const status = table.filters.status ?? "";
    const usersQuery = useQuery({
        queryKey: ["console", "users", table.page, table.pageSize, table.keyword, status],
        queryFn: async () =>
            (
                await apiClient.user.listUsers({
                    keyword: table.keyword || undefined,
                    page: table.page,
                    size: table.pageSize,
                    status: status || undefined,
                })
            ).data,
    });
    const groupsQuery = useQuery({
        queryKey: ["console", "user-groups", "options"],
        queryFn: async () => (await apiClient.userGroup.listUserGroup({ page: 1, size: 200 })).data,
    });
    const groupOptions = (groupsQuery.data?.items ?? []).map((group) => ({
        label: group.displayName,
        value: group.metadata.name,
    }));
    const saveUser = useMutation({
        mutationFn: async ({ record, values }: { record?: ListedUser; values: UserFormValues }) => {
            if (!record) {
                await apiClient.user.createLocalUser({
                    localCreateUserRequest: {
                        displayName: values.displayName,
                        email: values.email,
                        password: values.password,
                        userGroup: values.userGroup,
                        username: values.username,
                    },
                });
                return;
            }

            if (!record.name) {
                throw new Error("用户名称缺失。");
            }

            await apiClient.user.updateUser({
                name: record.name,
                updateProfileRequest: {
                    bio: values.bio,
                    displayName: values.displayName,
                    email: values.email,
                    phone: values.phone,
                },
            });

            const previousGroup = record.userGroup ?? "";
            const nextGroup = values.userGroup ?? "";

            if (nextGroup !== previousGroup) {
                if (nextGroup) {
                    await apiClient.user.assignUserGroup({
                        assignGroupRequest: { userGroup: nextGroup },
                        name: record.name,
                    });
                } else {
                    await apiClient.user.removeUserGroup({ name: record.name });
                }
            }
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("用户已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "users"] });
        },
    });
    const toggleUser = useMutation({
        mutationFn: async (user: ListedUser) => {
            if (!user.name) {
                throw new Error("用户名称缺失。");
            }

            if (user.disabled) {
                await apiClient.user.enableUser({ username: user.name });
                return;
            }

            await apiClient.user.disableUser({ username: user.name });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("用户状态已更新。");
            void queryClient.invalidateQueries({ queryKey: ["console", "users"] });
        },
    });
    const deleteUser = useMutation({
        mutationFn: async (name: string) => apiClient.user.deleteUser({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("用户已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "users"] });
        },
    });

    const openCreate = () => {
        setEditing(undefined);
        form.resetFields();
        setOpen(true);
    };

    const openEdit = async (user: ListedUser) => {
        if (!user.name) {
            prompt.error("用户名称缺失。");
            return;
        }

        setDetailLoading(true);

        try {
            const detail = (await apiClient.user.getUser({ name: user.name })).data;
            setEditing(user);
            form.resetFields();
            form.setFieldsValue({
                bio: detail.spec.bio,
                displayName: detail.spec.displayName,
                email: detail.spec.email,
                phone: detail.spec.phone,
                userGroup: user.userGroup,
                username: user.name,
            });
            setOpen(true);
        } catch (error) {
            prompt.error(consoleErrorMessage(error));
        } finally {
            setDetailLoading(false);
        }
    };

    const confirmDelete = (user: ListedUser) => {
        if (!user.name) {
            prompt.error("用户名称缺失。");
            return;
        }

        modal.confirm({
            cancelText: "取消",
            content: `确定删除用户 ${user.displayName ?? user.name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteUser.mutateAsync(user.name as string),
            title: "删除用户",
        });
    };

    const columns: TableProps<ListedUser>["columns"] = [
        {
            dataIndex: "displayName",
            key: "displayName",
            render: (_, user) => (
                <div>
                    <div className="font-medium">{user.displayName ?? user.name ?? "—"}</div>
                    <div className="text-xs text-neutral-500">{user.name}</div>
                </div>
            ),
            title: "用户",
        },
        { dataIndex: "email", key: "email", title: "邮箱" },
        { dataIndex: "phone", key: "phone", title: "手机号" },
        {
            dataIndex: "userGroupDisplayName",
            key: "userGroupDisplayName",
            render: (value, user) => value ?? user.userGroup ?? "—",
            title: "用户组",
        },
        {
            dataIndex: "disabled",
            key: "disabled",
            render: (disabled: boolean) => (
                <Tag color={disabled ? "default" : "green"}>{disabled ? "已禁用" : "正常"}</Tag>
            ),
            title: "状态",
        },
        {
            dataIndex: "registeredAt",
            key: "registeredAt",
            render: (value: string) => formatDateTime(value),
            title: "注册时间",
        },
        {
            key: "actions",
            render: (_, user) => (
                <Space>
                    <Button loading={detailLoading} onClick={() => void openEdit(user)} size="small">
                        编辑
                    </Button>
                    <Button loading={toggleUser.isPending} onClick={() => toggleUser.mutate(user)} size="small">
                        {user.disabled ? "启用" : "禁用"}
                    </Button>
                    <Button danger onClick={() => confirmDelete(user)} size="small">
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
                data={usersQuery.data?.items}
                error={usersQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建用户
                        </Button>
                        <Button onClick={() => void usersQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={usersQuery.isPending}
                onRetry={() => void usersQuery.refetch()}
                pagination={{ ...table.pagination, total: usersQuery.data?.total ?? 0 }}
                rowKey={(user) => user.name ?? ""}
                title="用户"
                toolbar={
                    <Space wrap>
                        <Input.Search
                            allowClear
                            defaultValue={table.keyword}
                            key={table.keyword}
                            onSearch={table.setKeyword}
                            placeholder="搜索显示名称或邮箱"
                            style={{ width: 280 }}
                        />
                        <Select
                            allowClear
                            onChange={(value) => table.setFilter("status", value ?? null)}
                            options={STATUS_OPTIONS}
                            placeholder="账号状态"
                            style={{ width: 160 }}
                            value={status || undefined}
                        />
                        <Button onClick={() => table.reset()}>重置</Button>
                    </Space>
                }
            />
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveUser.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title={editing ? "编辑用户" : "新建用户"}
                width={560}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveUser.mutate({ record: editing, values })}
                    requiredMark={false}
                >
                    <Form.Item
                        label="用户名"
                        name="username"
                        rules={[
                            { message: "请输入用户名。", required: true, whitespace: true },
                            { message: "用户名至少需要 4 个字符。", min: 4 },
                            { message: "用户名不能超过 63 个字符。", max: 63 },
                        ]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：alice" />
                    </Form.Item>
                    <Form.Item
                        label="显示名称"
                        name="displayName"
                        rules={[{ message: "请输入显示名称。", required: true, whitespace: true }]}
                    >
                        <Input placeholder="例如：Alice" />
                    </Form.Item>
                    <Form.Item label="邮箱" name="email" rules={[{ message: "请输入有效的邮箱地址。", type: "email" }]}>
                        <Input placeholder="可选" />
                    </Form.Item>
                    {editing ? (
                        <>
                            <Form.Item label="手机号" name="phone">
                                <Input placeholder="可选" />
                            </Form.Item>
                            <Form.Item label="简介" name="bio">
                                <Input.TextArea placeholder="可选" rows={2} />
                            </Form.Item>
                        </>
                    ) : null}
                    <Form.Item label="用户组" name="userGroup">
                        <Select allowClear options={groupOptions} placeholder="可选" />
                    </Form.Item>
                    {!editing ? (
                        <Form.Item
                            label="初始密码"
                            name="password"
                            rules={[
                                { message: "请输入密码。", required: true },
                                { message: "密码至少需要 8 个字符。", min: 8 },
                                { message: "密码不能超过 257 个字符。", max: 257 },
                            ]}
                        >
                            <Input.Password autoComplete="new-password" placeholder="至少 8 位" />
                        </Form.Item>
                    ) : null}
                </Form>
            </Modal>
        </>
    );
}
