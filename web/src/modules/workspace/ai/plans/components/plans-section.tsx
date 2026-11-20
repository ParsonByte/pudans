import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { UsageErrorState } from "../../usage/components/usage-state";
import { type AiPlanItem, deletePlan, fetchAllPlans, resolvePlanApiError } from "../lib/api";
import {
  formatUnits,
  planRuntimeSummary,
  planVoucherSummary,
  planWeeklyResetLabel,
} from "../lib/model";
import { PlanDeleteDialog } from "./plan-delete-dialog";
import { PlanDialog } from "./plan-dialog";

type PlansSectionProps = {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
};

type PlanDialogState = { mode: "create" } | { mode: "edit"; plan: AiPlanItem } | null;

export function PlansSection({ canCreate, canUpdate, canDelete }: PlansSectionProps) {
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<PlanDialogState>(null);
  const [deleting, setDeleting] = useState<AiPlanItem | null>(null);
  const plansQuery = useQuery({
    queryKey: ["ai-plans", "all"],
    queryFn: fetchAllPlans,
    retry: false,
  });

  const deleteMutation = useMutation({
    mutationFn: (plan: AiPlanItem) => deletePlan(plan.metadata.name),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-plans"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-bindings"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
      toast.success("套餐已删除。");
      setDeleting(null);
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  const plans = plansQuery.data ?? [];

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="套餐定义"
        description="定义 5 小时与周额度、周重置时间、结算容差和每周期重置券。"
        action={
          canCreate ? (
            <Button type="button" size="sm" onClick={() => setDialog({ mode: "create" })}>
              <Plus />
              新建套餐
            </Button>
          ) : null
        }
      />
      {plansQuery.isError ? (
        <UsageErrorState
          title="无法加载套餐"
          message={resolvePlanApiError(plansQuery.error)}
          onRetry={() => void plansQuery.refetch()}
        />
      ) : plansQuery.isPending ? (
        <ConsoleEmpty>正在加载套餐…</ConsoleEmpty>
      ) : plans.length === 0 ? (
        <ConsoleEmpty>暂无套餐，请先创建套餐定义。</ConsoleEmpty>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="px-5">名称</TableHead>
              <TableHead className="px-5 text-end">5 小时上限</TableHead>
              <TableHead className="px-5 text-end">周上限</TableHead>
              <TableHead className="px-5">周重置</TableHead>
              <TableHead className="px-5 text-end">结算容差</TableHead>
              <TableHead className="px-5">运行策略</TableHead>
              <TableHead className="px-5">重置券</TableHead>
              <TableHead className="px-5">状态</TableHead>
              <TableHead className="px-5 text-end">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {plans.map((plan) => (
              <TableRow key={plan.metadata.name}>
                <TableCell className="px-5 py-3">
                  <p className="text-sm font-medium">
                    {plan.spec.displayName || plan.metadata.name}
                  </p>
                  <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">
                    {plan.metadata.name}
                  </p>
                </TableCell>
                <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                  {formatUnits(plan.spec.quota?.fiveHour?.limit)}
                </TableCell>
                <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                  {formatUnits(plan.spec.quota?.weekly?.limit)}
                </TableCell>
                <TableCell className="px-5 py-3 text-xs text-muted-foreground">
                  {planWeeklyResetLabel(plan.spec)}
                </TableCell>
                <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                  {formatUnits(plan.spec.toleranceUnits)}
                </TableCell>
                <TableCell className="px-5 py-3 text-xs text-muted-foreground">
                  {planRuntimeSummary(plan.spec)}
                </TableCell>
                <TableCell className="px-5 py-3 text-xs text-muted-foreground">
                  {planVoucherSummary(plan.spec)}
                </TableCell>
                <TableCell className="px-5 py-3">
                  <Badge variant={plan.spec.enabled === false ? "outline" : "secondary"}>
                    {plan.spec.enabled === false ? "已禁用" : "启用"}
                  </Badge>
                </TableCell>
                <TableCell className="px-5 py-3">
                  <div className="flex items-center justify-end gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={!canUpdate}
                      onClick={() => setDialog({ mode: "edit", plan })}
                    >
                      <Pencil />
                      编辑
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={!canDelete}
                      onClick={() => setDeleting(plan)}
                    >
                      <Trash2 />
                      删除
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {dialog ? (
        <PlanDialog
          plan={dialog.mode === "edit" ? dialog.plan : undefined}
          onClose={() => setDialog(null)}
        />
      ) : null}

      <PlanDeleteDialog
        open={deleting != null}
        title="删除套餐"
        description={
          deleting
            ? `将删除套餐「${deleting.spec.displayName || deleting.metadata.name}」；绑定到该套餐的用户将回退到默认套餐或拒绝策略。`
            : ""
        }
        pending={deleteMutation.isPending}
        onOpenChange={(open) => {
          if (!open) {
            setDeleting(null);
          }
        }}
        onConfirm={() => {
          if (deleting) {
            deleteMutation.mutate(deleting);
          }
        }}
      />
    </ConsoleSection>
  );
}
