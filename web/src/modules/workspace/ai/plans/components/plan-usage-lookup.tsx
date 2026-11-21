import { useState } from "react";

import { useMutation, useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";

import { DetailDialog } from "@/components/detail-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatBeijingDateTime } from "@/lib/date";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { UsageErrorState } from "../../usage/components/usage-state";
import {
  fetchPlan,
  fetchPlanUsageByUser,
  resolvePlanApiError,
  resolvePlanSubjects,
} from "../lib/api";
import {
  formatUnits,
  planRuntimeSummary,
  planVoucherSummary,
  planWeeklyResetLabel,
} from "../lib/model";
import { SubjectPicker } from "./subject-picker";

export function PlanUsageLookupSection() {
  const [selectedUsers, setSelectedUsers] = useState<string[]>([]);
  const [planName, setPlanName] = useState<string | null>(null);

  const usageMutation = useMutation({
    mutationFn: async () => {
      if (selectedUsers.length === 0) {
        throw new Error("请选择要查询的用户。");
      }
      const resolved = await resolvePlanSubjects("User", [selectedUsers[0]]);
      const uid = resolved[0]?.name;
      if (!uid) {
        throw new Error("无法解析所选用户的 uid。");
      }
      return fetchPlanUsageByUser(uid);
    },
  });

  const usage = usageMutation.data;

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="按用户查询套餐用量"
        description="按用户稳定 uid 读取当前 5 小时与周窗口用量、限流与重置券余额。"
      />
      <div className="space-y-4 p-5">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-64 flex-1 space-y-1.5 text-sm">
            <span className="text-muted-foreground">用户</span>
            <SubjectPicker
              kind="User"
              onKindChange={() => undefined}
              value={selectedUsers}
              onChange={setSelectedUsers}
              multiple={false}
              lockKind
            />
          </div>
          <Button
            type="button"
            size="sm"
            disabled={usageMutation.isPending || selectedUsers.length === 0}
            onClick={() => usageMutation.mutate()}
          >
            <Search />
            查询用量
          </Button>
        </div>

        {usageMutation.isError ? (
          <UsageErrorState
            title="无法加载用户用量"
            message={resolvePlanApiError(usageMutation.error)}
            onRetry={() => usageMutation.mutate()}
          />
        ) : usageMutation.isPending ? (
          <div className="space-y-2">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-20 w-full" />
          </div>
        ) : usage ? (
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2 border p-3">
              <span className="text-sm font-medium">
                {usage.plan?.displayName || usage.plan?.name || "未命中套餐"}
              </span>
              {usage.plan?.name ? (
                <Badge variant="outline" className="font-mono text-[10px]">
                  {usage.plan.name}
                </Badge>
              ) : null}
              <span className="text-xs text-muted-foreground">
                命中来源：{usage.plan?.matchedBy || "—"}
              </span>
              {usage.plan?.name ? (
                <Button
                  type="button"
                  variant="outline"
                  size="xs"
                  className="ms-auto"
                  onClick={() => setPlanName(usage.plan?.name ?? null)}
                >
                  查看套餐详情
                </Button>
              ) : null}
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <UsageWindowCard title="5 小时窗口" window={usage.fiveHour} />
              <UsageWindowCard title="周窗口" window={usage.weekly} />
            </div>
            <div className="grid gap-2 text-xs sm:grid-cols-2">
              <InfoRow
                label="限流"
                value={`并发 ${usage.rateLimit?.maxConcurrency ?? 0} · RPM ${
                  usage.rateLimit?.maxRpm ?? 0
                }`}
              />
              <InfoRow
                label="重置券"
                value={`5h 剩余 ${usage.vouchers?.fiveHour?.remaining ?? 0} 张 · 周剩余 ${
                  usage.vouchers?.weekly?.remaining ?? 0
                } 张`}
              />
            </div>
          </div>
        ) : (
          <ConsoleEmpty>选择用户后查询其套餐用量。</ConsoleEmpty>
        )}
      </div>

      {planName ? <PlanDetailDialog planName={planName} onClose={() => setPlanName(null)} /> : null}
    </ConsoleSection>
  );
}

function UsageWindowCard({
  title,
  window: usageWindow,
}: {
  title: string;
  window: {
    limit: number;
    used: number;
    reserved: number;
    remaining: number;
    startsAt?: string | null;
    endsAt?: string | null;
    state?: string | null;
  };
}) {
  return (
    <div className="space-y-1.5 border p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm font-medium">{title}</span>
        {usageWindow.state ? <Badge variant="outline">{usageWindow.state}</Badge> : null}
      </div>
      <div className="grid grid-cols-3 gap-2 text-xs">
        <span className="text-muted-foreground">
          上限 <span className="text-foreground">{formatUnits(usageWindow.limit)}</span>
        </span>
        <span className="text-muted-foreground">
          已用 <span className="text-foreground">{formatUnits(usageWindow.used)}</span>
        </span>
        <span className="text-muted-foreground">
          剩余 <span className="text-foreground">{formatUnits(usageWindow.remaining)}</span>
        </span>
      </div>
      <p className="text-[11px] text-muted-foreground">
        {usageWindow.startsAt ? formatBeijingDateTime(usageWindow.startsAt) : "—"} 至{" "}
        {usageWindow.endsAt ? formatBeijingDateTime(usageWindow.endsAt) : "—"}
        {usageWindow.reserved > 0 ? ` · 预留 ${usageWindow.reserved} Token` : ""}
      </p>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b pb-1.5">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right">{value}</span>
    </div>
  );
}

function PlanDetailDialog({ planName, onClose }: { planName: string; onClose: () => void }) {
  const query = useQuery({
    queryKey: ["ai-plan", planName],
    queryFn: () => fetchPlan(planName),
    retry: false,
  });
  const plan = query.data;

  return (
    <DetailDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={plan?.spec.displayName || planName}
      description="读取套餐当前定义，包含额度、重置券与运行策略。"
      size="xl"
      footer={
        <Button type="button" variant="outline" onClick={onClose}>
          关闭
        </Button>
      }
    >
      {query.isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-6 w-full" />
          ))}
        </div>
      ) : query.isError ? (
        <UsageErrorState
          title="无法加载套餐详情"
          message={resolvePlanApiError(query.error)}
          onRetry={() => void query.refetch()}
        />
      ) : plan ? (
        <div className="space-y-2 text-xs">
          <InfoRow label="资源名称" value={plan.metadata.name} />
          <InfoRow label="5 小时上限" value={formatUnits(plan.spec.quota?.fiveHour?.limit)} />
          <InfoRow label="周上限" value={formatUnits(plan.spec.quota?.weekly?.limit)} />
          <InfoRow label="周重置" value={planWeeklyResetLabel(plan.spec)} />
          <InfoRow label="结算容差" value={formatUnits(plan.spec.toleranceUnits)} />
          <InfoRow label="重置券" value={planVoucherSummary(plan.spec)} />
          <InfoRow label="运行策略" value={planRuntimeSummary(plan.spec)} />
          <InfoRow label="允许路由组" value={plan.spec.access?.routeGroups?.join("、") || "不限"} />
          <InfoRow label="允许模型" value={plan.spec.access?.models?.join("、") || "不限"} />
          {plan.spec.description ? (
            <div className="space-y-1 pt-2">
              <span className="text-muted-foreground">描述</span>
              <p className="text-sm leading-relaxed">{plan.spec.description}</p>
            </div>
          ) : null}
        </div>
      ) : null}
    </DetailDialog>
  );
}
