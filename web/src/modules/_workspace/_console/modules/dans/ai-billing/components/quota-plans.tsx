"use client";

import { useMemo, useState } from "react";

import { BillIcon, MoreIcon, SearchIcon } from "tdesign-icons-react";
import { Alert, Button, Card, Dropdown, Empty, Input, Select, Skeleton, Tag } from "tdesign-react";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import {
    STATUS_OPTIONS,
    formatCredits,
    formatWindowDays,
    formatWindowDuration,
    planStatusLabel,
    weeklyAnchorLabel,
} from "../../composables/_billing-meta";
import type { AiQuotaPlan } from "../../composables/_dans";

type QuotaPlansProps = {
    assignmentCounts: Map<string, number>;
    error?: unknown;
    loading: boolean;
    onCreate?: () => void;
    onDelete?: (plan: AiQuotaPlan) => void;
    onEdit?: (plan: AiQuotaPlan) => void;
    onRetry: () => void;
    plans: AiQuotaPlan[];
};

export function QuotaPlans({
    assignmentCounts,
    error,
    loading,
    onCreate,
    onDelete,
    onEdit,
    onRetry,
    plans,
}: QuotaPlansProps) {
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState("all");
    const normalizedKeyword = keyword.trim().toLowerCase();
    const filteredPlans = useMemo(
        () =>
            plans
                .filter((plan) => {
                    if (status !== "all" && plan.status !== status) {
                        return false;
                    }
                    if (!normalizedKeyword) {
                        return true;
                    }

                    return [plan.displayName, plan.name, plan.id, plan.weeklyAnchorMode].some((value) =>
                        value?.toLowerCase().includes(normalizedKeyword),
                    );
                })
                .sort((left, right) => {
                    const statusOrder = Number(right.status === "active") - Number(left.status === "active");

                    return (
                        statusOrder ||
                        (left.displayName ?? left.name ?? left.id).localeCompare(
                            right.displayName ?? right.name ?? right.id,
                            "zh-CN",
                        )
                    );
                }),
        [normalizedKeyword, plans, status],
    );
    const hasFilters = Boolean(keyword) || status !== "all";
    const clearFilters = () => {
        setKeyword("");
        setStatus("all");
    };

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex min-w-0 flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <div className="w-full min-w-0 sm:w-[320px]">
                        <Input
                            clearable
                            onChange={(value) => setKeyword(value)}
                            placeholder="搜索计划名称或标识"
                            prefixIcon={<SearchIcon />}
                            value={keyword}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[150px]">
                        <Select
                            onChange={(value) => setStatus(String(value))}
                            options={STATUS_OPTIONS.map((option) => ({ ...option }))}
                            value={status}
                        />
                    </div>
                    {hasFilters ? (
                        <Button onClick={clearFilters} size="small" variant="text">
                            清除筛选
                        </Button>
                    ) : null}
                </div>
                <span className="shrink-0 text-xs text-[var(--td-text-color-secondary)]">
                    {filteredPlans.length === plans.length
                        ? `共 ${plans.length} 个配额计划`
                        : `筛选出 ${filteredPlans.length} / ${plans.length} 个`}
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
                <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-2">
                    {Array.from({ length: 4 }, (_, index) => (
                        <Card bordered className="min-w-0" key={index}>
                            <Skeleton animation="gradient" rowCol={[1, 2, 1, 2]} />
                        </Card>
                    ))}
                </div>
            ) : plans.length === 0 ? (
                <Card bordered={false}>
                    <Empty
                        action={
                            onCreate ? (
                                <Button onClick={onCreate} variant="outline">
                                    新建配额计划
                                </Button>
                            ) : undefined
                        }
                        description="当前没有配额计划。配额计划定义 5 小时与每周窗口内的积分、工具调用和 MCP 调用上限。"
                    />
                </Card>
            ) : filteredPlans.length === 0 ? (
                <Card bordered={false}>
                    <Empty
                        action={
                            <Button onClick={clearFilters} variant="outline">
                                清除筛选
                            </Button>
                        }
                        description="没有匹配的配额计划。"
                    />
                </Card>
            ) : (
                <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-2">
                    {filteredPlans.map((plan) => (
                        <QuotaPlanCard
                            assignmentCount={assignmentCounts.get(plan.id) ?? 0}
                            key={plan.id}
                            onDelete={onDelete ? () => onDelete(plan) : undefined}
                            onEdit={onEdit ? () => onEdit(plan) : undefined}
                            plan={plan}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

function QuotaPlanCard({
    assignmentCount,
    onDelete,
    onEdit,
    plan,
}: {
    assignmentCount: number;
    onDelete?: () => void;
    onEdit?: () => void;
    plan: AiQuotaPlan;
}) {
    const active = plan.status === "active";
    const name = plan.name ?? plan.id;

    return (
        <Card bordered className="h-full min-w-0">
            <article className="flex h-full min-w-0 flex-col gap-4">
                <div className="flex min-w-0 items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                        <BillIcon size={20} />
                    </span>
                    <div className="min-w-0 flex-1">
                        <div className="flex min-w-0 items-start justify-between gap-2">
                            <h3
                                className="m-0 truncate text-base font-semibold"
                                title={plan.displayName ?? plan.name ?? plan.id}
                            >
                                {plan.displayName ?? plan.name ?? plan.id}
                            </h3>
                            <div className="flex shrink-0 items-center gap-1">
                                <Tag theme={active ? "success" : "default"} variant="light">
                                    {planStatusLabel(plan.status)}
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
                                            aria-label="配额计划操作"
                                            icon={<MoreIcon />}
                                            shape="square"
                                            size="small"
                                            variant="text"
                                        />
                                    </Dropdown>
                                ) : null}
                            </div>
                        </div>
                        <div className="mt-1 min-w-0">
                            <CopyableIdentifier
                                className="max-w-full"
                                display={
                                    <span className="min-w-0 truncate" title={name}>
                                        {name}
                                    </span>
                                }
                                quiet
                                value={name}
                            />
                        </div>
                    </div>
                </div>

                <div className="grid grid-cols-2 gap-3 border-y border-[var(--td-border-level-1-color)] py-3">
                    <PlanMetric label="5 小时积分" value={formatCredits(plan.fiveHourCredits)} />
                    <PlanMetric label="每周积分" value={formatCredits(plan.weeklyCredits)} />
                </div>

                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    <PlanMetric label="5h 工具" value={formatCount(plan.fiveHourToolCalls)} />
                    <PlanMetric label="每周工具" value={formatCount(plan.weeklyToolCalls)} />
                    <PlanMetric label="5h MCP" value={formatCount(plan.fiveHourMcpCalls)} />
                    <PlanMetric label="每周 MCP" value={formatCount(plan.weeklyMcpCalls)} />
                </div>

                <p className="m-0 text-xs text-[var(--td-text-color-secondary)]">
                    5 小时窗口 {formatWindowDuration(plan.fiveHourWindowSeconds)} · 周窗口{" "}
                    {formatWindowDays(plan.weeklyWindowDays)} · {weeklyAnchorLabel(plan.weeklyAnchorMode)}
                </p>

                <div className="mt-auto flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--td-text-color-placeholder)]">
                    <span>{formatCount(assignmentCount)} 个配额分配</span>
                    <span>更新于 {formatDateTime(plan.updatedAt)}</span>
                </div>
            </article>
        </Card>
    );
}

function PlanMetric({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex min-w-0 flex-col gap-0.5">
            <span className="text-[11px] text-[var(--td-text-color-secondary)]">{label}</span>
            <span className="truncate font-medium tabular-nums" title={value}>
                {value}
            </span>
        </div>
    );
}
