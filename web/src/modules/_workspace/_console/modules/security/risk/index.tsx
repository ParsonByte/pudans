"use client";

import { type ReactNode, useEffect, useMemo, useState } from "react";

import { type RiskBan, type RiskEventView, type RiskRule, apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, HelpCircleIcon, RefreshIcon } from "tdesign-icons-react";
import {
    Breadcrumb,
    Button,
    Form,
    Input,
    InputNumber,
    RadioGroup,
    Select,
    Switch,
    Table,
    Tag,
    Textarea,
    Tooltip,
} from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { GlobalDialog, ResultTag, SectionCard, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { CopyableIdentifier } from "../../../components/copyable-identifier";
import { ObjectDetails } from "../../../components/object-details";
import { WeekHourHeatmap } from "../../../components/week-hour-heatmap";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCount, formatDateTime, formatDuration, operationLabel } from "../../../composables/_format";
import { DASHBOARD_RANGES, type DashboardRange } from "../../../composables/_range";
import { useConsoleTableState } from "../../../composables/_table";
import { IdentityDialogFooter } from "../../identity/components/identity-dialog-footer";
import { useDashboardTheme } from "../../overview/dashboard/composables/_theme";
import { RiskActionChart } from "./components/risk-action-chart";
import { RiskMetricGrid } from "./components/risk-metric-grid";
import { RiskRelationGraph } from "./components/risk-relation-graph";
import { RiskRuleGrid } from "./components/risk-rule-grid";
import { RiskSceneDistribution } from "./components/risk-scene-distribution";
import { RiskSubjectRanking } from "./components/risk-subject-ranking";
import { RiskTrendChart } from "./components/risk-trend-chart";
import {
    RISK_ACTION_OPTIONS,
    RISK_METRIC_OPTIONS,
    type RiskFormValues,
    readRiskSpec,
    riskSceneOptions,
    sceneLabel,
} from "./composables/_risk";

const { BreadcrumbItem } = Breadcrumb;

const REQUIRED_HINT = "必填项";
const SCENE_HINT = "场景由后端固定提供；同一场景仅一条规则生效，已配置的场景请直接编辑现有规则。";
const ACTION_HINT = "阻断会在达到阈值后封禁主体一段时间；告警只记录风险事件，不封禁。";
const METRIC_HINT = "统计指标，后端当前仅支持来源 IP；具体主体随场景自动判定。";

function RequiredLabel({ children, hint }: { children: ReactNode; hint?: string }) {
    return (
        <span className="inline-flex items-center gap-1">
            <Tooltip content={REQUIRED_HINT} placement="top-left">
                <span className="cursor-help text-[var(--td-error-color)]">*</span>
            </Tooltip>
            {children}
            {hint ? (
                <Tooltip content={hint} placement="top-left">
                    <HelpCircleIcon />
                </Tooltip>
            ) : null}
        </span>
    );
}

type RiskEventRow = RiskEventView & {
    rowId: string;
};

type RiskBanRow = RiskBan & {
    rowId: string;
};

type RiskTab = "events" | "overview" | "rules";

const RISK_TAB_OPTIONS: Array<{ label: string; value: RiskTab }> = [
    { label: "风险概览", value: "overview" },
    { label: "事件与封禁", value: "events" },
    { label: "规则管理", value: "rules" },
];

const EMPTY_RISK_FORM: RiskFormValues = {
    action: "BLOCK",
    blockSeconds: 60,
    enabled: true,
    metric: "IP",
    name: "",
    scene: "",
    threshold: 60,
    windowSeconds: 60,
};

export function RiskView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const { token } = useDashboardTheme();
    const [form] = Form.useForm();
    const formAction = Form.useWatch("action", form) as string | undefined;
    const [range, setRange] = useState<DashboardRange>(7);
    const [activeTab, setActiveTab] = useState<RiskTab>("overview");
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<RiskRule>();
    const [selected, setSelected] = useState<RiskRule>();
    const [formSeed, setFormSeed] = useState<RiskFormValues>(EMPTY_RISK_FORM);
    const overviewQuery = useQuery({
        placeholderData: keepPreviousData,
        queryFn: async () => (await apiClient.riskRule.getRiskOverview({ days: range })).data,
        queryKey: ["console", "risk", "overview", range],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const rulesQuery = useQuery({
        queryFn: async () => (await apiClient.riskRule.listRiskRule({ page: table.page, size: table.pageSize })).data,
        queryKey: ["console", "risk", "rules", table.page, table.pageSize],
    });
    const bansQuery = useQuery({
        queryFn: async () => (await apiClient.riskRule.listRiskBans()).data,
        queryKey: ["console", "risk", "bans"],
        refetchInterval: 30_000,
    });
    const saveRule = useMutation({
        mutationFn: async ({ record, values }: { record?: RiskRule; values: RiskFormValues }) => {
            const action = values.action ?? "BLOCK";
            const payload = {
                apiVersion: "pudans.tolim.cn/v1alpha1",
                kind: "RiskRule",
                metadata: { ...record?.metadata, name: record?.metadata.name ?? values.name },
                spec: {
                    action,
                    blockSeconds: action === "BLOCK" ? Number(values.blockSeconds) || 60 : 0,
                    description: values.description,
                    enabled: values.enabled ?? true,
                    metric: values.metric ?? "IP",
                    scene: values.scene,
                    threshold: Number(values.threshold) || 60,
                    windowSeconds: Number(values.windowSeconds) || 60,
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
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "风险规则已保存。" });
            setOpen(false);
            setActiveTab("rules");
            void queryClient.invalidateQueries({ queryKey: ["console", "risk"] });
        },
    });
    const deleteRule = useMutation({
        mutationFn: async (name: string) => apiClient.riskRule.deleteRiskRule({ name }),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "风险规则已删除。" });
            void queryClient.invalidateQueries({ queryKey: ["console", "risk"] });
        },
    });
    const unbanSubject = useMutation({
        mutationFn: async ({ scene, subject }: { scene: string; subject: string }) =>
            apiClient.riskRule.unbanRiskSubject({ scene, subject }),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "已解除封禁。" });
            void queryClient.invalidateQueries({ queryKey: ["console", "risk"] });
        },
    });
    const overview = overviewQuery.data;
    const sceneOptions = riskSceneOptions(overview?.scenes);
    const summary = overview?.summary;
    const isSyncing = overviewQuery.isFetching || rulesQuery.isFetching || bansQuery.isFetching;
    const eventRows = useMemo<RiskEventRow[]>(
        () =>
            (overview?.recentEvents ?? []).map((event, index) => ({
                ...event,
                rowId: event.auditId ?? `${event.occurredAt ?? "unknown"}:${event.operation ?? "event"}:${index}`,
            })),
        [overview?.recentEvents],
    );
    const banRows = useMemo<RiskBanRow[]>(
        () =>
            (bansQuery.data ?? []).map((ban, index) => ({
                ...ban,
                rowId: `${ban.scene ?? "scene"}:${ban.subject ?? "subject"}:${index}`,
            })),
        [bansQuery.data],
    );
    const refresh = () => {
        void Promise.all([overviewQuery.refetch(), rulesQuery.refetch(), bansQuery.refetch()]);
    };

    useEffect(() => {
        if (overviewQuery.error) {
            prompt.error({ content: consoleErrorMessage(overviewQuery.error) });
        }
    }, [overviewQuery.error, prompt]);

    useEffect(() => {
        if (bansQuery.error) {
            prompt.error({ content: consoleErrorMessage(bansQuery.error) });
        }
    }, [bansQuery.error, prompt]);

    useEffect(() => {
        if (rulesQuery.error) {
            prompt.error({ content: consoleErrorMessage(rulesQuery.error) });
        }
    }, [prompt, rulesQuery.error]);

    useEffect(() => {
        if (overview?.truncated) {
            prompt.warning({ content: "风险事件达到扫描上限，请缩小时间范围后重新查看。" });
        }
    }, [overview?.truncated, prompt]);

    useEffect(() => {
        if (!open) {
            return;
        }

        form.reset({ type: "empty" });
        form.setFieldsValue(formSeed);
    }, [form, formSeed, open]);

    const openCreate = () => {
        setEditing(undefined);
        setFormSeed(EMPTY_RISK_FORM);
        setOpen(true);
    };

    const openEdit = (rule: RiskRule) => {
        const spec = readRiskSpec(rule);
        setEditing(rule);
        setFormSeed({
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
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除风险规则 ${name} 吗？`,
            danger: true,
            onConfirm: () => void deleteRule.mutateAsync(name),
            title: "删除风险规则",
        });
    };

    const eventColumns: TableProps<RiskEventRow>["columns"] = [
        {
            cell: ({ row }) => (
                <span className="text-xs whitespace-nowrap tabular-nums" style={{ color: token.colorTextSecondary }}>
                    {formatDateTime(row.occurredAt)}
                </span>
            ),
            colKey: "occurredAt",
            title: "时间",
            width: 168,
        },
        {
            cell: ({ row }) => (
                <Tooltip content={row.scene}>
                    <span>{sceneLabel(row.scene, overview?.scenes)}</span>
                </Tooltip>
            ),
            colKey: "scene",
            title: "场景",
            width: 148,
        },
        {
            cell: ({ row }) => <CopyableIdentifier value={row.subject} />,
            colKey: "subject",
            title: "主体",
            width: 190,
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.operation === "RISK_BLOCKED" ? "danger" : "warning"} variant="light">
                    {operationLabel(row.operation)}
                </Tag>
            ),
            colKey: "operation",
            title: "处置",
            width: 104,
        },
        {
            cell: ({ row }) => <ResultTag result={row.result} />,
            colKey: "result",
            title: "结果",
            width: 92,
        },
        {
            cell: ({ row }) => (
                <Tooltip content={row.path}>
                    <span className="block max-w-60 truncate font-mono text-xs">{row.path || "—"}</span>
                </Tooltip>
            ),
            colKey: "path",
            title: "请求路径",
        },
        {
            cell: ({ row }) => formatCount(row.count),
            colKey: "count",
            title: "次数",
            width: 72,
        },
    ];

    const banColumns: TableProps<RiskBanRow>["columns"] = [
        {
            cell: ({ row }) => <CopyableIdentifier value={row.subject} />,
            colKey: "subject",
            title: "对象",
        },
        {
            cell: ({ row }) => sceneLabel(row.scene, overview?.scenes),
            colKey: "scene",
            title: "场景",
        },
        {
            cell: ({ row }) => formatDuration((row.expiresInSeconds ?? 0) * 1000),
            colKey: "expiresInSeconds",
            title: "剩余时间",
        },
        {
            cell: ({ row }) => (
                <Button
                    loading={unbanSubject.isPending}
                    onClick={() =>
                        unbanSubject.mutate({
                            scene: row.scene ?? "",
                            subject: row.subject ?? "",
                        })
                    }
                    size="small"
                >
                    解封
                </Button>
            ),
            colKey: "actions",
            title: "操作",
        },
    ];

    return (
        <div className="flex w-full min-w-0 flex-col gap-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <Breadcrumb>
                    <BreadcrumbItem>安全与审计</BreadcrumbItem>
                    <BreadcrumbItem>风险控制</BreadcrumbItem>
                </Breadcrumb>
                <div className="flex flex-wrap items-center gap-2">
                    <Select
                        aria-label="风险统计范围"
                        onChange={(value) => setRange(value as DashboardRange)}
                        options={DASHBOARD_RANGES}
                        style={{ width: 132 }}
                        value={range}
                    />
                    <Button icon={<RefreshIcon />} loading={isSyncing} onClick={refresh} variant="outline">
                        刷新
                    </Button>
                    <Button icon={<AddIcon />} onClick={openCreate} theme="primary">
                        新建规则
                    </Button>
                </div>
            </div>

            <RadioGroup
                className="w-fit"
                onChange={(value) => setActiveTab(value as RiskTab)}
                options={RISK_TAB_OPTIONS}
                theme="button"
                value={activeTab}
                variant="default-filled"
            />

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12" hidden={activeTab !== "overview"}>
                <RiskMetricGrid loading={overviewQuery.isPending} summary={summary} />

                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="风险事件趋势">
                        <RiskTrendChart points={overview?.timeline} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="7×24 风险热力图">
                        <WeekHourHeatmap
                            cells={overview?.heatmap}
                            count={(cell) => cell.events}
                            countUnit="次"
                            emptyText="暂无热力图数据"
                            tone="red"
                            valueLabel="风险事件"
                        />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-7">
                    <SectionCard title="场景 × 主体关系">
                        <RiskRelationGraph relations={overview?.relations} scenes={overview?.scenes} />
                    </SectionCard>
                </div>
                <div className="md:col-span-2 xl:col-span-5">
                    <SectionCard title="风险场景分布">
                        <RiskSceneDistribution scenes={overview?.scenes} />
                    </SectionCard>
                </div>
                <div className="md:col-span-1 xl:col-span-6">
                    <SectionCard title="高风险主体排行">
                        <RiskSubjectRanking subjects={overview?.subjects} />
                    </SectionCard>
                </div>
                <div className="md:col-span-1 xl:col-span-6">
                    <SectionCard title="规则动作分布">
                        <RiskActionChart scenes={overview?.scenes} />
                    </SectionCard>
                </div>
            </div>

            <div className="grid grid-cols-1 gap-4 xl:grid-cols-12" hidden={activeTab !== "events"}>
                <div className="xl:col-span-7">
                    <SectionCard title="最近风险事件">
                        <Table
                            columns={eventColumns}
                            data={eventRows}
                            loading={overviewQuery.isPending}
                            rowKey="rowId"
                            size="small"
                        />
                    </SectionCard>
                </div>
                <div className="xl:col-span-5">
                    <SectionCard title="实时封禁">
                        <Table
                            columns={banColumns}
                            data={banRows}
                            loading={bansQuery.isPending}
                            rowKey="rowId"
                            size="small"
                        />
                    </SectionCard>
                </div>
            </div>

            <div hidden={activeTab !== "rules"}>
                <RiskRuleGrid
                    error={rulesQuery.error}
                    loading={rulesQuery.isPending}
                    onCreate={openCreate}
                    onDelete={confirmDelete}
                    onDetails={setSelected}
                    onEdit={openEdit}
                    onRetry={() => void rulesQuery.refetch()}
                    pagination={table.pagination}
                    pending={deleteRule.isPending}
                    rules={rulesQuery.data?.items}
                    scenes={overview?.scenes}
                    total={rulesQuery.data?.total ?? 0}
                />
            </div>

            <GlobalDialog
                footer={
                    <IdentityDialogFooter
                        onCancel={() => setOpen(false)}
                        onSubmit={() => form.submit()}
                        pending={saveRule.isPending}
                        submitLabel="保存"
                    />
                }
                onClose={() => setOpen(false)}
                open={open}
                title={editing ? "编辑风险规则" : "新建风险规则"}
                width={640}
            >
                <Form
                    form={form}
                    initialData={formSeed}
                    labelAlign="top"
                    layout="vertical"
                    onSubmit={({ validateResult }) => {
                        if (validateResult !== true) {
                            return;
                        }

                        saveRule.mutate({
                            record: editing,
                            values: form.getFieldsValue(true) as RiskFormValues,
                        });
                    }}
                >
                    <Form.FormItem
                        label={<RequiredLabel>名称</RequiredLabel>}
                        name="name"
                        requiredMark={false}
                        rules={[{ message: "请输入名称。", required: true, whitespace: true }]}
                    >
                        <Input disabled={Boolean(editing)} placeholder="例如：login-failure" />
                    </Form.FormItem>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label={<RequiredLabel hint={SCENE_HINT}>场景</RequiredLabel>}
                            name="scene"
                            requiredMark={false}
                            rules={[{ message: "请选择场景。", required: true }]}
                        >
                            <Select disabled={Boolean(editing)} options={sceneOptions} placeholder="请选择风险场景" />
                        </Form.FormItem>
                        <Form.FormItem
                            label={
                                <span className="inline-flex items-center gap-1">
                                    指标
                                    <Tooltip content={METRIC_HINT} placement="top-left">
                                        <HelpCircleIcon />
                                    </Tooltip>
                                </span>
                            }
                            name="metric"
                        >
                            <Select options={RISK_METRIC_OPTIONS} />
                        </Form.FormItem>
                    </div>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label={
                                <span className="inline-flex items-center gap-1">
                                    动作
                                    <Tooltip content={ACTION_HINT} placement="top-left">
                                        <HelpCircleIcon />
                                    </Tooltip>
                                </span>
                            }
                            name="action"
                        >
                            <Select options={RISK_ACTION_OPTIONS} />
                        </Form.FormItem>
                        {formAction === "BLOCK" ? (
                            <Form.FormItem
                                label="阻断时长"
                                name="blockSeconds"
                                rules={[{ message: "请输入不小于 1 的整数。", min: 1, number: true }]}
                            >
                                <InputNumber decimalPlaces={0} min={1} style={{ width: "100%" }} suffix="秒" />
                            </Form.FormItem>
                        ) : null}
                    </div>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label="触发阈值"
                            name="threshold"
                            rules={[{ message: "请输入不小于 1 的整数。", min: 1, number: true }]}
                        >
                            <InputNumber decimalPlaces={0} min={1} style={{ width: "100%" }} suffix="次" />
                        </Form.FormItem>
                        <Form.FormItem
                            label="统计窗口"
                            name="windowSeconds"
                            rules={[{ message: "请输入不小于 1 的整数。", min: 1, number: true }]}
                        >
                            <InputNumber decimalPlaces={0} min={1} style={{ width: "100%" }} suffix="秒" />
                        </Form.FormItem>
                    </div>
                    <Form.FormItem name="enabled">
                        <div className="flex items-center gap-2">
                            <Switch />
                            <span className="text-sm text-[var(--td-text-color-secondary)]">
                                启用该规则，关闭后不再统计与拦截
                            </span>
                        </div>
                    </Form.FormItem>
                    <Form.FormItem label="描述" name="description">
                        <Textarea autosize={{ maxRows: 4, minRows: 2 }} placeholder="可选，说明规则用途或处置方式" />
                    </Form.FormItem>
                </Form>
            </GlobalDialog>

            <GlobalDialog
                footer={false}
                onClose={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={640}
            >
                {selected ? <ObjectDetails value={selected} /> : null}
            </GlobalDialog>
        </div>
    );
}
