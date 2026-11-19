import { useState } from "react";

import { ChevronDown, ChevronUp } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type { AiModelItem, AiProviderItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import type { AiRoutingLive, AiRoutingRequest } from "../../routing/lib/api";
import {
  accountDisplayName,
  agentLabel,
  formatClock,
  formatDuration,
  formatPoints,
  formatRelativeTime,
  formatTokenCount,
  providerDisplayName,
  requestStatusDotClass,
  requestStatusLabel,
  routeGroupDisplayName,
} from "../../routing/lib/model";
import { ingressProtocol } from "../lib/model";

type GatewayRecentCallsProps = {
  live: AiRoutingLive | undefined;
  providers: AiProviderItem[];
  models: AiModelItem[];
  loading: boolean;
  error: boolean;
  limit: number;
};

export function GatewayRecentCalls({
  live,
  providers,
  models,
  loading,
  error,
  limit,
}: GatewayRecentCallsProps) {
  const [expanded, setExpanded] = useState<string | null>(null);
  const requests = live?.requests ?? [];

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">最近调用</h2>
          <p className="text-xs text-muted-foreground">最近 7 天经过网关的请求。</p>
        </div>
        <span className="text-[11px] text-muted-foreground">显示最近 {limit} 个</span>
      </div>

      {loading ? (
        <div className="space-y-2 px-5 py-4">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-12 w-full" />
          ))}
        </div>
      ) : error ? (
        <p className="px-5 py-5 text-xs text-muted-foreground">无法加载网关调用记录。</p>
      ) : requests.length === 0 ? (
        <ConsoleEmpty>
          还没有经过网关的调用。使用上方接入地址发出第一个请求后，这里会显示真实记录。
        </ConsoleEmpty>
      ) : (
        <div className="divide-y">
          {requests.map((request) => (
            <RequestRow
              key={request.requestId}
              request={request}
              providers={providers}
              models={models}
              expanded={expanded === request.requestId}
              onToggle={() =>
                setExpanded((current) => (current === request.requestId ? null : request.requestId))
              }
            />
          ))}
        </div>
      )}
    </ConsoleSection>
  );
}

function RequestRow({
  request,
  providers,
  models,
  expanded,
  onToggle,
}: {
  request: AiRoutingRequest;
  providers: AiProviderItem[];
  models: AiModelItem[];
  expanded: boolean;
  onToggle: () => void;
}) {
  const provider = request.provider
    ? providers.find((item) => item.metadata.name === request.provider)
    : undefined;
  const model = request.model
    ? models.find((item) => item.metadata.name === request.model)
    : undefined;
  const protocol = ingressProtocol(request.agentSource);

  return (
    <div className={request.status === "RUNNING" ? "bg-amber-500/5" : ""}>
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full flex-wrap items-center gap-2 px-5 py-3 text-start text-sm transition-colors hover:bg-muted/20"
      >
        {expanded ? (
          <ChevronUp className="size-3.5 shrink-0 text-muted-foreground" />
        ) : (
          <ChevronDown className="size-3.5 shrink-0 text-muted-foreground" />
        )}
        <span className="font-mono text-[11px] text-muted-foreground">
          {formatClock(request.createdAt)}
        </span>
        <span className="min-w-0 truncate">{agentLabel(request.agentSource)}</span>
        <span className="inline-flex min-w-0 items-center gap-1.5">
          <ProviderBrandIcon provider={provider} className="size-3.5" />
          <span className="truncate font-mono text-xs">
            {request.provider ?? "未知"}/{model?.spec.modelCode ?? request.model ?? "未知模型"}
          </span>
        </span>
        <Badge variant="outline">{protocol}</Badge>
        <span className="ms-auto flex items-center gap-3 text-[11px] text-muted-foreground">
          <span className="inline-flex items-center gap-1.5">
            <span className={`size-1.5 rounded-full ${requestStatusDotClass(request.status)}`} />
            {requestStatusLabel(request.status)}
          </span>
          {request.latencyMs != null ? <span>{formatDuration(request.latencyMs)}</span> : null}
          {request.ttftMs != null ? <span>首字 {formatDuration(request.ttftMs)}</span> : null}
        </span>
      </button>

      {expanded ? (
        <div className="grid gap-x-6 gap-y-2 border-t bg-muted/10 px-5 py-3 ps-11 text-[11px] text-muted-foreground sm:grid-cols-2 lg:grid-cols-3">
          <Detail label="请求 ID" value={request.requestId} mono />
          <Detail label="用户" value={request.userId} mono />
          <Detail
            label="路由组"
            value={
              request.routeGroup ? routeGroupDisplayName(undefined, request.routeGroup) : "未指定"
            }
          />
          <Detail
            label="供应商"
            value={providerDisplayName(provider, request.provider ?? "未知")}
          />
          <Detail
            label="模型"
            value={model ? modelDisplayName(model) : (request.model ?? "未知")}
          />
          <Detail label="账号" value={accountDisplayName(undefined, request.account ?? "未知")} />
          <Detail label="输入 Token" value={formatTokenCount(request.inputTokens)} />
          <Detail label="缓存 Token" value={formatTokenCount(request.cacheTokens)} />
          <Detail label="输出 Token" value={formatTokenCount(request.outputTokens)} />
          <Detail label="总 Token" value={formatTokenCount(request.totalTokens)} />
          <Detail label="点数" value={formatPoints(request.points) ?? "—"} />
          <Detail
            label="完成时间"
            value={
              request.completedAt
                ? `${formatClock(request.completedAt)} · ${formatRelativeTime(request.completedAt)}`
                : "进行中"
            }
          />
          {request.errorCode ? <Detail label="错误" value={request.errorCode} destructive /> : null}
        </div>
      ) : null}
    </div>
  );
}

function Detail({
  label,
  value,
  mono,
  destructive,
}: {
  label: string;
  value: string;
  mono?: boolean;
  destructive?: boolean;
}) {
  return (
    <div className="flex min-w-0 gap-2">
      <span className="shrink-0">{label}</span>
      <span
        className={`min-w-0 truncate ${mono ? "font-mono" : ""} ${
          destructive ? "text-destructive" : "text-foreground/80"
        }`}
        title={value}
      >
        {value}
      </span>
    </div>
  );
}
