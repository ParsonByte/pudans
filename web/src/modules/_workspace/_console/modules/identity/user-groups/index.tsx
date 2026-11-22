"use client";

import { useState } from "react";

import { type UserGroup, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Form, Input, Modal, Select, Space, type TableProps } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

const DISPLAY_NAME_KEY = "rbac.authorization.pudans.tolim.cn/display-name";
const TEMPLATE_LABEL = "pudans.tolim.cn/role-template";

type UserGroupFormValues = {
    description?: string;
    displayName: string;
    name: string;
    roles?: string[];
};

export function UserGroupsView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<UserGroupFormValues>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<UserGroup>();
    const [selected, setSelected] = useState<UserGroup>();
    const groupsQuery = useQuery({
        queryKey: ["console", "user-groups", table.page, table.pageSize],
        queryFn: async () => (await apiClient.userGroup.listUserGroup({ page: table.page, size: table.pageSize })).data,
    });
    const rolesQuery = useQuery({
        queryKey: ["console", "roles", "options"],
        queryFn: async () =>
            (
                await apiClient.role.listRole({
                    labelSelector: [`${TEMPLATE_LABEL}!=true`],
                    page: 1,
                    size: 200,
                })
            ).data,
    });
    const roleOptions = (rolesQuery.data?.items ?? [])
        .filter((role) => role.metadata.labels?.[TEMPLATE_LABEL] !== "true")
        .map((role) => ({
            label: role.metadata.annotations?.[DISPLAY_NAME_KEY] ?? role.metadata.name,
            value: role.metadata.name,
        }));
    const saveGroup = useMutation({
        mutationFn: async ({ record, values }: { record?: UserGroup; values: UserGroupFormValues }) => {
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                description: values.description,
                displayName: values.displayName,
                kind: "UserGroup",
                metadata: { ...record?.metadata, name: record?.metadata.name ?? values.name },
                roleRefs: (values.roles ?? []).map((name) => ({
                    apiGroup: "pudans.tolim.cn",
                    kind: "Role",
                    name,
                })),
            };

            if (record) {
                await apiClient.userGroup.updateUserGroup({ name: record.metadata.name, userGroup: payload });
                return;
            }

            await apiClient.userGroup.createUserGroup({ userGroup: payload });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("用户组已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "user-groups"] });
        },
    });
    const deleteGroup = useMutation({
        mutationFn: async (name: string) => apiClient.userGroup.deleteUserGroup({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("用户组已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "user-groups"] });
        },
    });

    const openCreate = () => {
        setEditing(undefined);
        form.resetFields();
        setOpen(true);
    };

    const openEdit = (group: UserGroup) => {
        setEditing(group);
        form.resetFields();
        form.setFieldsValue({
            description: group.description,
            displayName: group.displayName,
            name: group.metadata.name,
            roles: (group.roleRefs ?? []).map((ref) => ref.name).filter((name): name is string => Boolean(name)),
        });
        setOpen(true);
    };

    const confirmDelete = (group: UserGroup) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除用户组 ${group.displayName} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteGroup.mutateAsync(group.metadata.name),
            title: "删除用户组",
        });
    };

    const columns: TableProps<UserGroup>["columns"] = [
        { dataIndex: "displayName", key: "displayName", title: "名称" },
        { dataIndex: ["metadata", "name"], key: "metadataName", title: "标识" },
        { dataIndex: "description", key: "description", render: (value: string) => value ?? "—", title: "描述" },
        {
            key: "roles",
            render: (_, group) => group.roleRefs?.length ?? 0,
            title: "角色数",
        },
        {
            dataIndex: ["metadata", "creationTimestamp"],
            key: "creationTimestamp",
            render: (value: string) => formatDateTime(value),
            title: "创建时间",
        },
        {
            key: "actions",
            render: (_, group) => (
                <Space>
                    <Button onClick={() => openEdit(group)} size="small">
                        编辑
                    </Button>
                    <Button onClick={() => setSelected(group)} size="small">
                        详情
                    </Button>
                    <Button danger onClick={() => confirmDelete(group)} size="small">
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
                data={groupsQuery.data?.items}
                error={groupsQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建用户组
                        </Button>
                        <Button onClick={() => void groupsQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={groupsQuery.isPending}
                onRetry={() => void groupsQuery.refetch()}
                pagination={{ ...table.pagination, total: groupsQuery.data?.total ?? 0 }}
                rowKey={(group) => group.metadata.name}
                title="用户组"
            />
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveGroup.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title={editing ? "编辑用户组" : "新建用户组"}
                width={560}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveGroup.mutate({ record: editing, values })}
                    requiredMark={false}
                >
                    <Form.Item
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：auditors" />
                    </Form.Item>
                    <Form.Item
                        label="显示名称"
                        name="displayName"
                        rules={[{ message: "请输入显示名称。", required: true, whitespace: true }]}
                    >
                        <Input placeholder="例如：审计组" />
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea placeholder="可选" rows={2} />
                    </Form.Item>
                    <Form.Item label="绑定角色" name="roles">
                        <Select allowClear mode="multiple" options={roleOptions} placeholder="选择角色" />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.displayName}
                width={640}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </>
    );
}
