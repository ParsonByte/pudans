import { useState } from "react";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw, Ticket } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatBeijingDateTime } from "@/lib/date";
import {
  PLAN_VOUCHER_KIND_OPTIONS,
  planVoucherKindLabel,
} from "@/modules/workspace/ai/usage/lib/model";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { UsageErrorState } from "../../usage/components/usage-state";
import type { PlanVoucherKind } from "../../usage/lib/api";
import {
  type PlanSubjectKind,
  type PlanVoucherHistoryItem,
  fetchPlanVoucherBalance,
  fetchPlanVoucherHistory,
  grantPlanVouchers,
  resolvePlanApiError,
  resolvePlanSubjects,
} from "../lib/api";
import { SubjectPicker } from "./subject-picker";

const COUNT_PATTERN = /^[1-9]\d*$/;

const VOUCHER_HISTORY_PAGE_SIZE = 20;

type VoucherGrantSectionProps = {
  canGrant: boolean;
};

export function VoucherGrantSection({ canGrant }: VoucherGrantSectionProps) {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<PlanSubjectKind>("UserGroup");
  const [subjects, setSubjects] = useState<string[]>([]);
  const [voucherKind, setVoucherKind] = useState<PlanVoucherKind>("FIVE_HOUR_RESET");
  const [count, setCount] = useState("1");

  const balanceQuery = useQuery({
    queryKey: ["ai-plan-vouchers", "balance"],
    queryFn: fetchPlanVoucherBalance,
    retry: false,
  });
  const historyQuery = useInfiniteQuery({
    queryKey: ["ai-plan-vouchers", "history"],
    queryFn: ({ pageParam }) =>
      fetchPlanVoucherHistory({
        limit: VOUCHER_HISTORY_PAGE_SIZE,
        beforeId: typeof pageParam === "number" ? pageParam : undefined,
      }),
    initialPageParam: null as number | null,
    getNextPageParam: (lastPage) => {
      if (lastPage.length < VOUCHER_HISTORY_PAGE_SIZE) {
        return null;
      }
      const oldest = lastPage.reduce(
        (min, item) => (item.id < min ? item.id : min),
        Number.MAX_SAFE_INTEGER,
      );
      return Number.isFinite(oldest) ? oldest : null;
    },
    retry: false,
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (subjects.length === 0) {
        throw new Error("请选择要发放的用户组或用户。");
      }
      if (!COUNT_PATTERN.test(count.trim())) {
        throw new Error("发放数量必须为正整数。");
      }
      const resolved = await resolvePlanSubjects(kind, subjects);
      return grantPlanVouchers({
        kind: voucherKind,
        count: Number(count.trim()),
        userIds: kind === "User" ? resolved.map((subject) => subject.name) : undefined,
        groupNames: kind === "UserGroup" ? resolved.map((subject) => subject.name) : undefined,
      });
    },
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-vouchers"] });
      const grantedKind = result.kind ?? voucherKind;
      toast.success(
        `已为 ${result.userIds?.length ?? 0} 个用户发放 ${result.count ?? count} 张${planVoucherKindLabel(grantedKind)}。`,
      );
      setSubjects([]);
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  const historyItems = historyQuery.data?.pages.flatMap((page) => page) ?? [];

  return (
    <div className="flex flex-col gap-5">
      <ConsoleSection>
        <ConsoleSectionHeader
          title="重置券发放"
          description="为选定用户或用户组成员追加当前周期的 5 小时 / 周重置券；用户组成员在发放时解析。"
        />
        <div className="grid gap-4 p-5 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
          <div className="space-y-4">
            <div className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">发放主体</span>
              <SubjectPicker
                kind={kind}
                onKindChange={setKind}
                value={subjects}
                onChange={setSubjects}
                disabled={!canGrant}
              />
            </div>
            <div className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">重置券类型</span>
              <ToggleGroup
                type="single"
                variant="outline"
                size="sm"
                value={voucherKind}
                onValueChange={(value) => {
                  if (value === "FIVE_HOUR_RESET" || value === "WEEKLY_RESET") {
                    setVoucherKind(value);
                  }
                }}
                disabled={!canGrant}
              >
                {PLAN_VOUCHER_KIND_OPTIONS.map((option) => (
                  <ToggleGroupItem key={option.value} value={option.value}>
                    {option.label}
                  </ToggleGroupItem>
                ))}
              </ToggleGroup>
              <p className="text-xs text-muted-foreground">
                {PLAN_VOUCHER_KIND_OPTIONS.find((option) => option.value === voucherKind)?.hint}
              </p>
            </div>
          </div>
          <div className="space-y-4">
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">每主体发放数量</span>
              <Input
                value={count}
                onChange={(event) => setCount(event.target.value)}
                inputMode="numeric"
                disabled={!canGrant}
              />
            </label>
            <p className="text-xs text-muted-foreground">
              用户组会在发放时展开为组内成员，每人各获得指定张数。
            </p>
            <div className="flex justify-end">
              <Button
                type="button"
                size="sm"
                disabled={!canGrant || mutation.isPending}
                onClick={() => mutation.mutate()}
              >
                <Ticket />
                发放重置券
              </Button>
            </div>
          </div>
        </div>
      </ConsoleSection>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <ConsoleSection>
          <ConsoleSectionHeader
            title="我的重置券"
            description="当前账号的 5 小时与周重置券余额；管理员发放的记录会累计到对应用户。"
            action={
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={balanceQuery.isFetching}
                onClick={() => balanceQuery.refetch()}
              >
                <RefreshCw className={balanceQuery.isFetching ? "animate-spin" : ""} />
                刷新
              </Button>
            }
          />
          {balanceQuery.isError ? (
            <UsageErrorState
              title="无法加载重置券余额"
              message={resolvePlanApiError(balanceQuery.error)}
              onRetry={() => void balanceQuery.refetch()}
            />
          ) : balanceQuery.isLoading ? (
            <div className="space-y-2 p-4">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
            </div>
          ) : balanceQuery.data ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-5">类型</TableHead>
                  <TableHead className="px-5 text-end">累计发放</TableHead>
                  <TableHead className="px-5 text-end">已使用</TableHead>
                  <TableHead className="px-5 text-end">剩余</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(
                  [
                    ["5 小时重置券", balanceQuery.data.fiveHour],
                    ["周重置券", balanceQuery.data.weekly],
                  ] as const
                ).map(([label, balance]) => (
                  <TableRow key={label}>
                    <TableCell className="px-5 py-3 text-sm">{label}</TableCell>
                    <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                      {balance.total}
                    </TableCell>
                    <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                      {balance.used}
                    </TableCell>
                    <TableCell className="px-5 py-3 text-end">
                      <Badge variant={balance.remaining > 0 ? "secondary" : "outline"}>
                        {balance.remaining}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <ConsoleEmpty>暂无重置券数据。</ConsoleEmpty>
          )}
        </ConsoleSection>

        <ConsoleSection>
          <ConsoleSectionHeader
            title="重置券使用记录"
            description="当前账号最近使用重置券的窗口记录，按使用时间倒序。"
            action={
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={historyQuery.isFetching}
                onClick={() => void historyQuery.refetch()}
              >
                <RefreshCw className={historyQuery.isFetching ? "animate-spin" : ""} />
                刷新
              </Button>
            }
          />
          {historyQuery.isError ? (
            <UsageErrorState
              title="无法加载重置券记录"
              message={resolvePlanApiError(historyQuery.error)}
              onRetry={() => void historyQuery.refetch()}
            />
          ) : historyQuery.isLoading ? (
            <div className="space-y-2 p-4">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="h-9 w-full" />
              ))}
            </div>
          ) : historyItems.length === 0 ? (
            <ConsoleEmpty>暂无重置券使用记录。</ConsoleEmpty>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="px-5">使用时间</TableHead>
                    <TableHead className="px-5">类型</TableHead>
                    <TableHead className="px-5">目标窗口</TableHead>
                    <TableHead className="px-5">重置后窗口</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {historyItems.map((item: PlanVoucherHistoryItem) => (
                    <TableRow key={item.id}>
                      <TableCell className="px-5 py-3 text-xs text-muted-foreground">
                        {formatBeijingDateTime(item.usedAt)}
                      </TableCell>
                      <TableCell className="px-5 py-3">
                        <Badge variant="outline">{planVoucherKindLabel(item.kind)}</Badge>
                      </TableCell>
                      <TableCell className="px-5 py-3 font-mono text-[11px]">
                        {item.targetWindowKey}
                      </TableCell>
                      <TableCell className="px-5 py-3 font-mono text-[11px] text-muted-foreground">
                        {item.windowKeyAfter}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {historyQuery.hasNextPage ? (
                <div className="flex justify-center border-t p-3">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={historyQuery.isFetchingNextPage}
                    onClick={() => historyQuery.fetchNextPage()}
                  >
                    {historyQuery.isFetchingNextPage ? "加载中…" : "加载更早记录"}
                  </Button>
                </div>
              ) : null}
            </>
          )}
        </ConsoleSection>
      </div>
    </div>
  );
}
