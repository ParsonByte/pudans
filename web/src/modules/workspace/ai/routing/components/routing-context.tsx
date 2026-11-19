import { Icon } from "@iconify/react";
import { Activity, Database } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import { modelDisplayName } from "../../providers/lib/model";
import type { AiRoutingLive, AiRoutingRequest } from "../lib/api";
import {
  type AiRoutingLookups,
  accountDisplayName,
  agentIcon,
  agentLabel,
  cacheHitRate,
  formatRelativeTime,
  formatTokenCount,
  providerDisplayName,
  requestStatusLabel,
} from "../lib/model";

type RoutingContextProps = {
  live: AiRoutingLive | undefined;
  lookups: AiRoutingLookups;
  loading: boolean;
  error: boolean;
};

export function RoutingContext({ live, lookups, loading, error }: RoutingContextProps) {
  const request = live?.requests.find((item) => item.totalTokens || item.inputTokens);
  const contextWindow = request?.model
    ? lookups.models.get(request.model)?.spec.contextWindow
    : undefined;
  const used = request
    ? (request.totalTokens ?? (request.inputTokens ?? 0) + (request.outputTokens ?? 0))
    : 0;
  const percent =
    contextWindow && contextWindow > 0 ? Math.min(100, (used / contextWindow) * 100) : undefined;
  const remaining = contextWindow ? Math.max(0, contextWindow - used) : undefined;
  const cacheRate = cacheHitRate(request?.inputTokens, request?.cacheTokens);

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-sm font-semibold">上下文窗口</h2>
          {request ? (
            <>
              <Badge variant="outline" className="gap-1">
                <Icon icon={agentIcon(request.agentSource)} className="size-3" aria-hidden="true" />
                {agentLabel(request.agentSource)}
              </Badge>
              <Badge variant="outline" className="gap-1">
                <ProviderBrandIcon
                  provider={request.provider ? lookups.providers.get(request.provider) : undefined}
                  className="size-3"
                />
                {providerDisplayName(
                  lookups.providers.get(request.provider ?? ""),
                  request.provider ?? "未知供应商",
                )}{" "}
                · {modelLabel(request, lookups)}
              </Badge>
              <span className="inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400">
                <span className="size-1.5 animate-pulse rounded-full bg-emerald-500" />
                实时
              </span>
            </>
          ) : null}
        </div>
        {request ? (
          <span className="text-[11px] text-muted-foreground">
            {requestStatusLabel(request.status)} ·{" "}
            {formatRelativeTime(request.completedAt ?? request.createdAt)}
          </span>
        ) : null}
      </div>

      {loading ? (
        <div className="space-y-4 px-5 py-5">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : error ? (
        <p className="px-5 py-5 text-xs text-muted-foreground">无法加载请求上下文。</p>
      ) : !request ? (
        <ConsoleEmpty>还没有可用的请求上下文数据。</ConsoleEmpty>
      ) : (
        <div className="space-y-6 px-5 py-5">
          <div className="grid gap-6 lg:grid-cols-2">
            <div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Activity className="size-3.5" />
                已用上下文
                {percent == null ? (
                  <Badge variant="outline">模型未设置窗口</Badge>
                ) : (
                  <Badge variant={percent >= 90 ? "destructive" : "secondary"}>
                    {percent >= 90 ? "接近上限" : percent >= 70 ? "偏紧" : "健康"}
                  </Badge>
                )}
              </div>
              <div className="mt-2 flex flex-wrap items-baseline gap-x-2 gap-y-1">
                <span className="text-3xl font-semibold tracking-tight">
                  {formatTokenCount(used)}
                </span>
                <span className="text-sm text-muted-foreground">
                  / {contextWindow ? formatTokenCount(contextWindow) : "未知"} token
                </span>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                {percent == null
                  ? "该模型没有配置上下文窗口，只能展示本次请求的 Token 用量。"
                  : `已用 ${percent.toFixed(1)}% · ${
                      percent >= 90 ? "余量紧张" : percent >= 70 ? "注意余量" : "余量充足"
                    }`}
              </p>
            </div>

            <div>
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span className="flex items-center gap-2">
                  <Database className="size-3.5" />
                  提示词缓存
                </span>
                <span>{cacheRate == null ? "未上报缓存" : `${cacheRate.toFixed(1)}% 命中`}</span>
              </div>
              <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-emerald-500 transition-[width]"
                  style={{ width: `${cacheRate ?? 0}%` }}
                />
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                缓存 {formatTokenCount(request.cacheTokens)} · 输入{" "}
                {formatTokenCount(request.inputTokens)} · 输出{" "}
                {formatTokenCount(request.outputTokens)}
              </p>
            </div>
          </div>

          <div>
            <div className="flex h-14 w-full overflow-hidden rounded-xl border bg-muted/20">
              <Segment
                value={request.inputTokens ?? 0}
                total={contextWindow ?? used}
                className="bg-sky-500/80"
                title={`输入 ${formatTokenCount(request.inputTokens)} token`}
              />
              <Segment
                value={request.outputTokens ?? 0}
                total={contextWindow ?? used}
                className="bg-violet-500/80"
                title={`输出 ${formatTokenCount(request.outputTokens)} token`}
              />
              {remaining != null ? (
                <Segment
                  value={remaining}
                  total={contextWindow ?? used}
                  className="bg-muted/40"
                  title={`剩余 ${formatTokenCount(remaining)} token`}
                />
              ) : null}
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
              <Legend
                className="bg-sky-500"
                label="输入"
                value={formatTokenCount(request.inputTokens)}
              />
              <Legend
                className="bg-violet-500"
                label="输出"
                value={formatTokenCount(request.outputTokens)}
              />
              {remaining != null ? (
                <Legend
                  className="bg-muted-foreground/30"
                  label="剩余空间"
                  value={formatTokenCount(remaining)}
                />
              ) : null}
              {request.account ? (
                <span className="ms-auto">
                  账号 {accountDisplayName(lookups.accounts.get(request.account), request.account)}
                </span>
              ) : null}
            </div>
          </div>
        </div>
      )}
    </ConsoleSection>
  );
}

function Segment({
  value,
  total,
  className,
  title,
}: {
  value: number;
  total: number;
  className: string;
  title: string;
}) {
  if (value <= 0 || total <= 0) {
    return null;
  }
  return (
    <div
      className={className}
      style={{ width: `${Math.min(100, (value / total) * 100)}%` }}
      title={title}
    />
  );
}

function Legend({ className, label, value }: { className: string; label: string; value: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={`size-2 rounded-sm ${className}`} />
      {label} {value}
    </span>
  );
}

function modelLabel(request: AiRoutingRequest, lookups: AiRoutingLookups): string {
  const model = request.model ? lookups.models.get(request.model) : undefined;
  return model ? modelDisplayName(model) : request.model || "未知模型";
}
