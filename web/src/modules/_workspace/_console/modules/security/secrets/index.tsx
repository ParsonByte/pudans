"use client";

import { useState } from "react";

import { type Secret, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Flex, Form, Input, Modal, Space, type TableProps } from "antd";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { type KeyValueEntry, toKeyValueRecord } from "../../../composables/_key-value";
import { useConsoleTableState } from "../../../composables/_table";

type SecretFormValues = {
    name: string;
    stringData?: KeyValueEntry[];
    type?: string;
};

export function SecretsView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<SecretFormValues>();
    const [open, setOpen] = useState(false);
    const secretsQuery = useQuery({
        queryKey: ["console", "secrets", table.page, table.pageSize],
        queryFn: async () => (await apiClient.secret.listSecret({ page: table.page, size: table.pageSize })).data,
    });
    const saveSecret = useMutation({
        mutationFn: async (values: SecretFormValues) => {
            const stringData = toKeyValueRecord(values.stringData);
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                kind: "Secret",
                metadata: { name: values.name },
                type: values.type ?? "Opaque",
                ...(Object.keys(stringData).length > 0 ? { stringData } : {}),
            };

            await apiClient.secret.createSecret({ secret: payload });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("密钥已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "secrets"] });
        },
    });
    const deleteSecret = useMutation({
        mutationFn: async (name: string) => apiClient.secret.deleteSecret({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("密钥已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "secrets"] });
        },
    });

    const openCreate = () => {
        form.resetFields();
        form.setFieldsValue({ stringData: [], type: "Opaque" });
        setOpen(true);
    };

    const confirmDelete = (record: Secret) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除密钥 ${record.metadata.name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteSecret.mutateAsync(record.metadata.name),
            title: "删除密钥",
        });
    };

    const columns: TableProps<Secret>["columns"] = [
        { dataIndex: ["metadata", "name"], key: "name", title: "名称" },
        { dataIndex: "type", key: "type", render: (value: string) => value ?? "Opaque", title: "类型" },
        {
            key: "keys",
            render: (_, record) => Object.keys(record.data ?? record.stringData ?? {}).length,
            title: "键数量",
        },
        {
            dataIndex: ["metadata", "creationTimestamp"],
            key: "creationTimestamp",
            render: (value: string) => formatDateTime(value),
            title: "创建时间",
        },
        {
            key: "actions",
            render: (_, record) => (
                <Button danger onClick={() => confirmDelete(record)} size="small">
                    删除
                </Button>
            ),
            title: "操作",
        },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={secretsQuery.data?.items}
                error={secretsQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建密钥
                        </Button>
                        <Button onClick={() => void secretsQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={secretsQuery.isPending}
                onRetry={() => void secretsQuery.refetch()}
                pagination={{ ...table.pagination, total: secretsQuery.data?.total ?? 0 }}
                rowKey={(record) => record.metadata.name}
                title="密钥管理"
            />
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveSecret.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title="新建密钥"
                width={640}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveSecret.mutate(values)}
                    requiredMark={false}
                >
                    <Form.Item
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input placeholder="例如：smtp-credentials" />
                    </Form.Item>
                    <Form.Item label="类型" name="type">
                        <Input placeholder="Opaque" />
                    </Form.Item>
                    <Form.List name="stringData">
                        {(fields, { add, remove }) => (
                            <Flex vertical gap={8}>
                                {fields.map(({ key, name, ...restField }) => (
                                    <Flex align="start" gap={8} key={key}>
                                        <Form.Item
                                            {...restField}
                                            className="!mb-0 flex-1"
                                            name={[name, "key"]}
                                            rules={[{ message: "请输入键。", required: true, whitespace: true }]}
                                        >
                                            <Input placeholder="键" />
                                        </Form.Item>
                                        <Form.Item {...restField} className="!mb-0 flex-[2]" name={[name, "value"]}>
                                            <Input placeholder="值" />
                                        </Form.Item>
                                        <Button danger onClick={() => remove(name)} type="text">
                                            删除
                                        </Button>
                                    </Flex>
                                ))}
                                <Button onClick={() => add({ key: "", value: "" })} type="dashed">
                                    添加键值
                                </Button>
                            </Flex>
                        )}
                    </Form.List>
                </Form>
            </Modal>
        </>
    );
}
