"use client";

import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, BillIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, RadioGroup } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import { DansPage } from "../components/page";
import {
    type AiPriceRule,
    type AiQuotaAssignment,
    type AiQuotaPlan,
    deleteAiPriceRule,
    deleteAiQuotaAssignment,
    deleteAiQuotaPlan,
    listAiPriceRules,
    listAiQuotaAssignments,
    listAiQuotaPlans,
} from "../composables/_dans";
import { PriceRuleDialog } from "./components/price-rule-dialog";
import { PriceRules } from "./components/price-rules";
import { QuotaAssignmentDialog } from "./components/quota-assignment-dialog";
import { QuotaAssignments } from "./components/quota-assignments";
import { QuotaPlanDialog } from "./components/quota-plan-dialog";
import { QuotaPlans } from "./components/quota-plans";

const TABS = [
    { label: "价格规则", value: "prices" },
    { label: "配额计划", value: "plans" },
    { label: "配额分配", value: "assignments" },
] as const;

type BillingTab = (typeof TABS)[number]["value"];

type BillingDialog =
    | { kind: "price"; rule?: AiPriceRule }
    | { kind: "plan"; plan?: AiQuotaPlan }
    | { kind: "assignment"; assignment?: AiQuotaAssignment }
    | null;

export function AiBillingView() {
    const [tab, setTab] = useState<BillingTab>("prices");
    const [dialog, setDialog] = useState<BillingDialog>(null);
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const { allowed: canManage } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        "ai-billing:manage",
    );
    const pricesQuery = useQuery({ queryFn: listAiPriceRules, queryKey: ["dans", "ai", "price-rules"] });
    const plansQuery = useQuery({ queryFn: listAiQuotaPlans, queryKey: ["dans", "ai", "quota-plans"] });
    const assignmentsQuery = useQuery({
        queryFn: listAiQuotaAssignments,
        queryKey: ["dans", "ai", "quota-assignments"],
    });
    const prices = useMemo<AiPriceRule[]>(() => pricesQuery.data ?? [], [pricesQuery.data]);
    const plans = useMemo<AiQuotaPlan[]>(() => plansQuery.data ?? [], [plansQuery.data]);
    const assignments = useMemo<AiQuotaAssignment[]>(() => assignmentsQuery.data ?? [], [assignmentsQuery.data]);
    const assignmentCounts = useMemo(() => {
        const counts = new Map<string, number>();
        for (const assignment of assignments) {
            if (!assignment.planId) {
                continue;
            }
            counts.set(assignment.planId, (counts.get(assignment.planId) ?? 0) + 1);
        }

        return counts;
    }, [assignments]);
    const refreshing = pricesQuery.isFetching || plansQuery.isFetching || assignmentsQuery.isFetching;
    const refresh = () => {
        void pricesQuery.refetch();
        void plansQuery.refetch();
        void assignmentsQuery.refetch();
    };
    const invalidatePrices = () => void queryClient.invalidateQueries({ queryKey: ["dans", "ai", "price-rules"] });
    const invalidatePlans = () => void queryClient.invalidateQueries({ queryKey: ["dans", "ai", "quota-plans"] });
    const invalidateAssignments = () =>
        void queryClient.invalidateQueries({ queryKey: ["dans", "ai", "quota-assignments"] });
    const deleteMutation = useMutation({
        mutationFn: async (target: Exclude<BillingDialog, null>) => {
            if (target.kind === "price" && target.rule) {
                await deleteAiPriceRule(target.rule.id);
                return "价格规则已删除。";
            }
            if (target.kind === "plan" && target.plan) {
                await deleteAiQuotaPlan(target.plan.id);
                return "配额计划已删除。";
            }
            if (target.kind === "assignment" && target.assignment) {
                await deleteAiQuotaAssignment(target.assignment.id);
                return "配额分配已删除。";
            }
            throw new Error("删除目标不存在。");
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (message, target) => {
            if (target.kind === "price") {
                invalidatePrices();
            } else if (target.kind === "plan") {
                invalidatePlans();
            } else {
                invalidateAssignments();
            }
            prompt.success({ content: message });
        },
    });
    const deleteRule = (rule: AiPriceRule) =>
        confirmGlobalDialog({
            content: `删除后不可恢复，确定删除价格规则“${rule.targetKey ?? rule.mcpToolKey ?? rule.modelId ?? rule.id}”吗？`,
            danger: true,
            onConfirm: () => deleteMutation.mutate({ kind: "price", rule }),
            title: "删除价格规则",
        });
    const deletePlan = (plan: AiQuotaPlan) =>
        confirmGlobalDialog({
            content: `删除后不可恢复，确定删除配额计划“${plan.displayName ?? plan.name ?? plan.id}”吗？`,
            danger: true,
            onConfirm: () => deleteMutation.mutate({ kind: "plan", plan }),
            title: "删除配额计划",
        });
    const deleteAssignment = (assignment: AiQuotaAssignment) =>
        confirmGlobalDialog({
            content: `删除后不可恢复，确定删除主体“${assignment.subjectId}”的配额分配吗？`,
            danger: true,
            onConfirm: () => deleteMutation.mutate({ kind: "assignment", assignment }),
            title: "删除配额分配",
        });
    const createDialog = () => {
        if (tab === "prices") {
            setDialog({ kind: "price" });
        } else if (tab === "plans") {
            setDialog({ kind: "plan" });
        } else {
            setDialog({ kind: "assignment" });
        }
    };
    const createLabel = tab === "prices" ? "新建价格规则" : tab === "plans" ? "新建配额计划" : "新建配额分配";

    return (
        <DansPage
            actions={
                <div className="flex flex-wrap items-center justify-end gap-2">
                    {canManage ? (
                        <Button icon={<AddIcon />} onClick={createDialog} theme="primary">
                            {createLabel}
                        </Button>
                    ) : null}
                    <Button icon={<RefreshIcon />} loading={refreshing} onClick={refresh} variant="outline">
                        刷新
                    </Button>
                </div>
            }
            icon={<BillIcon size={22} />}
            section="AI"
            showOverview={false}
            title="计费与配额"
        >
            <RadioGroup
                className="w-fit"
                onChange={(value) => setTab(String(value) as BillingTab)}
                options={TABS.map((item) => ({ label: item.label, value: item.value }))}
                theme="button"
                value={tab}
                variant="default-filled"
            />
            {tab === "prices" ? (
                <PriceRules
                    error={pricesQuery.error}
                    loading={pricesQuery.isPending}
                    onCreate={canManage ? () => setDialog({ kind: "price" }) : undefined}
                    onDelete={canManage ? deleteRule : undefined}
                    onEdit={canManage ? (rule) => setDialog({ kind: "price", rule }) : undefined}
                    onRetry={() => void pricesQuery.refetch()}
                    rules={prices}
                />
            ) : tab === "plans" ? (
                <QuotaPlans
                    assignmentCounts={assignmentCounts}
                    error={plansQuery.error}
                    loading={plansQuery.isPending}
                    onCreate={canManage ? () => setDialog({ kind: "plan" }) : undefined}
                    onDelete={canManage ? deletePlan : undefined}
                    onEdit={canManage ? (plan) => setDialog({ kind: "plan", plan }) : undefined}
                    onRetry={() => void plansQuery.refetch()}
                    plans={plans}
                />
            ) : (
                <QuotaAssignments
                    assignments={assignments}
                    error={assignmentsQuery.error}
                    loading={assignmentsQuery.isPending}
                    onCreate={canManage ? () => setDialog({ kind: "assignment" }) : undefined}
                    onDelete={canManage ? deleteAssignment : undefined}
                    onEdit={canManage ? (assignment) => setDialog({ kind: "assignment", assignment }) : undefined}
                    onRetry={() => void assignmentsQuery.refetch()}
                    onShowPlans={() => setTab("plans")}
                    plans={plans}
                />
            )}
            {dialog?.kind === "price" ? (
                <PriceRuleDialog onClose={() => setDialog(null)} onSaved={invalidatePrices} rule={dialog.rule} />
            ) : null}
            {dialog?.kind === "plan" ? (
                <QuotaPlanDialog onClose={() => setDialog(null)} onSaved={invalidatePlans} plan={dialog.plan} />
            ) : null}
            {dialog?.kind === "assignment" ? (
                <QuotaAssignmentDialog
                    assignment={dialog.assignment}
                    onClose={() => setDialog(null)}
                    onSaved={invalidateAssignments}
                    plans={plans}
                />
            ) : null}
        </DansPage>
    );
}
