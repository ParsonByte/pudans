import type { ReactNode } from "react";
import { useMemo, useState } from "react";

import { useInfiniteQuery } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { resolveApiErrorWithDetail } from "@/lib/api-error";
import { formatBeijingDateTime } from "@/lib/date";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import type { AiModelItem, AiProviderItem, AiRouteGroupItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import { type AiRoutingHistoryItem, fetchRoutingHistory } from "../lib/api";
import {
  type AiRoutingLookups,
  HISTORY_STATUS_OPTIONS,
  accountDisplayName,
  agentLabel,
  formatDuration,
  formatRelativeTime,
  providerDisplayName,
  requestStatusDotClass,
  requestStatusLabel,
  routeGroupDisplayName,
  shortIdentifier,
} from "../lib/model";
import { RoutingTraceDetail } from "./routing-trace-detail";

type HistoryFilters = {
  from: string;
  to: string;
  status: string;
  provider: string;
  model: string;
  agent: string;
  group: string;
};

const EMPTY_FILTERS: HistoryFilters = {
  from: "",
  to: "",
  status: "",
  provider: "",
  model: "",
  agent: "",
  group: "",
};

type RoutingHistoryProps = {
  providers: AiProviderItem[];
  models: AiModelItem[];
  routeGroups: AiRouteGroupItem[];
  lookups: AiRoutingLookups;
};

export function RoutingHistory({ providers, models, routeGroups, lookups }: RoutingHistoryProps) {
  const [draft, setDraft] = useState<HistoryFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<HistoryFilters>(EMPTY_FILTERS);
  const [detailId, setDetailId] = useState<string | null>(null);

  const providerOptions = useMemo(
    () =>
      providers.map((provider) => ({
        value: provider.metadata.name,
        label: provider.spec.displayName || provider.metadata.name,
      })),
    [providers],
  );
  const modelOptions = useMemo(
    () =>
      models.map((model) => ({
        value: model.metadata.name,
        label: modelDisplayName(model),
        hint: model.spec.modelCode,
      })),
    [models],
  );
  const routeGroupOptions = useMemo(
    () =>
      routeGroups.map((group) => ({
        value: group.metadata.name,
        label: group.spec.displayName || group.metadata.name,
      })),
    [routeGroups],
  );

  const query = useInfiniteQuery({
    queryKey: ["ai-routing-history", filters],
    queryFn: ({ pageParam }) =>
      fetchRoutingHistory({
        ...filters,
        beforeId: pageParam,
        limit: 20,
      }),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    retry: false,
  });

  const items = useMemo(() => query.data?.pages.flatMap((page) => page.items) ?? [], [query.data]);

  const updateDraft = (patch: Partial<HistoryFilters>) =>
    setDraft((current) => ({ ...current, ...patch }));

  const applyFilters = () => setFilters(draft);

  const resetFilters = () => {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
  };

  return (
    <ConsoleSection className="overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            路由历史
            {items.length > 0 ? <Badge variant="secondary">已加载 {items.length}</Badge> : null}
          </h2>
          <p className="text-xs text-muted-foreground">
            按时间倒序分页；筛选条件对起始与结束日期均包含当天。
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => void query.refetch()}
          disabled={query.isFetching}
        >
          {query.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
          刷新
        </Button>
      </div>

      <div className="border-b px-5 py-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <FilterField label="起始日期">
            <Input
              type="date"
              value={draft.from}
              max={draft.to || undefined}
              onChange={(event) => updateDraft({ from: event.target.value })}
            />
          </FilterField>
          <FilterField label="结束日期">
            <Input
              type="date"
              value={draft.to}
              min={draft.from || undefined}
              onChange={(event) => updateDraft({ to: event.target.value })}
            />
          </FilterField>
          <FilterField label="状态">
            <Select
              value={draft.status || "ALL"}
              onValueChange={(value) => updateDraft({ status: value === "ALL" ? "" : value })}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {HISTORY_STATUS_OPTIONS.map((option) => (
                  <SelectItem key={option.value || "ALL"} value={option.value || "ALL"}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FilterField>
          <FilterField label="供应商">
            <Select
              value={draft.provider || "ALL"}
              onValueChange={(value) => updateDraft({ provider: value === "ALL" ? "" : value })}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部供应商</SelectItem>
                {providerOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FilterField>
          <FilterField label="模型">
            <Select
              value={draft.model || "ALL"}
              onValueChange={(value) => updateDraft({ model: value === "ALL" ? "" : value })}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部模型</SelectItem>
                {modelOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FilterField>
          <FilterField label="智能体">
            <Input
              value={draft.agent}
              onChange={(event) => updateDraft({ agent: event.target.value })}
              placeholder="智能体 ID"
            />
          </FilterField>
          <FilterField label="路由组">
            <Select
              value={draft.group || "ALL"}
              onValueChange={(value) => updateDraft({ group: value === "ALL" ? "" : value })}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部路由组</SelectItem>
                {routeGroupOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FilterField>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Button type="button" size="sm" onClick={applyFilters}>
            查询
          </Button>
          <Button type="button" size="sm" variant="outline" onClick={resetFilters}>
            重置
          </Button>
          <span className="text-[11px] text-muted-foreground">
            日期留空时使用后端默认的最近 7 天。
          </span>
        </div>
      </div>

      {query.isPending ? (
        <div className="space-y-3 px-5 py-4">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-11 rounded-lg" />
          ))}
        </div>
      ) : query.isError && items.length === 0 ? (
        <div className="flex flex-col items-center gap-3 px-5 py-10 text-center">
          <p className="text-sm font-medium">无法加载路由历史</p>
          <p className="max-w-md text-xs text-muted-foreground">
            {resolveApiErrorWithDetail(query.error, {
              notFound: "找不到路由历史。",
              conflict: "请求状态冲突，请稍后重试。",
            })}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void query.refetch()}>
            重试
          </Button>
        </div>
      ) : items.length === 0 ? (
        <ConsoleEmpty>
          {Object.values(filters).some(Boolean)
            ? "当前筛选条件下没有路由历史。"
            : "还没有路由历史。网关产生请求后会在这里留下完整追踪。"}
        </ConsoleEmpty>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>时间</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>智能体 / 会话</TableHead>
                <TableHead>路由组</TableHead>
                <TableHead>模型</TableHead>
                <TableHead>路由</TableHead>
                <TableHead className="text-end">尝试</TableHead>
                <TableHead className="text-end">延迟</TableHead>
                <TableHead className="text-end">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <HistoryRow
                  key={item.requestId}
                  item={item}
                  lookups={lookups}
                  onOpen={() => setDetailId(item.requestId)}
                />
              ))}
            </TableBody>
          </Table>
          <div className="flex flex-wrap items-center justify-center gap-3 border-t px-5 py-4">
            {query.hasNextPage ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={query.isFetchingNextPage}
                onClick={() => void query.fetchNextPage()}
              >
                {query.isFetchingNextPage ? <Loader2 className="animate-spin" /> : null}
                加载更多
              </Button>
            ) : (
              <span className="text-[11px] text-muted-foreground">已加载全部结果。</span>
            )}
            {query.isFetchNextPageError ? (
              <span className="text-xs text-destructive">下一页加载失败，请重试。</span>
            ) : null}
          </div>
        </>
      )}

      {detailId ? (
        <RoutingTraceDetail
          requestId={detailId}
          lookups={lookups}
          onClose={() => setDetailId(null)}
        />
      ) : null}
    </ConsoleSection>
  );
}

type FilterFieldProps = {
  label: string;
  children: ReactNode;
};

function FilterField({ label, children }: FilterFieldProps) {
  return (
    <label className="space-y-1 text-xs text-muted-foreground">
      <span>{label}</span>
      {children}
    </label>
  );
}

type HistoryRowProps = {
  item: AiRoutingHistoryItem;
  lookups: AiRoutingLookups;
  onOpen: () => void;
};

function HistoryRow({ item, lookups, onOpen }: HistoryRowProps) {
  const provider = item.provider ? lookups.providers.get(item.provider) : undefined;
  const account = item.account ? lookups.accounts.get(item.account) : undefined;
  const model = item.model ? lookups.models.get(item.model) : undefined;
  const group = item.routeGroup ? lookups.routeGroups.get(item.routeGroup) : undefined;
  const conversationLabel =
    item.sessionTitle ||
    (item.conversationId ? `会话 #${item.conversationId}` : undefined) ||
    (item.sessionKey ? shortIdentifier(item.sessionKey, 24) : "—");

  return (
    <TableRow>
      <TableCell className="text-xs text-muted-foreground">
        <span title={formatBeijingDateTime(item.createdAt)}>
          {formatRelativeTime(item.createdAt)}
        </span>
      </TableCell>
      <TableCell>
        <span className="inline-flex items-center gap-1.5 text-xs">
          <span className={`size-1.5 rounded-full ${requestStatusDotClass(item.status)}`} />
          {requestStatusLabel(item.status)}
        </span>
      </TableCell>
      <TableCell className="max-w-52">
        <span className="block truncate text-xs">{agentLabel(item.agent)}</span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {conversationLabel}
        </span>
      </TableCell>
      <TableCell className="max-w-40">
        <span className="block truncate text-xs">
          {item.routeGroup ? routeGroupDisplayName(group, item.routeGroup) : "—"}
        </span>
      </TableCell>
      <TableCell className="max-w-48">
        <span className="block truncate text-xs">
          {item.model ? (model ? modelDisplayName(model) : item.model) : "—"}
        </span>
        {item.model ? (
          <span className="block truncate font-mono text-[11px] text-muted-foreground">
            {item.model}
          </span>
        ) : null}
      </TableCell>
      <TableCell className="max-w-48">
        <span className="block truncate text-xs">
          {item.provider ? providerDisplayName(provider, item.provider) : "—"}
        </span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {item.account ? accountDisplayName(account, item.account) : "未选择账号"}
        </span>
      </TableCell>
      <TableCell className="text-end text-xs text-muted-foreground">
        {item.tries > 0 ? `${item.tries} 次` : "—"}
      </TableCell>
      <TableCell className="text-end text-xs text-muted-foreground">
        {formatDuration(item.latencyMs)}
      </TableCell>
      <TableCell className="text-end">
        <Button type="button" variant="ghost" size="sm" onClick={onOpen}>
          详情
        </Button>
      </TableCell>
    </TableRow>
  );
}
