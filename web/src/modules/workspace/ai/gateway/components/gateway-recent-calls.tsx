import { useMemo, useState } from "react";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { differenceInCalendarDays, parseISO } from "date-fns";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { beijingDateKey } from "@/lib/date";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type { AiModelItem, AiProviderItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import { type AiRoutingRequest, fetchRoutingLive } from "../../routing/lib/api";
import {
  agentLabel,
  formatDuration,
  formatTokenCount,
  formatUnits,
  providerDisplayName,
  requestStatusDotClass,
  requestStatusLabel,
} from "../../routing/lib/model";
import { UsageCursorPagination } from "../../usage/components/usage-cursor-pagination";
import { UsageDateRangeFilter } from "../../usage/components/usage-date-range";
import { UsageErrorState, UsageTableSkeleton } from "../../usage/components/usage-state";
import {
  type UsageRequestItem,
  fetchUsageRequests,
  resolveUsageApiError,
} from "../../usage/lib/api";
import { useCursorPager } from "../../usage/lib/cursor";
import {
  USAGE_STATUS_OPTIONS,
  type UsageDateRangeValue,
  usageDateRange,
} from "../../usage/lib/model";
import { RequestDetailDialog } from "../../usage/requests/components/request-detail-dialog";
import { isForbiddenError, resolveGatewayApiError } from "../lib/api";
import { ingressProtocol } from "../lib/model";

type GatewayRecentCallsProps = {
  providers: AiProviderItem[];
  models: AiModelItem[];
};

type RecentCall = {
  requestId: string;
  createdAt: string;
  agent: string;
  agentSource: string;
  provider: string | null;
  model: string | null;
  status: string;
  errorCode: string | null;
  totalTokens: number | null;
  units: number | null;
  latencyMs: number | null;
  ttftMs: number | null;
  routeGroup: string | null;
  detailAvailable: boolean;
};

const PAGE_SIZE_DEFAULT = 20;

const TABLE_COLUMNS = 8;

function rangeDays(range: UsageDateRangeValue): number {
  const span = differenceInCalendarDays(parseISO(range.to), parseISO(range.from)) + 1;
  return Math.min(Math.max(span, 1), 366);
}

function fromUsageRequest(request: UsageRequestItem): RecentCall {
  return {
    requestId: request.requestId,
    createdAt: request.createdAt,
    agent: request.agent,
    agentSource: request.agentSource,
    provider: request.provider,
    model: request.model,
    status: request.status,
    errorCode: request.errorCode,
    totalTokens: request.totalTokens,
    units: request.units,
    latencyMs: request.latencyMs,
    ttftMs: request.ttftMs,
    routeGroup: request.routeGroup,
    detailAvailable: true,
  };
}

function fromRoutingRequest(request: AiRoutingRequest): RecentCall {
  return {
    requestId: request.requestId,
    createdAt: request.createdAt,
    agent: request.agentSource,
    agentSource: request.agentSource,
    provider: request.provider ?? null,
    model: request.model ?? null,
    status: request.status,
    errorCode: request.errorCode ?? null,
    totalTokens: request.totalTokens ?? null,
    units: request.units ?? null,
    latencyMs: request.latencyMs ?? null,
    ttftMs: request.ttftMs ?? null,
    routeGroup: request.routeGroup ?? null,
    detailAvailable: false,
  };
}

export function GatewayRecentCalls({ providers, models }: GatewayRecentCallsProps) {
  const [range, setRange] = useState<UsageDateRangeValue>(() => usageDateRange("7d"));
  const [provider, setProvider] = useState("all");
  const [model, setModel] = useState("all");
  const [status, setStatus] = useState("all");
  const [agentDraft, setAgentDraft] = useState("");
  const [agent, setAgent] = useState("");
  const [pageSize, setPageSize] = useState(PAGE_SIZE_DEFAULT);
  const [fallbackPage, setFallbackPage] = useState(1);
  const [detailRequestId, setDetailRequestId] = useState<string | null>(null);
  const pager = useCursorPager();

  const usageQuery = useQuery({
    queryKey: [
      "ai-gateway",
      "recent-usage",
      range.from,
      range.to,
      provider,
      model,
      status,
      agent,
      pager.cursor,
      pageSize,
    ],
    queryFn: () =>
      fetchUsageRequests(
        {
          from: range.from,
          to: range.to,
          ...(provider === "all" ? {} : { provider }),
          ...(model === "all" ? {} : { model }),
          ...(status === "all" ? {} : { status }),
          ...(agent ? { agent } : {}),
        },
        pager.cursor,
        pageSize,
      ),
    placeholderData: keepPreviousData,
    retry: false,
  });

  const usageForbidden = isForbiddenError(usageQuery.error);
  const days = rangeDays(range);
  const fallbackQuery = useQuery({
    queryKey: ["ai-gateway", "recent-routing", days],
    queryFn: () => fetchRoutingLive(days, 200),
    enabled: usageForbidden,
    retry: false,
  });

  const modelOptions = useMemo(() => {
    if (provider === "all") {
      return models;
    }
    return models.filter((item) => item.spec.provider === provider);
  }, [models, provider]);

  const fallbackItems = useMemo(() => {
    const requests = fallbackQuery.data?.requests ?? [];
    const normalizedAgent = agent.toLowerCase();
    return requests
      .filter((request) => {
        const day = beijingDateKey(request.createdAt);
        if (!day || day < range.from || day > range.to) {
          return false;
        }
        if (provider !== "all" && request.provider !== provider) {
          return false;
        }
        if (model !== "all" && request.model !== model) {
          return false;
        }
        if (status !== "all" && request.status !== status) {
          return false;
        }
        if (normalizedAgent && !`${request.agentSource}`.toLowerCase().includes(normalizedAgent)) {
          return false;
        }
        return true;
      })
      .map(fromRoutingRequest);
  }, [agent, fallbackQuery.data, model, provider, range.from, range.to, status]);

  const fallbackPageCount = Math.max(1, Math.ceil(fallbackItems.length / pageSize));
  const safeFallbackPage = Math.min(fallbackPage, fallbackPageCount);
  const fallbackPageItems = fallbackItems.slice(
    (safeFallbackPage - 1) * pageSize,
    safeFallbackPage * pageSize,
  );
  const fallbackMode = usageForbidden;

  const resetPaging = () => {
    pager.reset();
    setFallbackPage(1);
  };

  const handleRangeChange = (value: UsageDateRangeValue) => {
    setRange(value);
    resetPaging();
  };

  const handleProviderChange = (value: string) => {
    setProvider(value);
    if (value !== "all" && model !== "all") {
      const current = models.find((item) => item.metadata.name === model);
      if (!current || current.spec.provider !== value) {
        setModel("all");
      }
    }
    resetPaging();
  };

  const handleModelChange = (value: string) => {
    setModel(value);
    resetPaging();
  };

  const handleStatusChange = (value: string) => {
    setStatus(value);
    resetPaging();
  };

  const applyAgentFilter = () => {
    setAgent(agentDraft.trim());
    resetPaging();
  };

  const resetFilters = () => {
    setRange(usageDateRange("7d"));
    setProvider("all");
    setModel("all");
    setStatus("all");
    setAgentDraft("");
    setAgent("");
    resetPaging();
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    resetPaging();
  };

  const usageItems = (usageQuery.data?.items ?? []).map(fromUsageRequest);
  const usageTotal = usageQuery.data?.totals.requests;
  const activeLoading = fallbackMode ? fallbackQuery.isFetching : usageQuery.isFetching;
  const refresh = () => {
    if (fallbackMode) {
      void fallbackQuery.refetch();
    } else {
      void usageQuery.refetch();
    }
  };
  const items = fallbackMode ? fallbackPageItems : usageItems;
  const error = fallbackMode ? fallbackQuery.error : usageQuery.isError ? usageQuery.error : null;
  const pending = fallbackMode ? fallbackQuery.isPending : usageQuery.isPending;
  const filtered = provider !== "all" || model !== "all" || status !== "all" || agent !== "";

  return (
    <ConsoleSection className="overflow-hidden">
      <ConsoleSectionHeader
        title="最近调用"
        description="展示最近的模型调用记录；详情包含路由轨迹、尝试与用量，不包含请求正文。"
        action={
          <div className="flex items-center gap-2">
            {fallbackMode ? (
              <span className="text-[11px] text-muted-foreground">
                共 {fallbackItems.length} 条
              </span>
            ) : usageTotal != null ? (
              <span className="text-[11px] text-muted-foreground">共 {usageTotal} 条</span>
            ) : null}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={refresh}
              disabled={activeLoading}
            >
              {activeLoading ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          </div>
        }
      />

      <div className="space-y-3 border-b px-5 py-4">
        <UsageDateRangeFilter value={range} onChange={handleRangeChange} idPrefix="gateway-range" />
        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1.5">
            <p className="text-xs text-muted-foreground">供应商</p>
            <Select value={provider} onValueChange={handleProviderChange}>
              <SelectTrigger className="w-40" aria-label="筛选供应商">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部供应商</SelectItem>
                {providers.map((item) => (
                  <SelectItem key={item.metadata.name} value={item.metadata.name}>
                    {providerDisplayName(item, item.metadata.name)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <p className="text-xs text-muted-foreground">模型</p>
            <Select value={model} onValueChange={handleModelChange}>
              <SelectTrigger className="w-48" aria-label="筛选模型">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部模型</SelectItem>
                {modelOptions.map((item) => (
                  <SelectItem key={item.metadata.name} value={item.metadata.name}>
                    {modelDisplayName(item)} · {item.spec.modelCode}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <p className="text-xs text-muted-foreground">状态</p>
            <Select value={status} onValueChange={handleStatusChange}>
              <SelectTrigger className="w-28" aria-label="筛选状态">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部状态</SelectItem>
                {USAGE_STATUS_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <p className="text-xs text-muted-foreground">Agent</p>
            <div className="flex items-center gap-2">
              <Input
                value={agentDraft}
                onChange={(event) => setAgentDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    applyAgentFilter();
                  }
                }}
                placeholder="Agent 名称"
                className="h-8 w-40"
                aria-label="筛选 Agent"
              />
              <Button type="button" variant="outline" size="sm" onClick={applyAgentFilter}>
                查询
              </Button>
            </div>
          </div>
          <Button type="button" variant="ghost" size="sm" onClick={resetFilters}>
            重置
          </Button>
        </div>
        <p className="text-[11px] text-muted-foreground">
          数据来自用量接口；页面不展示提示词与响应正文，详情仅包含路由轨迹、尝试与用量。
        </p>
      </div>

      {fallbackMode ? (
        <div className="border-b bg-warning/5 px-5 py-2 text-[11px] text-warning">
          当前账号没有用量明细读取权限，已回退到路由实时记录（所选日期共 {days} 个自然日、最多 200
          条，客户端筛选与分页）； 回退模式不提供请求详情、路由轨迹与尝试记录，Agent 筛选按 Agent
          来源匹配。
        </div>
      ) : null}

      {pending ? (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>时间</TableHead>
                <TableHead>请求</TableHead>
                <TableHead>Agent</TableHead>
                <TableHead>供应商 / 模型</TableHead>
                <TableHead>协议</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>用量</TableHead>
                <TableHead className="text-end">详情</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <UsageTableSkeleton columns={TABLE_COLUMNS} />
            </TableBody>
          </Table>
        </div>
      ) : error ? (
        <UsageErrorState
          title="无法加载最近调用"
          message={
            isForbiddenError(error)
              ? "没有读取调用记录的权限，请联系管理员授予用量或路由查看权限。"
              : fallbackMode
                ? resolveGatewayApiError(error)
                : resolveUsageApiError(error)
          }
          onRetry={refresh}
        />
      ) : items.length === 0 ? (
        <ConsoleEmpty>
          {filtered
            ? "当前筛选条件下没有调用记录，试试放宽筛选或调整时间范围。"
            : "所选时间范围内还没有模型调用记录。"}
        </ConsoleEmpty>
      ) : (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>时间</TableHead>
                <TableHead>请求</TableHead>
                <TableHead>Agent</TableHead>
                <TableHead>供应商 / 模型</TableHead>
                <TableHead>协议</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>用量</TableHead>
                <TableHead className="text-end">详情</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <RecentCallRow
                  key={item.requestId}
                  item={item}
                  providers={providers}
                  models={models}
                  onDetail={() => setDetailRequestId(item.requestId)}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {!pending && !error && items.length > 0 ? (
        <div className="border-t px-5 py-3">
          <UsageCursorPagination
            page={fallbackMode ? safeFallbackPage : pager.page}
            pageSize={pageSize}
            hasPrevious={fallbackMode ? safeFallbackPage > 1 : pager.hasPrevious}
            hasNext={
              fallbackMode
                ? safeFallbackPage < fallbackPageCount
                : usageQuery.data?.nextCursor != null
            }
            onPrevious={() => {
              if (fallbackMode) {
                setFallbackPage((current) => Math.max(1, current - 1));
              } else {
                pager.goPrevious();
              }
            }}
            onNext={() => {
              if (fallbackMode) {
                setFallbackPage((current) => Math.min(fallbackPageCount, current + 1));
              } else if (usageQuery.data?.nextCursor != null) {
                pager.goNext(usageQuery.data.nextCursor);
              }
            }}
            onPageSizeChange={handlePageSizeChange}
            disabled={activeLoading}
          />
        </div>
      ) : null}

      {detailRequestId ? (
        <RequestDetailDialog requestId={detailRequestId} onClose={() => setDetailRequestId(null)} />
      ) : null}
    </ConsoleSection>
  );
}

function RecentCallRow({
  item,
  providers,
  models,
  onDetail,
}: {
  item: RecentCall;
  providers: AiProviderItem[];
  models: AiModelItem[];
  onDetail: () => void;
}) {
  const provider = item.provider
    ? providers.find((entry) => entry.metadata.name === item.provider)
    : undefined;
  const model = item.model ? models.find((entry) => entry.metadata.name === item.model) : undefined;
  const protocol = ingressProtocol(item.agentSource);
  const modelLabel = model ? modelDisplayName(model) : (item.model ?? "未知模型");

  return (
    <TableRow>
      <TableCell className="text-xs whitespace-nowrap">
        {formatUserDateTime(item.createdAt)}
      </TableCell>
      <TableCell className="max-w-40">
        <p className="truncate font-mono text-[11px]" title={item.requestId}>
          {item.requestId}
        </p>
      </TableCell>
      <TableCell className="text-xs">
        <p className="truncate">{item.agent || "—"}</p>
        <p className="truncate text-[11px] text-muted-foreground">{agentLabel(item.agentSource)}</p>
      </TableCell>
      <TableCell>
        <div className="flex items-center gap-1.5">
          <ProviderBrandIcon provider={provider} className="size-3.5 shrink-0" />
          <div className="min-w-0">
            <p className="truncate text-xs">
              {providerDisplayName(provider, item.provider ?? "未知供应商")}
            </p>
            <p className="truncate font-mono text-[11px] text-muted-foreground" title={modelLabel}>
              {model?.spec.modelCode ?? item.model ?? "未知模型"}
            </p>
          </div>
        </div>
      </TableCell>
      <TableCell>
        <Badge variant="outline">{protocol}</Badge>
      </TableCell>
      <TableCell>
        <span className="inline-flex items-center gap-1.5 text-xs whitespace-nowrap">
          <span className={`size-1.5 ${requestStatusDotClass(item.status)}`} />
          {requestStatusLabel(item.status)}
        </span>
        {item.errorCode ? (
          <p
            className="mt-0.5 max-w-40 truncate font-mono text-[10px] text-destructive"
            title={item.errorCode}
          >
            {item.errorCode}
          </p>
        ) : null}
      </TableCell>
      <TableCell className="text-xs whitespace-nowrap">
        <p>{item.totalTokens == null ? "—" : `${formatTokenCount(item.totalTokens)} Token`}</p>
        <p className="text-[11px] text-muted-foreground">
          {formatUnits(item.units ?? undefined) ?? "—"}
          {item.latencyMs != null ? ` · ${formatDuration(item.latencyMs)}` : ""}
        </p>
      </TableCell>
      <TableCell className="text-end">
        {item.detailAvailable ? (
          <Button type="button" variant="ghost" size="sm" onClick={onDetail}>
            查看
          </Button>
        ) : (
          <span className="text-[11px] text-muted-foreground" title="回退模式不提供请求详情">
            —
          </span>
        )}
      </TableCell>
    </TableRow>
  );
}
