"use client";

import { useMemo, useState } from "react";

import { ApiIcon, CpuIcon, MoreIcon, SearchIcon } from "tdesign-icons-react";
import { Alert, Button, Card, Dropdown, Empty, Input, Select, Skeleton, Tag } from "tdesign-react";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatDateTime } from "../../../../composables/_format";
import {
    PRICE_RULE_STATE_OPTIONS,
    PRICING_MODE_OPTIONS,
    type PriceRuleState,
    TARGET_TYPE_OPTIONS,
    formatCredits,
    priceRuleState,
    priceRuleStateLabel,
    pricingModeLabel,
    targetTypeLabel,
    unitLabel,
} from "../../composables/_billing-meta";
import type { AiPriceRule } from "../../composables/_dans";

const STATE_THEME: Record<PriceRuleState, "default" | "success" | "warning"> = {
    active: "success",
    expired: "default",
    pending: "warning",
    unknown: "default",
};

const STATE_ORDER: Record<PriceRuleState, number> = {
    active: 0,
    pending: 1,
    expired: 2,
    unknown: 3,
};

type PriceRulesProps = {
    error?: unknown;
    loading: boolean;
    onCreate?: () => void;
    onDelete?: (rule: AiPriceRule) => void;
    onEdit?: (rule: AiPriceRule) => void;
    onRetry: () => void;
    rules: AiPriceRule[];
};

export function PriceRules({ error, loading, onCreate, onDelete, onEdit, onRetry, rules }: PriceRulesProps) {
    const [keyword, setKeyword] = useState("");
    const [targetType, setTargetType] = useState("all");
    const [pricingMode, setPricingMode] = useState("all");
    const [state, setState] = useState("all");
    const normalizedKeyword = keyword.trim().toLowerCase();
    const filteredRules = useMemo(() => {
        return rules
            .map((rule) => ({ rule, state: priceRuleState(rule) }))
            .filter((entry) => {
                if (targetType !== "all" && entry.rule.targetType !== targetType) {
                    return false;
                }
                if (pricingMode !== "all" && entry.rule.pricingMode !== pricingMode) {
                    return false;
                }
                if (state !== "all" && entry.state !== state) {
                    return false;
                }
                if (!normalizedKeyword) {
                    return true;
                }

                return [
                    entry.rule.targetKey,
                    entry.rule.mcpToolKey,
                    entry.rule.modelId,
                    entry.rule.quotaBucket,
                    targetTypeLabel(entry.rule.targetType),
                    pricingModeLabel(entry.rule.pricingMode),
                ].some((value) => value?.toLowerCase().includes(normalizedKeyword));
            })
            .sort((left, right) => {
                const stateOrder = STATE_ORDER[left.state] - STATE_ORDER[right.state];
                const leftFrom = left.rule.effectiveFrom ? Date.parse(left.rule.effectiveFrom) : 0;
                const rightFrom = right.rule.effectiveFrom ? Date.parse(right.rule.effectiveFrom) : 0;

                return (
                    stateOrder ||
                    rightFrom - leftFrom ||
                    (left.rule.targetKey ?? left.rule.id).localeCompare(right.rule.targetKey ?? right.rule.id, "zh-CN")
                );
            });
    }, [normalizedKeyword, pricingMode, rules, state, targetType]);
    const hasFilters = Boolean(keyword) || targetType !== "all" || pricingMode !== "all" || state !== "all";
    const clearFilters = () => {
        setKeyword("");
        setTargetType("all");
        setPricingMode("all");
        setState("all");
    };

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex min-w-0 flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <div className="w-full min-w-0 sm:w-[280px]">
                        <Input
                            clearable
                            onChange={(value) => setKeyword(value)}
                            placeholder="搜索模型、MCP 工具或配额桶"
                            prefixIcon={<SearchIcon />}
                            value={keyword}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[140px]">
                        <Select
                            onChange={(value) => setTargetType(String(value))}
                            options={TARGET_TYPE_OPTIONS.map((option) => ({ ...option }))}
                            value={targetType}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[150px]">
                        <Select
                            onChange={(value) => setPricingMode(String(value))}
                            options={PRICING_MODE_OPTIONS.map((option) => ({ ...option }))}
                            value={pricingMode}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[140px]">
                        <Select
                            onChange={(value) => setState(String(value))}
                            options={PRICE_RULE_STATE_OPTIONS.map((option) => ({ ...option }))}
                            value={state}
                        />
                    </div>
                    {hasFilters ? (
                        <Button onClick={clearFilters} size="small" variant="text">
                            清除筛选
                        </Button>
                    ) : null}
                </div>
                <span className="shrink-0 text-xs text-[var(--td-text-color-secondary)]">
                    {filteredRules.length === rules.length
                        ? `共 ${rules.length} 条价格规则`
                        : `筛选出 ${filteredRules.length} / ${rules.length} 条`}
                </span>
            </div>

            {error ? (
                <Alert
                    message={consoleErrorMessage(error)}
                    operation={
                        <Button onClick={onRetry} size="small" variant="text">
                            重试
                        </Button>
                    }
                    theme="error"
                />
            ) : loading ? (
                <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                    {Array.from({ length: 6 }, (_, index) => (
                        <Card bordered className="min-w-0" key={index}>
                            <Skeleton animation="gradient" rowCol={[1, 2, 1, 2]} />
                        </Card>
                    ))}
                </div>
            ) : rules.length === 0 ? (
                <Card bordered={false}>
                    <Empty
                        action={
                            onCreate ? (
                                <Button onClick={onCreate} variant="outline">
                                    新建价格规则
                                </Button>
                            ) : undefined
                        }
                        description="当前没有价格规则。价格规则定义模型 Token、单位用量或 MCP 调用如何折算为积分。"
                    />
                </Card>
            ) : filteredRules.length === 0 ? (
                <Card bordered={false}>
                    <Empty
                        action={
                            <Button onClick={clearFilters} variant="outline">
                                清除筛选
                            </Button>
                        }
                        description="没有匹配的价格规则。"
                    />
                </Card>
            ) : (
                <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                    {filteredRules.map((entry) => (
                        <PriceRuleCard
                            key={entry.rule.id}
                            onDelete={onDelete ? () => onDelete(entry.rule) : undefined}
                            onEdit={onEdit ? () => onEdit(entry.rule) : undefined}
                            rule={entry.rule}
                            state={entry.state}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

function PriceRuleCard({
    onDelete,
    onEdit,
    rule,
    state,
}: {
    onDelete?: () => void;
    onEdit?: () => void;
    rule: AiPriceRule;
    state: PriceRuleState;
}) {
    const targetName = rule.targetKey ?? rule.mcpToolKey ?? rule.modelId ?? rule.id;
    const Icon = rule.targetType === "mcp_tool" ? ApiIcon : CpuIcon;

    return (
        <Card bordered className="h-full min-w-0">
            <article className="flex min-w-0 flex-col gap-4">
                <div className="flex min-w-0 items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                        <Icon size={20} />
                    </span>
                    <div className="min-w-0 flex-1">
                        <h3 className="m-0 truncate text-base font-semibold" title={targetName}>
                            {targetName}
                        </h3>
                        <div className="mt-1 flex flex-wrap items-center gap-1.5">
                            <Tag size="small" theme="primary" variant="light">
                                {targetTypeLabel(rule.targetType)}
                            </Tag>
                            <Tag size="small" variant="outline">
                                {pricingModeLabel(rule.pricingMode)}
                            </Tag>
                        </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                        <Tag theme={STATE_THEME[state]} variant="light">
                            {priceRuleStateLabel(state)}
                        </Tag>
                        {onEdit || onDelete ? (
                            <Dropdown
                                onClick={(item) => {
                                    if (item.value === "edit") {
                                        onEdit?.();
                                    } else if (item.value === "delete") {
                                        onDelete?.();
                                    }
                                }}
                                options={[
                                    { content: "编辑", value: "edit" },
                                    { content: "删除", theme: "error", value: "delete" },
                                ]}
                                trigger="click"
                            >
                                <Button
                                    aria-label="价格规则操作"
                                    icon={<MoreIcon />}
                                    shape="square"
                                    size="small"
                                    variant="text"
                                />
                            </Dropdown>
                        ) : null}
                    </div>
                </div>

                <div className="grid grid-cols-2 gap-2">
                    {priceMetrics(rule).map((metric) => (
                        <div
                            className="flex min-w-0 flex-col gap-0.5 rounded-lg bg-[var(--td-bg-color-secondarycontainer)] px-3 py-2"
                            key={metric.label}
                        >
                            <span className="text-[11px] text-[var(--td-text-color-secondary)]">{metric.label}</span>
                            <span className="truncate font-medium tabular-nums" title={metric.value}>
                                {metric.value}
                            </span>
                        </div>
                    ))}
                </div>

                <div className="text-xs text-[var(--td-text-color-secondary)]">
                    {rule.peakWindows?.length
                        ? `高峰时段 ${rule.peakWindows.length} 段 · 高峰 ${formatCredits(rule.peakMultiplier)} / 非高峰 ${formatCredits(rule.offpeakMultiplier)}`
                        : `未设置高峰时段 · 全天倍率 ${formatCredits(rule.offpeakMultiplier)}`}
                </div>

                <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-[var(--td-border-level-1-color)] pt-3 text-xs text-[var(--td-text-color-placeholder)]">
                    <span>
                        生效 {formatDateTime(rule.effectiveFrom)}
                        <span className="mx-1.5">·</span>
                        {rule.effectiveTo ? `失效 ${formatDateTime(rule.effectiveTo)}` : "长期有效"}
                    </span>
                    <span>更新于 {formatDateTime(rule.updatedAt)}</span>
                </div>
            </article>
        </Card>
    );
}

function priceMetrics(rule: AiPriceRule): Array<{ label: string; value: string }> {
    if (rule.pricingMode === "token") {
        return [
            { label: "输入系数", value: formatCredits(rule.inputCoefficient) },
            { label: "缓存输入", value: formatCredits(rule.cachedInputCoefficient) },
            { label: "输出系数", value: formatCredits(rule.outputCoefficient) },
            { label: "最低积分", value: formatCredits(rule.minimumCredits) },
        ];
    }
    if (rule.pricingMode === "unit_credit") {
        return [
            {
                label: `单位积分 / ${unitLabel(rule.unit)}`,
                value: formatCredits(rule.unitCredits),
            },
            { label: "基础积分", value: formatCredits(rule.baseCredits) },
            { label: "最低积分", value: formatCredits(rule.minimumCredits) },
        ];
    }
    if (rule.pricingMode === "mcp_call") {
        return [
            { label: "单位调用系数", value: formatCredits(rule.mcpCallCoefficient) },
            { label: "最低积分", value: formatCredits(rule.minimumCredits) },
        ];
    }

    return [{ label: "计价配置", value: "—" }];
}
