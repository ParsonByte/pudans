import { useRef, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Ticket } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { formatBeijingDateTime } from "@/lib/date";
import { cn } from "@/lib/utils";
import { formatUnits } from "@/modules/workspace/ai/plans/lib/model";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import {
  type PlanUsage,
  type PlanUsageWindow,
  type PlanVoucherBalance,
  type PlanVoucherKind,
  fetchPlanUsage,
  redeemPlanVoucher,
  resolvePlanUsageApiError,
  resolvePlanVoucherApiError,
} from "@/modules/workspace/ai/usage/lib/api";
import {
  PLAN_VOUCHER_KIND_OPTIONS,
  planVoucherKindLabel,
  voucherResetPreview,
} from "@/modules/workspace/ai/usage/lib/model";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

export function UsagePlanCard() {
  const allowedOperations = useAllowedOperations();
  const canUseVoucher = allowedOperations.has("use-voucher");
  const [voucherOpen, setVoucherOpen] = useState(false);
  const planQuery = useQuery({
    queryKey: ["ai-plan-usage"],
    queryFn: fetchPlanUsage,
    retry: false,
  });

  if (planQuery.isError) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>套餐用量</CardTitle>
          <CardDescription>当前账号的套餐窗口用量。</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex min-h-32 flex-col items-center justify-center gap-3 text-center">
            <p className="text-sm font-medium">无法加载套餐用量</p>
            <p className="text-xs text-muted-foreground">
              {resolvePlanUsageApiError(planQuery.error)}
            </p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void planQuery.refetch()}
            >
              重试
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (planQuery.isPending || !planQuery.data) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>套餐用量</CardTitle>
          <CardDescription>当前账号的套餐窗口用量。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </CardContent>
      </Card>
    );
  }

  const usage = planQuery.data;
  const voucherRemaining =
    (usage.vouchers?.fiveHour?.remaining ?? 0) + (usage.vouchers?.weekly?.remaining ?? 0);

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>套餐用量</CardTitle>
          <CardDescription>
            {usage.plan?.displayName || usage.plan?.name
              ? `${usage.plan.displayName || usage.plan.name} · 命中 ${usage.plan.matchedBy || "—"}`
              : "当前账号未命中套餐。"}
          </CardDescription>
          <CardAction className="flex items-center gap-2">
            {usage.plan?.name ? (
              <Badge variant="outline" className="font-mono text-[10px]">
                {usage.plan.name}
              </Badge>
            ) : null}
            {canUseVoucher ? (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-7 px-2"
                disabled={voucherRemaining <= 0}
                onClick={() => setVoucherOpen(true)}
              >
                <Ticket />
                使用重置券
              </Button>
            ) : null}
          </CardAction>
        </CardHeader>
        <CardContent className="space-y-4">
          <UsageWindowBlock
            label="5 小时窗口"
            window={usage.fiveHour}
            vouchers={usage.vouchers?.fiveHour}
          />
          <UsageWindowBlock
            label="周窗口"
            window={usage.weekly}
            vouchers={usage.vouchers?.weekly}
          />
          {usage.rateLimit ? (
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
              <span>套餐限流</span>
              <span className="tabular-nums">
                并发 {usage.rateLimit.maxConcurrency ?? "未设置"} · RPM{" "}
                {usage.rateLimit.maxRpm ?? "未设置"}
              </span>
            </div>
          ) : null}
        </CardContent>
      </Card>
      {voucherOpen ? (
        <UsageVoucherUseDialog usage={usage} onClose={() => setVoucherOpen(false)} />
      ) : null}
    </>
  );
}

type UsageWindowBlockProps = {
  label: string;
  window: PlanUsageWindow;
  vouchers?: PlanVoucherBalance | null;
};

function UsageWindowBlock({ label, window, vouchers }: UsageWindowBlockProps) {
  const used = window.used ?? 0;
  const reserved = window.reserved ?? 0;
  const limit = window.limit ?? 0;
  const remaining = window.remaining ?? 0;
  const percent = limit > 0 ? Math.min(Math.max(((used + reserved) / limit) * 100, 0), 100) : 0;

  return (
    <div className="space-y-2 border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-medium">{label}</span>
        <span className="text-[11px] text-muted-foreground">
          重置 {formatBeijingDateTime(window.endsAt)}
        </span>
      </div>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="text-2xl font-semibold tracking-tight tabular-nums">
          {formatUnits(remaining)}
        </span>
        <span className="text-xs text-muted-foreground tabular-nums">
          剩余 / {formatUnits(limit)}
        </span>
      </div>
      <Progress value={percent} />
      <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-muted-foreground">
        <span className="tabular-nums">
          已用 {formatNumber(used, 0)} · 预留 {formatNumber(reserved, 0)}
        </span>
        {vouchers ? (
          <span className="tabular-nums">
            重置券 {formatNumber(vouchers.remaining ?? 0, 0)} /{" "}
            {formatNumber(vouchers.total ?? 0, 0)}
          </span>
        ) : null}
      </div>
      {window.startsAt ? (
        <p className="text-[11px] text-muted-foreground">
          窗口开始 {formatBeijingDateTime(window.startsAt)}（北京时间）
        </p>
      ) : null}
      {remaining <= 0 ? <Badge variant="destructive">额度已用尽</Badge> : null}
    </div>
  );
}

type UsageVoucherUseDialogProps = {
  usage: PlanUsage;
  onClose: () => void;
};

function UsageVoucherUseDialog({ usage, onClose }: UsageVoucherUseDialogProps) {
  const queryClient = useQueryClient();
  const initialKind = firstUsableKind(usage);
  const [selected, setSelected] = useState<PlanVoucherKind>(initialKind ?? "FIVE_HOUR_RESET");
  const requestIds = useRef<Partial<Record<PlanVoucherKind, string>>>({});
  const mutation = useMutation({
    mutationFn: (kind: PlanVoucherKind) => {
      const requestId = requestIds.current[kind] ?? crypto.randomUUID();
      requestIds.current[kind] = requestId;
      return redeemPlanVoucher({ kind, requestId });
    },
    onSuccess: (result, kind) => {
      const label = planVoucherKindLabel(kind);
      if (result.duplicate) {
        toast.success(`${label}此前已使用，目标窗口已重置。`);
      } else {
        const usedAt = result.usedAt ? new Date(result.usedAt) : new Date();
        const resetAt = voucherResetPreview(
          kind,
          Number.isNaN(usedAt.getTime()) ? new Date() : usedAt,
        );
        toast.success(
          `已使用${label}，预计新重置时间 ${formatBeijingDateTime(resetAt)}（北京时间）。`,
        );
      }
      void queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
      onClose();
    },
  });

  const selectedUsable = isUsable(usage, selected);

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>使用重置券</DialogTitle>
          <DialogDescription>选择重置目标窗口；新窗口时间以服务端计算结果为准。</DialogDescription>
        </DialogHeader>
        <RadioGroup
          value={selected}
          onValueChange={(value) => setSelected(value as PlanVoucherKind)}
          className="gap-3"
        >
          {PLAN_VOUCHER_KIND_OPTIONS.map((option) => {
            const balance =
              option.value === "FIVE_HOUR_RESET"
                ? usage.vouchers?.fiveHour
                : usage.vouchers?.weekly;
            const used =
              option.value === "FIVE_HOUR_RESET"
                ? (usage.fiveHour.used ?? 0)
                : (usage.weekly.used ?? 0);
            const usable = isUsable(usage, option.value);
            return (
              <label
                key={option.value}
                className={cn(
                  "flex items-start gap-3 border p-3",
                  usable ? "cursor-pointer" : "cursor-not-allowed opacity-60",
                )}
              >
                <RadioGroupItem
                  value={option.value}
                  disabled={!usable || mutation.isPending}
                  className="mt-0.5"
                />
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium">{option.label}</span>
                    <Badge variant={usable ? "secondary" : "outline"}>
                      剩余 {formatNumber(balance?.remaining ?? 0, 0)} /{" "}
                      {formatNumber(balance?.total ?? 0, 0)}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground">{option.hint}</p>
                  {!usable ? (
                    <p className="text-xs text-muted-foreground">
                      {used <= 0 ? "目标窗口没有用量，无需重置。" : "当前周期没有可用的重置券。"}
                    </p>
                  ) : null}
                </div>
              </label>
            );
          })}
        </RadioGroup>
        {selectedUsable ? (
          <p className="text-xs text-muted-foreground">
            预计新重置时间：{formatBeijingDateTime(voucherResetPreview(selected))}（北京时间）。
          </p>
        ) : null}
        {mutation.isError ? (
          <p role="alert" className="text-xs text-destructive">
            {resolvePlanVoucherApiError(mutation.error)}
          </p>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" disabled={mutation.isPending} onClick={onClose}>
            取消
          </Button>
          <Button
            type="button"
            disabled={!selectedUsable || mutation.isPending}
            onClick={() => mutation.mutate(selected)}
          >
            {mutation.isPending ? "正在重置…" : "确认使用"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function isUsable(usage: PlanUsage, kind: PlanVoucherKind): boolean {
  const balance = kind === "FIVE_HOUR_RESET" ? usage.vouchers?.fiveHour : usage.vouchers?.weekly;
  const used = kind === "FIVE_HOUR_RESET" ? usage.fiveHour.used : usage.weekly.used;
  return (balance?.remaining ?? 0) > 0 && (used ?? 0) > 0;
}

function firstUsableKind(usage: PlanUsage): PlanVoucherKind | null {
  for (const option of PLAN_VOUCHER_KIND_OPTIONS) {
    if (isUsable(usage, option.value)) {
      return option.value;
    }
  }
  return null;
}
