"use client";

import { useState } from "react";

import { type RiskBan, type RiskRule, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    App,
    Button,
    Flex,
    Form,
    Input,
    InputNumber,
    Modal,
    Select,
    Space,
    Switch,
    Table,
    type TableProps,
    Tag,
} from "antd";

import { SectionCard } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime, formatDuration } from "../../../composables/_format";
import { useConsoleTableState } from "../../../composables/_table";

type RiskRuleSpec = {
    action?: string;
    blockSeconds?: number;
    description?: string;
    enabled?: boolean;
    metric?: string;
    scene?: string;
    threshold?: number;
    windowSeconds?: number;
};

type RiskFormValues = {
    action?: string;
    blockSeconds?: number;
    description?: string;
    enabled?: boolean;
    metric?: string;
    name: string;
    scene: string;
    threshold?: number;
    windowSeconds?: number;
};

const ACTION_OPTIONS = [
    { label: "阻断", value: "BLOCK" },
    { label: "告警", value: "ALERT" },
];

function readSpec(rule: RiskRule): RiskRuleSpec {
    return rule.spec as unknown as RiskRuleSpec;
}

export function RiskView() {
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const [form] = Form.useForm<RiskFormValues>();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<RiskRule>();
    const [selected, setSelected] = useState<RiskRule>();
    const rulesQuery = useQuery({
        queryKey: ["console", "risk", table.page, table.pageSize],
        queryFn: async () => (await apiClient.riskRule.listRiskRule({ page: table.page, size: table.pageSize })).data,
    });
    const bansQuery = useQuery({
        queryKey: ["console", "risk", "bans"],
        queryFn: async () => (await apiClient.riskRule.listRiskBans()).data,
    });
    const saveRule = useMutation({
        mutationFn: async ({ record, values }: { record?: RiskRule; values: RiskFormValues }) => {
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                kind: "RiskRule",
                metadata: { ...record?.metadata, name: record?.metadata.name ?? values.name },
                spec: {
                    action: values.action ?? "BLOCK",
                    blockSeconds: values.blockSeconds ?? 60,
                    description: values.description,
                    enabled: values.enabled ?? true,
                    metric: values.metric ?? "IP",
                    scene: values.scene,
                    threshold: values.threshold ?? 60,
                    windowSeconds: values.windowSeconds ?? 60,
                },
            };

            if (record) {
                await apiClient.riskRule.updateRiskRule({
                    name: record.metadata.name,
                    riskRule: payload as unknown as RiskRule,
                });
                return;
            }

            await apiClient.riskRule.createRiskRule({ riskRule: payload as unknown as RiskRule });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("风险规则已保存。");
            setOpen(false);
            void queryClient.invalidateQueries({ queryKey: ["console", "risk"] });
        },
    });
    const deleteRule = useMutation({
        mutationFn: async (name: string) => apiClient.riskRule.deleteRiskRule({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("风险规则已删除。");
            void queryClient.invalidateQueries({ queryKey: ["console", "risk"] });
        },
    });
    const unbanSubject = useMutation({
        mutationFn: async ({ scene, subject }: { scene: string; subject: string }) =>
            apiClient.riskRule.unbanRiskSubject({ scene, subject }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("已解除封禁。");
            void queryClient.invalidateQueries({ queryKey: ["console", "risk", "bans"] });
        },
    });

    const openCreate = () => {
        setEditing(undefined);
        form.resetFields();
        form.setFieldsValue({
            action: "BLOCK",
            blockSeconds: 60,
            enabled: true,
            metric: "IP",
            threshold: 60,
            windowSeconds: 60,
        });
        setOpen(true);
    };

    const openEdit = (rule: RiskRule) => {
        const spec = readSpec(rule);
        setEditing(rule);
        form.resetFields();
        form.setFieldsValue({
            action: spec.action ?? "BLOCK",
            blockSeconds: spec.blockSeconds ?? 60,
            description: spec.description,
            enabled: spec.enabled ?? true,
            metric: spec.metric ?? "IP",
            name: rule.metadata.name,
            scene: spec.scene ?? "",
            threshold: spec.threshold ?? 60,
            windowSeconds: spec.windowSeconds ?? 60,
        });
        setOpen(true);
    };

    const confirmDelete = (rule: RiskRule) => {
        const name = rule.metadata.name;
        modal.confirm({
            cancelText: "取消",
            content: `确定删除风险规则 ${name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () => deleteRule.mutateAsync(name),
            title: "删除风险规则",
        });
    };

    const columns: TableProps<RiskRule>["columns"] = [
        {
            key: "scene",
            render: (_, rule) => readSpec(rule).scene ?? rule.metadata.name,
            title: "场景",
        },
        {
            key: "description",
            render: (_, rule) => readSpec(rule).description ?? "—",
            title: "描述",
        },
        {
            key: "enabled",
            render: (_, rule) => {
                const enabled = readSpec(rule).enabled;

                return <Tag color={enabled === false ? "default" : "green"}>{enabled === false ? "停用" : "启用"}</Tag>;
            },
            title: "状态",
        },
        {
            key: "threshold",
            render: (_, rule) => {
                const spec = readSpec(rule);

                return `${spec.threshold ?? "—"} / ${spec.windowSeconds ?? "—"}s`;
            },
            title: "阈值 / 窗口",
        },
        {
            key: "action",
            render: (_, rule) => readSpec(rule).action ?? "—",
            title: "动作",
        },
        {
            dataIndex: ["metadata", "creationTimestamp"],
            key: "creationTimestamp",
            render: (value: string) => formatDateTime(value),
            title: "创建时间",
        },
        {
            key: "actions",
            render: (_, rule) => (
                <Space>
                    <Button onClick={() => openEdit(rule)} size="small">
                        编辑
                    </Button>
                    <Button onClick={() => setSelected(rule)} size="small">
                        详情
                    </Button>
                    <Button danger onClick={() => confirmDelete(rule)} size="small">
                        删除
                    </Button>
                </Space>
            ),
            title: "操作",
        },
    ];

    const banColumns: TableProps<RiskBan>["columns"] = [
        { dataIndex: "subject", key: "subject", title: "对象" },
        { dataIndex: "scene", key: "scene", title: "场景" },
        {
            dataIndex: "expiresInSeconds",
            key: "expiresInSeconds",
            render: (value: number) => formatDuration((value ?? 0) * 1000),
            title: "剩余时间",
        },
        {
            key: "actions",
            render: (_, ban) => (
                <Button
                    loading={unbanSubject.isPending}
                    onClick={() =>
                        unbanSubject.mutate({
                            scene: ban.scene ?? "",
                            subject: ban.subject ?? "",
                        })
                    }
                    size="small"
                >
                    解封
                </Button>
            ),
            title: "操作",
        },
    ];

    return (
        <Flex vertical gap={16}>
            <ConsoleTable
                columns={columns}
                data={rulesQuery.data?.items}
                error={rulesQuery.error}
                extra={
                    <Space>
                        <Button onClick={openCreate} size="small" type="primary">
                            新建规则
                        </Button>
                        <Button onClick={() => void rulesQuery.refetch()} size="small">
                            刷新
                        </Button>
                    </Space>
                }
                loading={rulesQuery.isPending}
                onRetry={() => void rulesQuery.refetch()}
                pagination={{ ...table.pagination, total: rulesQuery.data?.total ?? 0 }}
                rowKey={(rule) => rule.metadata.name}
                title="风险规则"
            />
            <SectionCard
                extra={
                    <Button onClick={() => void bansQuery.refetch()} size="small">
                        刷新
                    </Button>
                }
                title="当前封禁"
            >
                <Table
                    columns={banColumns}
                    dataSource={bansQuery.data}
                    loading={bansQuery.isPending}
                    pagination={false}
                    rowKey={(ban) => `${ban.scene ?? ""}:${ban.subject ?? ""}`}
                    scroll={{ x: "max-content" }}
                    size="small"
                />
            </SectionCard>
            <Modal
                destroyOnHidden
                footer={
                    <Space>
                        <Button onClick={() => setOpen(false)}>取消</Button>
                        <Button loading={saveRule.isPending} onClick={() => form.submit()} type="primary">
                            保存
                        </Button>
                    </Space>
                }
                onCancel={() => setOpen(false)}
                open={open}
                title={editing ? "编辑风险规则" : "新建风险规则"}
                width={560}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveRule.mutate({ record: editing, values })}
                    requiredMark={false}
                >
                    <Form.Item
                        label="名称"
                        name="name"
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：login-failure" />
                    </Form.Item>
                    <Form.Item
                        label="场景"
                        name="scene"
                        rules={[{ message: "请输入场景。", required: true, whitespace: true }]}
                    >
                        <Input placeholder="例如：login-failure" />
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea placeholder="可选" rows={2} />
                    </Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="指标" name="metric">
                        <Input placeholder="IP" />
                    </Form.Item>
                    <Form.Item label="动作" name="action">
                        <Select options={ACTION_OPTIONS} />
                    </Form.Item>
                    <div className="grid gap-4 sm:grid-cols-3">
                        <Form.Item className="!mb-0" label="阈值" name="threshold">
                            <InputNumber className="w-full" min={1} precision={0} />
                        </Form.Item>
                        <Form.Item className="!mb-0" label="窗口（秒）" name="windowSeconds">
                            <InputNumber className="w-full" min={1} precision={0} />
                        </Form.Item>
                        <Form.Item className="!mb-0" label="阻断时长（秒）" name="blockSeconds">
                            <InputNumber className="w-full" min={0} precision={0} />
                        </Form.Item>
                    </div>
                </Form>
            </Modal>
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={640}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </Flex>
    );
}
