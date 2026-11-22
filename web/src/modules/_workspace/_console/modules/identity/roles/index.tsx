"use client";

import { useState } from "react";

import { type PolicyRule, type Role, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    App,
    Button,
    Card,
    Descriptions,
    Empty,
    Flex,
    Form,
    Input,
    Modal,
    Select,
    Space,
    Table,
    type TableProps,
    Tag,
} from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

const DISPLAY_NAME_KEY = "rbac.authorization.pudans.tolim.cn/display-name";
const TEMPLATE_LABEL = "pudans.tolim.cn/role-template";
const BUILTIN_LABEL = "pudans.tolim.cn/builtin";

type RoleFormValues = {
    displayName?: string;
    name: string;
    rules?: PolicyRule[];
};

function tagsOf(value?: string[]) {
    return value && value.length > 0 ? (
        <Space size={[4, 4]} wrap>
            {value.map((item) => (
                <Tag key={item}>{item}</Tag>
            ))}
        </Space>
    ) : (
        "—"
    );
}

function isBuiltin(role: Role): boolean {
    return role.metadata.labels?.[BUILTIN_LABEL] === "true";
}

export function RolesView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<RoleFormValues>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<Role>();
    const [selected, setSelected] = useState<Role>();
    const rolesQuery = useQuery({
        queryKey: ["console", "roles", table.page, table.pageSize],
        queryFn: async () =>
            (
                await apiClient.role.listRole({
                    labelSelector: [`${TEMPLATE_LABEL}!=true`],
                    page: table.page,
                    size: table.pageSize,
                })
            ).data,
    });
    const roles = (rolesQuery.data?.items ?? []).filter((role) => role.metadata.labels?.[TEMPLATE_LABEL] !== "true");
    const saveRole = useMutation({
        mutationFn: async ({ record, values }: { record?: Role; values: RoleFormValues }) => {
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                kind: "Role",
                metadata: {
                    ...record?.metadata,
                    annotations: {
                        ...record?.metadata.annotations,
                        [DISPLAY_NAME_KEY]: values.displayName ?? "",
                    },
                    name: record?.metadata.name ?? values.name,
                },
                rules: values.rules ?? [],
            };

            if (record) {
                await apiClient.role.updateRole({ name: record.metadata.name, role: payload });
                return;
            }

            await apiClient.role.createRole({ role: payload });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("角色已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "roles"] });
        },
    });
    const deleteRole = useMutation({
        mutationFn: async (name: string) => apiClient.role.deleteRole({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("角色已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "roles"] });
        },
    });

    const openCreate = () => {
        setEditing(undefined);
        form.resetFields();
        form.setFieldsValue({ rules: [] });
        setOpen(true);
    };

    const openEdit = (role: Role) => {
        setEditing(role);
        form.resetFields();
        form.setFieldsValue({
            displayName: role.metadata.annotations?.[DISPLAY_NAME_KEY],
            name: role.metadata.name,
            rules: role.rules ?? [],
        });
        setOpen(true);
    };

    const confirmDelete = (role: Role) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除角色 ${role.metadata.name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteRole.mutateAsync(role.metadata.name),
            title: "删除角色",
        });
    };

    const columns: TableProps<Role>["columns"] = [
        {
            key: "name",
            render: (_, role) => (
                <div className="flex items-center gap-2">
                    <span className="font-medium">
                        {role.metadata.annotations?.[DISPLAY_NAME_KEY] ?? role.metadata.name}
                    </span>
                    {isBuiltin(role) ? <Tag>内置</Tag> : null}
                </div>
            ),
            title: "角色",
        },
        { dataIndex: ["metadata", "name"], key: "metadataName", title: "名称" },
        {
            key: "rules",
            render: (_, role) => role.rules?.length ?? 0,
            title: "规则数",
        },
        {
            dataIndex: ["metadata", "creationTimestamp"],
            key: "creationTimestamp",
            render: (value: string) => formatDateTime(value),
            title: "创建时间",
        },
        {
            key: "actions",
            render: (_, role) => (
                <Space>
                    <Button disabled={isBuiltin(role)} onClick={() => openEdit(role)} size="small">
                        编辑
                    </Button>
                    <Button onClick={() => setSelected(role)} size="small">
                        详情
                    </Button>
                    <Button danger disabled={isBuiltin(role)} onClick={() => confirmDelete(role)} size="small">
                        删除
                    </Button>
                </Space>
            ),
            title: "操作",
        },
    ];

    const ruleColumns: TableProps<PolicyRule>["columns"] = [
        { key: "apiGroups", render: (_, rule) => tagsOf(rule.apiGroups), title: "API 组" },
        { key: "resources", render: (_, rule) => tagsOf(rule.resources), title: "资源" },
        { key: "resourceNames", render: (_, rule) => tagsOf(rule.resourceNames), title: "资源名称" },
        { key: "nonResourceURLs", render: (_, rule) => tagsOf(rule.nonResourceURLs), title: "非资源 URL" },
        { key: "verbs", render: (_, rule) => tagsOf(rule.verbs), title: "动词" },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={roles}
                error={rolesQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建角色
                        </Button>
                        <Button onClick={() => void rolesQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={rolesQuery.isPending}
                onRetry={() => void rolesQuery.refetch()}
                pagination={{ ...table.pagination, total: rolesQuery.data?.total ?? 0 }}
                rowKey={(role) => role.metadata.name}
                title="角色"
            />
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveRole.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title={editing ? "编辑角色" : "新建角色"}
                width={760}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveRole.mutate({ record: editing, values })}
                    requiredMark={false}
                >
                    <Form.Item
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：auditor" />
                    </Form.Item>
                    <Form.Item label="显示名称" name="displayName">
                        <Input placeholder="例如：审计员" />
                    </Form.Item>
                    <Form.List name="rules">
                        {(fields, { add, remove }) => (
                            <Flex vertical gap={12}>
                                {fields.map(({ key, name, ...restField }) => (
                                    <Card
                                        extra={
                                            <Button danger onClick={() => remove(name)} size="small" type="text">
                                                删除规则
                                            </Button>
                                        }
                                        key={key}
                                        size="small"
                                        title={`规则 ${name + 1}`}
                                    >
                                        <Form.Item {...restField} label="API 组" name={[name, "apiGroups"]}>
                                            <Select mode="tags" placeholder="例如：api.pudans.tolim.cn" />
                                        </Form.Item>
                                        <Form.Item {...restField} label="资源" name={[name, "resources"]}>
                                            <Select mode="tags" placeholder="例如：users" />
                                        </Form.Item>
                                        <Form.Item {...restField} label="动词" name={[name, "verbs"]}>
                                            <Select
                                                mode="tags"
                                                options={[
                                                    { label: "get", value: "get" },
                                                    { label: "list", value: "list" },
                                                    { label: "create", value: "create" },
                                                    { label: "update", value: "update" },
                                                    { label: "patch", value: "patch" },
                                                    { label: "delete", value: "delete" },
                                                ]}
                                            />
                                        </Form.Item>
                                        <Form.Item {...restField} label="资源名称" name={[name, "resourceNames"]}>
                                            <Select mode="tags" placeholder="可选" />
                                        </Form.Item>
                                        <Form.Item {...restField} label="非资源 URL" name={[name, "nonResourceURLs"]}>
                                            <Select mode="tags" placeholder="可选" />
                                        </Form.Item>
                                    </Card>
                                ))}
                                <Button onClick={() => add({ apiGroups: [], resources: [], verbs: [] })} type="dashed">
                                    添加规则
                                </Button>
                            </Flex>
                        )}
                    </Form.List>
                </Form>
            </Modal>
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={760}
            >
                <Flex vertical gap={16}>
                    <Descriptions
                        bordered
                        column={{ sm: 2, xs: 1 }}
                        items={[
                            {
                                children: selected?.metadata.annotations?.[DISPLAY_NAME_KEY] ?? "—",
                                key: "displayName",
                                label: "显示名称",
                            },
                            { children: selected?.metadata.name ?? "—", key: "name", label: "名称" },
                            {
                                children: selected && isBuiltin(selected) ? "是" : "否",
                                key: "builtin",
                                label: "内置角色",
                            },
                            {
                                children: formatDateTime(selected?.metadata.creationTimestamp),
                                key: "created",
                                label: "创建时间",
                            },
                        ]}
                        size="small"
                    />
                    <div>
                        <div className="mb-2 font-medium">权限规则</div>
                        {(selected?.rules ?? []).length === 0 ? (
                            <Empty description="暂无权限规则" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                        ) : (
                            <Table
                                columns={ruleColumns}
                                dataSource={selected?.rules}
                                pagination={false}
                                rowKey={(_, index) => String(index)}
                                scroll={{ x: "max-content" }}
                                size="small"
                            />
                        )}
                    </div>
                </Flex>
            </Modal>
        </>
    );
}
