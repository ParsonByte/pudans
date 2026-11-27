"use client";

import { useMemo, useState } from "react";

import type { AiPeakWindow } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { AddIcon, ChevronDownIcon, ChevronUpIcon, DeleteIcon } from "tdesign-icons-react";
import { Button, DatePicker, Form, Input, InputNumber, RadioGroup, Select, Switch, TimePicker } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { IdentityDialogFooter } from "@/modules/_workspace/_console/modules/identity/components/identity-dialog-footer";

import { ProviderMark } from "../../components/provider-mark";
import {
    UNIT_OPTIONS,
    WEEKDAY_OPTIONS,
    dateTimeInputValue,
    formatCredits,
    instantValue,
} from "../../composables/_billing-meta";
import {
    type AiPriceRule,
    type AiPriceRuleInput,
    createAiPriceRule,
    listAiModels,
    listAiTools,
    updateAiPriceRule,
} from "../../composables/_dans";
import { modelLabel, modelProvider } from "../../composables/_model-meta";

type PriceRuleDialogProps = {
    onClose: () => void;
    onSaved: () => void;
    rule?: AiPriceRule;
};

type PriceRuleForm = {
    baseCredits?: number;
    cachedInputCoefficient?: number;
    effectiveFrom?: string;
    effectiveTo?: string;
    inputCoefficient?: number;
    mcpCallCoefficient?: number;
    mcpToolKey?: string;
    minimumCredits?: number;
    modelId?: string;
    offpeakMultiplier?: number;
    outputCoefficient?: number;
    peakMultiplier?: number;
    pricingMode?: string;
    quotaBucket?: string;
    targetType?: string;
    unit?: string;
    unitCredits?: number;
};

const DEFAULT_PEAK_WINDOW: AiPeakWindow = {
    days: ["mon"],
    end: "18:00",
    start: "09:00",
    zone: "Asia/Shanghai",
};

const TARGET_TYPE_OPTIONS = [
    { label: "模型", value: "model" },
    { label: "MCP 工具", value: "mcp_tool" },
] as const;

const MODEL_PRICING_OPTIONS = [
    { label: "按 Token", value: "token" },
    { label: "按用量单位", value: "unit_credit" },
] as const;

const MCP_PRICING_OPTIONS = [{ label: "按调用次数", value: "mcp_call" }] as const;

const PRICING_HINTS: Record<string, string> = {
    mcp_call: "MCP 工具通常按调用次数计费：每次调用固定扣多少积分。",
    token: "文本模型通常按 Token 计费：每 1 万 Token 扣多少积分。",
    unit_credit: "图片、视频、音频等通常按实际用量计费，也可以额外设置每笔基础积分。",
};

export function PriceRuleDialog({ onClose, onSaved, rule }: PriceRuleDialogProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [pending, setPending] = useState(false);
    const [peakWindows, setPeakWindows] = useState<AiPeakWindow[]>(rule?.peakWindows ?? []);
    const [peakEnabled, setPeakEnabled] = useState(Boolean(rule?.peakWindows?.length));
    const [showAdvanced, setShowAdvanced] = useState(false);
    const editing = Boolean(rule);
    const targetType = (Form.useWatch("targetType", form) as string | undefined) ?? rule?.targetType ?? "model";
    const pricingMode = (Form.useWatch("pricingMode", form) as string | undefined) ?? rule?.pricingMode ?? "token";
    const unit = (Form.useWatch("unit", form) as string | undefined) ?? rule?.unit ?? "request";
    const inputCoefficient =
        (Form.useWatch("inputCoefficient", form) as number | undefined) ?? rule?.inputCoefficient ?? 1;
    const outputCoefficient =
        (Form.useWatch("outputCoefficient", form) as number | undefined) ?? rule?.outputCoefficient ?? 1;
    const unitCredits = (Form.useWatch("unitCredits", form) as number | undefined) ?? rule?.unitCredits ?? 1;
    const baseCredits = (Form.useWatch("baseCredits", form) as number | undefined) ?? rule?.baseCredits ?? 0;
    const mcpCallCoefficient =
        (Form.useWatch("mcpCallCoefficient", form) as number | undefined) ?? rule?.mcpCallCoefficient ?? 1;
    const modelsQuery = useQuery({
        enabled: targetType === "model",
        queryFn: listAiModels,
        queryKey: ["dans", "ai", "models"],
    });
    const toolsQuery = useQuery({
        enabled: targetType === "mcp_tool",
        queryFn: listAiTools,
        queryKey: ["dans", "ai", "tools"],
    });
    const modelOptions = useMemo(
        () =>
            (modelsQuery.data ?? [])
                .slice()
                .sort((left, right) => modelLabel(left).localeCompare(modelLabel(right), "zh-CN"))
                .map((model) => ({
                    label: (
                        <span className="flex min-w-0 items-center gap-2">
                            <ProviderMark provider={modelProvider(model)} size="small" />
                            <span className="truncate">{modelLabel(model)}</span>
                        </span>
                    ),
                    value: model.id,
                })),
        [modelsQuery.data],
    );
    const toolOptions = useMemo(
        () =>
            (toolsQuery.data ?? [])
                .filter((tool) => tool.enabled !== false)
                .map((tool) => ({
                    label: tool.label ?? tool.key,
                    value: tool.key,
                }))
                .sort((left, right) => left.label.localeCompare(right.label, "zh-CN")),
        [toolsQuery.data],
    );
    const pricingOptions = targetType === "mcp_tool" ? MCP_PRICING_OPTIONS : MODEL_PRICING_OPTIONS;
    const pricingHint = PRICING_HINTS[pricingMode] ?? "";
    const preview = useMemo(() => {
        if (pricingMode === "token") {
            return `1 万输入 Token + 1 万输出 Token ≈ ${formatCredits(
                numeric(inputCoefficient) + numeric(outputCoefficient),
            )} 积分`;
        }
        if (pricingMode === "unit_credit") {
            const unitLabel = UNIT_OPTIONS.find((option) => option.value === unit)?.label ?? "1 个单位";

            return `1 个用量单位（${unitLabel.replace(/^每/, "")}）≈ ${formatCredits(
                numeric(unitCredits) + numeric(baseCredits),
            )} 积分`;
        }

        return `1 次调用 = ${formatCredits(numeric(mcpCallCoefficient))} 积分`;
    }, [baseCredits, inputCoefficient, mcpCallCoefficient, outputCoefficient, pricingMode, unit, unitCredits]);

    const submit = async (values: PriceRuleForm) => {
        const resolvedTargetType = rule?.targetType ?? values.targetType ?? "model";
        const resolvedPricingMode = rule?.pricingMode ?? values.pricingMode ?? "token";
        const tokenMode = resolvedPricingMode === "token";
        const unitMode = resolvedPricingMode === "unit_credit";
        const mcpMode = resolvedPricingMode === "mcp_call";
        const input: AiPriceRuleInput = {
            baseCredits: unitMode ? (values.baseCredits ?? 0) : 0,
            cachedInputCoefficient: tokenMode ? (values.cachedInputCoefficient ?? 1) : 0,
            effectiveFrom: instantValue(
                values.effectiveFrom ?? dateTimeInputValue(rule?.effectiveFrom) ?? dateTimeInputValue(new Date()),
            ),
            effectiveTo: instantValue(values.effectiveTo ?? dateTimeInputValue(rule?.effectiveTo)),
            inputCoefficient: tokenMode ? (values.inputCoefficient ?? 0) : 0,
            mcpCallCoefficient: mcpMode ? (values.mcpCallCoefficient ?? 0) : 0,
            mcpToolKey: resolvedTargetType === "mcp_tool" ? values.mcpToolKey?.trim() : undefined,
            minimumCredits: values.minimumCredits ?? rule?.minimumCredits ?? 0,
            modelId: resolvedTargetType === "model" ? values.modelId : undefined,
            offpeakMultiplier: peakEnabled ? (values.offpeakMultiplier ?? rule?.offpeakMultiplier ?? 1) : 1,
            outputCoefficient: tokenMode ? (values.outputCoefficient ?? 0) : 0,
            peakMultiplier: peakEnabled ? (values.peakMultiplier ?? rule?.peakMultiplier ?? 1) : 1,
            peakWindows: peakEnabled ? peakWindows : [],
            pricingMode: resolvedPricingMode,
            quotaBucket: (values.quotaBucket ?? rule?.quotaBucket)?.trim() || undefined,
            targetType: resolvedTargetType,
            unit: unitMode ? (values.unit ?? rule?.unit ?? "request") : undefined,
            unitCredits: unitMode ? (values.unitCredits ?? 0) : 0,
        };

        setPending(true);
        try {
            if (rule) {
                await updateAiPriceRule(rule.id, input);
                prompt.success({ content: "价格规则已更新。" });
            } else {
                await createAiPriceRule(input);
                prompt.success({ content: "价格规则已创建。" });
            }
            onSaved();
            onClose();
        } catch (error) {
            prompt.error({
                content: consoleErrorMessage(error, rule ? "价格规则更新失败。" : "价格规则创建失败。"),
            });
        } finally {
            setPending(false);
        }
    };

    const close = () => {
        if (!pending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={pending}
                    submitLabel={editing ? "保存" : "创建"}
                />
            }
            onClose={close}
            open
            title={editing ? "编辑价格规则" : "新建价格规则"}
            width={760}
        >
            <Form
                form={form}
                initialData={{
                    baseCredits: rule?.baseCredits ?? 0,
                    cachedInputCoefficient: rule?.cachedInputCoefficient ?? 1,
                    effectiveFrom: dateTimeInputValue(rule?.effectiveFrom) ?? dateTimeInputValue(new Date()),
                    effectiveTo: dateTimeInputValue(rule?.effectiveTo),
                    inputCoefficient: rule?.inputCoefficient ?? 1,
                    mcpCallCoefficient: rule?.mcpCallCoefficient ?? 1,
                    mcpToolKey: rule?.mcpToolKey ?? "",
                    minimumCredits: rule?.minimumCredits ?? 0,
                    modelId: rule?.modelId,
                    offpeakMultiplier: rule?.offpeakMultiplier ?? 1,
                    outputCoefficient: rule?.outputCoefficient ?? 1,
                    peakMultiplier: rule?.peakMultiplier ?? 1,
                    pricingMode: rule?.pricingMode ?? "token",
                    quotaBucket: rule?.quotaBucket ?? "",
                    targetType: rule?.targetType ?? "model",
                    unit: rule?.unit ?? "request",
                    unitCredits: rule?.unitCredits ?? 1,
                }}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit(form.getFieldsValue(true) as PriceRuleForm);
                    }
                }}
            >
                <div className="mb-5 rounded-xl bg-[var(--td-bg-color-secondarycontainer)] px-4 py-3 text-sm text-[var(--td-text-color-secondary)]">
                    价格规则决定每次请求扣多少积分。先选要计费的模型或工具，再按提示填写价格即可。
                </div>

                <Form.FormItem label="要计费什么" name="targetType" rules={[{ required: true }]}>
                    <RadioGroup
                        disabled={editing}
                        onChange={(value) => {
                            const next = String(value);
                            form.setFieldsValue({
                                mcpToolKey: undefined,
                                modelId: undefined,
                                pricingMode: next === "mcp_tool" ? "mcp_call" : "token",
                            });
                        }}
                        options={[...TARGET_TYPE_OPTIONS]}
                        theme="button"
                        value={targetType}
                    />
                </Form.FormItem>

                <Form.FormItem
                    help={
                        targetType === "mcp_tool"
                            ? "只能为已启用的 MCP 工具设置价格。"
                            : "选择要扣费的逻辑模型；同一模型同一时间只能有一条生效规则。"
                    }
                    label={targetType === "mcp_tool" ? "选择 MCP 工具" : "选择模型"}
                    name={targetType === "mcp_tool" ? "mcpToolKey" : "modelId"}
                    rules={[
                        {
                            message: targetType === "mcp_tool" ? "请选择 MCP 工具。" : "请选择模型。",
                            required: true,
                        },
                    ]}
                >
                    {targetType === "mcp_tool" ? (
                        <Select
                            disabled={editing}
                            filterable
                            loading={toolsQuery.isPending}
                            options={toolOptions}
                            placeholder="搜索并选择 MCP 工具"
                        />
                    ) : (
                        <Select
                            disabled={editing}
                            filterable
                            loading={modelsQuery.isPending}
                            options={modelOptions}
                            placeholder="搜索并选择模型"
                        />
                    )}
                </Form.FormItem>

                <Form.FormItem
                    help={pricingHint}
                    label="怎么计费"
                    name="pricingMode"
                    rules={[{ message: "请选择计费方式。", required: true }]}
                >
                    <RadioGroup
                        disabled={editing}
                        onChange={(value) => {
                            const next = String(value);
                            form.setFieldsValue({
                                pricingMode: next,
                                unit: next === "unit_credit" ? (rule?.unit ?? "request") : undefined,
                            });
                        }}
                        options={[...pricingOptions]}
                        theme="button"
                        value={pricingMode}
                    />
                </Form.FormItem>

                {pricingMode === "token" ? (
                    <div className="grid gap-x-4 md:grid-cols-2">
                        <Form.FormItem
                            help="每 1 万输入 Token 扣多少积分。"
                            label="输入 Token 单价"
                            name="inputCoefficient"
                        >
                            <InputNumber min={0} suffix="积分 / 万 Token" style={{ width: "100%" }} />
                        </Form.FormItem>
                        <Form.FormItem
                            help="每 1 万输出 Token 扣多少积分。"
                            label="输出 Token 单价"
                            name="outputCoefficient"
                        >
                            <InputNumber min={0} suffix="积分 / 万 Token" style={{ width: "100%" }} />
                        </Form.FormItem>
                    </div>
                ) : null}

                {pricingMode === "unit_credit" ? (
                    <div className="grid gap-x-4 md:grid-cols-3">
                        <Form.FormItem help="图片、视频等按哪种用量计算。" label="按什么单位" name="unit">
                            <Select options={[...UNIT_OPTIONS]} />
                        </Form.FormItem>
                        <Form.FormItem help="每个单位用量扣多少积分。" label="单位积分" name="unitCredits">
                            <InputNumber min={0} suffix="积分 / 单位" style={{ width: "100%" }} />
                        </Form.FormItem>
                        <Form.FormItem
                            help="每笔用量额外加上的积分，没有就填 0。"
                            label="每笔基础积分"
                            name="baseCredits"
                        >
                            <InputNumber min={0} suffix="积分 / 笔" style={{ width: "100%" }} />
                        </Form.FormItem>
                    </div>
                ) : null}

                {pricingMode === "mcp_call" ? (
                    <Form.FormItem
                        help="每次调用这个 MCP 工具扣多少积分。"
                        label="每次调用单价"
                        name="mcpCallCoefficient"
                    >
                        <InputNumber min={0} suffix="积分 / 次" style={{ width: "100%" }} />
                    </Form.FormItem>
                ) : null}

                <div className="mb-2 rounded-xl border border-[var(--td-border-level-1-color)] bg-[var(--td-bg-color-container-hover)] px-4 py-3">
                    <div className="text-xs font-medium text-[var(--td-text-color-secondary)]">费用示例</div>
                    <div className="mt-1 text-sm font-medium tabular-nums">{preview}</div>
                </div>

                <div className="my-4 flex items-center justify-between gap-3 border-t border-[var(--td-border-level-1-color)] pt-3">
                    <span className="text-xs text-[var(--td-text-color-placeholder)]">
                        默认立即生效、长期有效；需要定时生效或设置高峰加价时再展开高级设置。
                    </span>
                    <Button
                        icon={showAdvanced ? <ChevronUpIcon /> : <ChevronDownIcon />}
                        onClick={() => setShowAdvanced((current) => !current)}
                        size="small"
                        variant="text"
                    >
                        {showAdvanced ? "收起高级设置" : "高级设置"}
                    </Button>
                </div>

                {showAdvanced ? (
                    <div className="flex flex-col gap-4 rounded-xl border border-[var(--td-border-level-1-color)] p-4">
                        <div className="grid gap-x-4 md:grid-cols-2">
                            <Form.FormItem
                                help="一般留空。只有需要把用量拆到多个配额池时才填写。"
                                label="配额桶"
                                name="quotaBucket"
                            >
                                <Input maxlength={64} placeholder="可选，一般不用填" />
                            </Form.FormItem>
                            <Form.FormItem
                                help="无论用量多少，每笔至少扣这么多积分。"
                                label="每笔最低扣分"
                                name="minimumCredits"
                            >
                                <InputNumber min={0} suffix="积分" style={{ width: "100%" }} />
                            </Form.FormItem>
                        </div>

                        {pricingMode === "token" ? (
                            <Form.FormItem
                                help="命中缓存时每 1 万输入 Token 的单价；不区分缓存就与输入 Token 单价保持一致。"
                                label="缓存输入 Token 单价"
                                name="cachedInputCoefficient"
                            >
                                <InputNumber min={0} suffix="积分 / 万 Token" style={{ width: "100%" }} />
                            </Form.FormItem>
                        ) : null}

                        <div className="grid gap-x-4 md:grid-cols-2">
                            <Form.FormItem
                                help="不填写则创建后立即生效。"
                                label="生效时间"
                                name="effectiveFrom"
                                rules={[{ message: "请选择生效时间。", required: true }]}
                            >
                                <DatePicker
                                    enableTimePicker
                                    format="YYYY-MM-DD HH:mm:ss"
                                    style={{ width: "100%" }}
                                    valueType="YYYY-MM-DD HH:mm:ss"
                                />
                            </Form.FormItem>
                            <Form.FormItem label="失效时间" name="effectiveTo">
                                <DatePicker
                                    enableTimePicker
                                    format="YYYY-MM-DD HH:mm:ss"
                                    placeholder="留空表示长期有效"
                                    style={{ width: "100%" }}
                                    valueType="YYYY-MM-DD HH:mm:ss"
                                />
                            </Form.FormItem>
                        </div>

                        <div className="flex items-center justify-between gap-3 border-t border-[var(--td-border-level-1-color)] pt-4">
                            <div>
                                <div className="text-sm font-medium">高峰时段加价</div>
                                <p className="m-0 mt-1 text-xs text-[var(--td-text-color-placeholder)]">
                                    开启后可在指定日期和时段使用更高倍率。
                                </p>
                            </div>
                            <Switch onChange={(value) => setPeakEnabled(Boolean(value))} value={peakEnabled} />
                        </div>

                        {peakEnabled ? (
                            <>
                                <div className="grid gap-x-4 md:grid-cols-2">
                                    <Form.FormItem
                                        help="高峰时段内按这个倍率扣分。"
                                        label="高峰倍率"
                                        name="peakMultiplier"
                                    >
                                        <InputNumber min={0} suffix="倍" style={{ width: "100%" }} />
                                    </Form.FormItem>
                                    <Form.FormItem
                                        help="其它时间按这个倍率扣分。"
                                        label="非高峰倍率"
                                        name="offpeakMultiplier"
                                    >
                                        <InputNumber min={0} suffix="倍" style={{ width: "100%" }} />
                                    </Form.FormItem>
                                </div>

                                <div className="flex items-center justify-between gap-3">
                                    <div className="text-sm font-medium">高峰时段</div>
                                    <Button
                                        icon={<AddIcon />}
                                        onClick={() =>
                                            setPeakWindows((current) => [...current, { ...DEFAULT_PEAK_WINDOW }])
                                        }
                                        size="small"
                                        variant="outline"
                                    >
                                        添加时段
                                    </Button>
                                </div>
                                {peakWindows.length === 0 ? (
                                    <div className="rounded-lg border border-dashed border-[var(--td-border-level-1-color)] px-4 py-3 text-sm text-[var(--td-text-color-placeholder)]">
                                        尚未添加时段，当前不会触发高峰价。
                                    </div>
                                ) : (
                                    <div className="flex flex-col gap-2">
                                        {peakWindows.map((window, index) => (
                                            <div
                                                className="grid gap-2 rounded-lg border border-[var(--td-border-level-1-color)] p-3 md:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.2fr)_auto]"
                                                key={index}
                                            >
                                                <Select
                                                    multiple
                                                    onChange={(value) =>
                                                        setPeakWindows((current) =>
                                                            current.map((entry, entryIndex) =>
                                                                entryIndex === index
                                                                    ? { ...entry, days: (value as string[]) ?? [] }
                                                                    : entry,
                                                            ),
                                                        )
                                                    }
                                                    options={[...WEEKDAY_OPTIONS]}
                                                    placeholder="星期"
                                                    value={window.days}
                                                />
                                                <TimePicker
                                                    format="HH:mm"
                                                    onChange={(value) =>
                                                        setPeakWindows((current) =>
                                                            current.map((entry, entryIndex) =>
                                                                entryIndex === index
                                                                    ? { ...entry, start: String(value) }
                                                                    : entry,
                                                            ),
                                                        )
                                                    }
                                                    placeholder="开始"
                                                    value={window.start}
                                                />
                                                <TimePicker
                                                    format="HH:mm"
                                                    onChange={(value) =>
                                                        setPeakWindows((current) =>
                                                            current.map((entry, entryIndex) =>
                                                                entryIndex === index
                                                                    ? { ...entry, end: String(value) }
                                                                    : entry,
                                                            ),
                                                        )
                                                    }
                                                    placeholder="结束"
                                                    value={window.end}
                                                />
                                                <Input
                                                    onChange={(value) =>
                                                        setPeakWindows((current) =>
                                                            current.map((entry, entryIndex) =>
                                                                entryIndex === index
                                                                    ? { ...entry, zone: value }
                                                                    : entry,
                                                            ),
                                                        )
                                                    }
                                                    placeholder="时区"
                                                    value={window.zone}
                                                />
                                                <Button
                                                    aria-label="删除高峰时段"
                                                    icon={<DeleteIcon />}
                                                    onClick={() =>
                                                        setPeakWindows((current) =>
                                                            current.filter((_, entryIndex) => entryIndex !== index),
                                                        )
                                                    }
                                                    shape="square"
                                                    variant="text"
                                                />
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </>
                        ) : null}
                    </div>
                ) : null}
            </Form>
        </GlobalDialog>
    );
}

function numeric(value: number | string | undefined): number {
    const parsed = Number(value);

    return Number.isFinite(parsed) ? parsed : 0;
}
