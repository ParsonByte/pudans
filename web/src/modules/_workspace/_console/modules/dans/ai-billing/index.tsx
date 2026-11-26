"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { RefreshIcon, TagIcon } from "tdesign-icons-react";
import { Button, Tabs, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import {
    type AiPriceRule,
    type AiQuotaAssignment,
    type AiQuotaPlan,
    listAiPriceRules,
    listAiQuotaAssignments,
    listAiQuotaPlans,
} from "../composables/_dans";

const TABS = [
    { label: "价格规则", value: "prices" },
    { label: "配额计划", value: "plans" },
    { label: "配额分配", value: "assignments" },
] as const;

type BillingTab = (typeof TABS)[number]["value"];

export function AiBillingView() {
    const [tab, setTab] = useState<BillingTab>("prices");
    const pricesQuery = useQuery({ queryFn: listAiPriceRules, queryKey: ["dans", "ai", "price-rules"] });
    const plansQuery = useQuery({ queryFn: listAiQuotaPlans, queryKey: ["dans", "ai", "quota-plans"] });
    const assignmentsQuery = useQuery({
        queryFn: listAiQuotaAssignments,
        queryKey: ["dans", "ai", "quota-assignments"],
    });
    const prices = pricesQuery.data ?? [];
    const plans = plansQuery.data ?? [];
    const assignments = assignmentsQuery.data ?? [];
    const activeQuery = tab === "prices" ? pricesQuery : tab === "plans" ? plansQuery : assignmentsQuery;
    const refresh = () => {
        void pricesQuery.refetch();
        void plansQuery.refetch();
        void assignmentsQuery.refetch();
    };
    const planLabel = (id?: string) => {
        const plan = plans.find((item) => item.id === id);

        return plan ? (plan.displayName ?? plan.name ?? plan.id) : (id ?? "—");
    };
    const priceColumns: TableProps<AiPriceRule>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.targetType ?? <EmptyValue />}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                        {row.targetKey ?? row.modelId ?? row.mcpToolKey ?? row.id}
                    </span>
                </div>
            ),
            colKey: "targetType",
            title: "计费对象",
        },
        { cell: ({ row }) => row.pricingMode ?? <EmptyValue />, colKey: "pricingMode", title: "计价模式" },
        {
            cell: ({ row }) => `${row.unitCredits ?? row.baseCredits ?? "—"} / ${row.minimumCredits ?? "—"}`,
            colKey: "unitCredits",
            title: "单价 / 最低消耗",
        },
        { cell: ({ row }) => row.unit ?? <EmptyValue />, colKey: "unit", title: "计费单位" },
        { cell: ({ row }) => row.quotaBucket ?? <EmptyValue />, colKey: "quotaBucket", title: "配额桶" },
        {
            cell: ({ row }) => (
                <div className="flex flex-col gap-1 text-xs">
                    <span>起 {formatDateTime(row.effectiveFrom)}</span>
                    <span>止 {formatDateTime(row.effectiveTo)}</span>
                </div>
            ),
            colKey: "effectiveFrom",
            title: "生效区间",
        },
    ];
    const planColumns: TableProps<AiQuotaPlan>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.displayName ?? row.name ?? <EmptyValue />}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">{row.id}</span>
                </div>
            ),
            colKey: "name",
            title: "配额计划",
        },
        {
            cell: ({ row }) => `${row.fiveHourCredits ?? "—"} / ${row.weeklyCredits ?? "—"}`,
            colKey: "fiveHourCredits",
            title: "5 小时 / 每周额度",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "active" ? "success" : "default"} variant="light">
                    {row.status ?? "未设置"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
    ];
    const assignmentColumns: TableProps<AiQuotaAssignment>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.subjectType ?? <EmptyValue />}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                        {row.subjectId ?? row.id}
                    </span>
                </div>
            ),
            colKey: "subjectId",
            title: "分配主体",
        },
        { cell: ({ row }) => planLabel(row.planId), colKey: "planId", title: "配额计划" },
        {
            cell: ({ row }) => (
                <div className="flex flex-col gap-1 text-xs">
                    <span>起 {formatDateTime(row.effectiveFrom)}</span>
                    <span>止 {formatDateTime(row.effectiveTo)}</span>
                </div>
            ),
            colKey: "effectiveFrom",
            title: "生效区间",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "active" ? "success" : "default"} variant="light">
                    {row.status ?? "未设置"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
    ];

    return (
        <DansPage
            actions={
                <Button icon={<RefreshIcon />} loading={activeQuery.isFetching} onClick={refresh} variant="outline">
                    刷新
                </Button>
            }
            description="价格规则、配额计划和主体配额分配分别对应 AI 计费与配额接口。"
            icon={<TagIcon size={22} />}
            metrics={[
                { label: "价格规则", value: formatCountOrDash(pricesQuery.data?.length) },
                { label: "配额计划", value: formatCountOrDash(plansQuery.data?.length) },
                { label: "配额分配", value: formatCountOrDash(assignmentsQuery.data?.length) },
            ]}
            section="AI"
            title="计费与配额"
        >
            <Tabs
                list={TABS.map((item) => ({ label: item.label, value: item.value }))}
                onChange={(value) => setTab(String(value) as BillingTab)}
                value={tab}
            />
            {tab === "prices" ? (
                <ConsoleTable
                    columns={priceColumns}
                    data={prices}
                    error={pricesQuery.error}
                    loading={pricesQuery.isPending}
                    onRetry={() => void pricesQuery.refetch()}
                    rowKey="id"
                    title="价格规则"
                />
            ) : tab === "plans" ? (
                <ConsoleTable
                    columns={planColumns}
                    data={plans}
                    error={plansQuery.error}
                    loading={plansQuery.isPending}
                    onRetry={() => void plansQuery.refetch()}
                    rowKey="id"
                    title="配额计划"
                />
            ) : (
                <ConsoleTable
                    columns={assignmentColumns}
                    data={assignments}
                    error={assignmentsQuery.error}
                    loading={assignmentsQuery.isPending}
                    onRetry={() => void assignmentsQuery.refetch()}
                    rowKey="id"
                    title="配额分配"
                />
            )}
        </DansPage>
    );
}
