"use client";

import { useState } from "react";

import { type ConfigMap, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Flex, Form, Input, Modal, Space, type TableProps } from "antd";

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

export function ConfigMapsView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<ConfigMapFormValues>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<ConfigMap>();
    const configMapsQuery = useQuery({
        queryKey: ["console", "config-maps", table.page, table.pageSize],
        queryFn: async () => (await apiClient.configMap.listConfigMap({ page: table.page, size: table.pageSize })).data,
    });
    const saveConfigMap = useMutation({
        mutationFn: async ({ record, values }: { record?: ConfigMap; values: ConfigMapFormValues }) => {
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
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("配置字典已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "config-maps"] });
        },
    });
    const deleteConfigMap = useMutation({
        mutationFn: async (name: string) => apiClient.configMap.deleteConfigMap({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("配置字典已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "config-maps"] });
        },
    });

    const openCreate = () => {
        setEditing(undefined);
        form.resetFields();
        form.setFieldsValue({ data: [] });
        setOpen(true);
    };

    const openEdit = (record: ConfigMap) => {
        setEditing(record);
        form.resetFields();
        form.setFieldsValue({
            data: toKeyValueEntries(record.data),
            name: record.metadata.name,
        });
        setOpen(true);
    };

    const confirmDelete = (record: ConfigMap) => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除 ${record.metadata.name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteConfigMap.mutateAsync(record.metadata.name),
            title: "删除配置字典",
        });
    };

    const columns: TableProps<ConfigMap>["columns"] = [
        { dataIndex: ["metadata", "name"], key: "name", title: "名称" },
        {
            key: "keys",
            render: (_, record) => Object.keys(record.data ?? {}).length,
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
                data={configMapsQuery.data?.items}
                error={configMapsQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建配置字典
                        </Button>
                        <Button onClick={() => void configMapsQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={configMapsQuery.isPending}
                onRetry={() => void configMapsQuery.refetch()}
                pagination={{ ...table.pagination, total: configMapsQuery.data?.total ?? 0 }}
                rowKey={(record) => record.metadata.name}
                title="配置字典"
            />
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveConfigMap.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title={editing ? "编辑配置字典" : "新建配置字典"}
                width={640}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveConfigMap.mutate({ record: editing, values })}
                    requiredMark={false}
                >
                    <Form.Item
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：console-features" />
                    </Form.Item>
                    <Form.List name="data">
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
