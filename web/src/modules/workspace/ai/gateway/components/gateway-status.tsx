import { Link } from "@tanstack/react-router";
import { Loader2, Network, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { AiRouteGroupItem } from "../../providers/lib/api";
import {
  type GatewayRecentError,
  type GatewayStatus,
  isNotFoundError,
  resolveGatewayApiError,
} from "../lib/api";
import {
  GATEWAY_PROTOCOLS,
  gatewayRootUrl,
  gatewayStatusCount,
  gatewayStatusProtocolLabel,
  gatewayStatusProtocols,
  gatewayStatusRecentErrors,
} from "../lib/model";
import { CopyField } from "./copy-field";

type GatewayStatusFallback = {
  usableModelCount: number;
  routeGroupCount: number;
  defaultRouteGroup: string | undefined;
  rateLimitEnabled: boolean | undefined;
  recentErrors: GatewayRecentError[];
  recentErrorsLoading: boolean;
  recentErrorsUnavailable: boolean;
};

type GatewayStatusProps = {
  status: GatewayStatus | undefined;
  statusLoading: boolean;
  statusError: unknown;
  routeGroups: AiRouteGroupItem[];
  fallback: GatewayStatusFallback;
  loading: boolean;
  refreshing: boolean;
  providersPath: string | undefined;
  routeGroupsPath: string | undefined;
  onRefresh: () => void;
};

export function GatewayStatus({
  status,
  statusLoading,
  statusError,
  routeGroups,
  fallback,
  loading,
  refreshing,
  providersPath,
  routeGroupsPath,
  onRefresh,
}: GatewayStatusProps) {
  const statusProtocols = gatewayStatusProtocols(status?.protocols);
  const protocolLabels = [
    ...new Set(
      statusProtocols.length > 0
        ? statusProtocols.map(gatewayStatusProtocolLabel)
        : status
          ? []
          : GATEWAY_PROTOCOLS.map((protocol) => protocol.label),
    ),
  ];
  const modelCount =
    (status ? gatewayStatusCount(status.models) : undefined) ?? fallback.usableModelCount;
  const routeGroupCount =
    (status ? gatewayStatusCount(status.routeGroups) : undefined) ?? fallback.routeGroupCount;
  const defaultRouteGroup = status?.defaultRouteGroup ?? fallback.defaultRouteGroup;
  const defaultGroup = routeGroups.find((group) => group.metadata.name === defaultRouteGroup);
  const rateLimitEnabled = status?.rateLimitEnabled ?? fallback.rateLimitEnabled;
  const recentErrors = status
    ? gatewayStatusRecentErrors(status.recentErrors)
    : fallback.recentErrors;
  const enabled =
    status?.enabled ?? (fallback.usableModelCount > 0 && fallback.routeGroupCount > 0);
  const entryRoot = gatewayRootUrl(status?.entryUrl);
  const sourceLabel = status ? "网关状态接口" : "配置推导";
  const statusNote = status
    ? "以下状态由 GET /aigateway/-/status 返回，未做额外健康探测。"
    : statusLoading
      ? "正在读取网关状态接口，暂按可用模型、路由组与 AI 设置推导。"
      : isNotFoundError(statusError)
        ? "网关尚未提供 GET /aigateway/-/status，以下数据由可用模型、路由组与 AI 设置推导。"
        : statusError
          ? `状态接口调用失败（${resolveGatewayApiError(statusError)}），以下数据由配置推导。`
          : "以下数据由配置推导。";

  return (
    <ConsoleSection className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-5">
        <div className="flex items-start gap-3">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Network className="size-5" />
          </span>
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-base font-semibold">网关</h2>
              <Badge variant={enabled ? "secondary" : "outline"}>
                <span
                  className={`size-1.5 rounded-full ${
                    statusLoading
                      ? "bg-muted-foreground"
                      : enabled
                        ? "bg-emerald-500"
                        : "bg-muted-foreground"
                  }`}
                />
                {statusLoading ? "加载中" : enabled ? "运行中" : "未启用"}
              </Badge>
              <Badge variant="outline">状态来源：{sourceLabel}</Badge>
              {status?.version ? <Badge variant="outline">版本 {status.version}</Badge> : null}
            </div>
            <p className="text-xs text-muted-foreground">
              {modelCount} 个可用模型 · {routeGroupCount} 个启用路由组 ·{" "}
              {protocolLabels.length > 0 ? `${protocolLabels.length} 种入口协议` : "未报告入口协议"}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link to="/">聊天页面</Link>
          </Button>
          <Button size="sm" onClick={onRefresh} disabled={refreshing}>
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>
      </div>

      <div
        className={`border-t px-5 py-2 text-[11px] ${
          status
            ? "bg-muted/20 text-muted-foreground"
            : "bg-amber-500/5 text-amber-700 dark:text-amber-300"
        }`}
      >
        {statusNote}
      </div>

      {loading && !status ? (
        <div className="grid gap-4 border-t px-5 py-4 lg:grid-cols-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : (
        <>
          <div className="grid gap-4 border-t px-5 py-4 lg:grid-cols-2">
            <CopyField
              label="网关入口地址"
              value={entryRoot}
              hint="接入示例中的 Base URL 以此地址为根路径。"
            />
            <div className="space-y-2">
              <p className="text-xs text-muted-foreground">入口协议</p>
              <div className="flex flex-wrap gap-1.5">
                {protocolLabels.length > 0 ? (
                  protocolLabels.map((label) => (
                    <Badge key={label} variant="outline">
                      {label}
                    </Badge>
                  ))
                ) : (
                  <span className="text-xs text-muted-foreground">状态接口未报告协议</span>
                )}
              </div>
              <p className="text-[11px] text-muted-foreground">
                OpenAI、Anthropic、Gemini 入口共用同一个网关地址与密钥。
              </p>
            </div>
          </div>

          <div className="grid gap-4 border-t px-5 py-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatusMetric
              label="可用模型"
              value={String(modelCount)}
              hint="具备可用账号与启用状态的模型"
            />
            <StatusMetric
              label="启用路由组"
              value={String(routeGroupCount)}
              hint="模型由路由组选择与编排"
            />
            <StatusMetric
              label="默认路由组"
              value={
                defaultGroup
                  ? defaultGroup.spec.displayName || defaultGroup.metadata.name
                  : defaultRouteGroup || "未设置"
              }
              hint={defaultGroup ? defaultGroup.metadata.name : "未在 AI 设置中找到默认组"}
            />
            <StatusMetric
              label="限流"
              value={
                rateLimitEnabled === undefined ? "未读取" : rateLimitEnabled ? "已开启" : "已关闭"
              }
              hint="来自 AI 设置 gateway.rateLimitEnabled"
            />
          </div>

          <div className="space-y-2 border-t px-5 py-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-xs font-medium">最近错误</p>
              <span className="text-[11px] text-muted-foreground">
                {status ? "由状态接口报告" : "来自近 7 天路由记录的失败请求"}
              </span>
            </div>
            {recentErrors.length === 0 ? (
              <p className="text-xs text-muted-foreground">
                {status
                  ? "状态接口未报告最近错误。"
                  : fallback.recentErrorsLoading
                    ? "正在读取最近错误…"
                    : fallback.recentErrorsUnavailable
                      ? "无法读取最近错误（路由记录接口不可用或没有权限）。"
                      : "近 7 天没有失败的路由记录。"}
              </p>
            ) : (
              <ul className="space-y-1.5">
                {recentErrors.map((error, index) => (
                  <li
                    key={`${error.code}-${error.at ?? index}`}
                    className="flex flex-wrap items-center gap-2 text-xs"
                  >
                    <span className="size-1.5 rounded-full bg-destructive" />
                    <span className="font-mono text-[11px] text-destructive">{error.code}</span>
                    {error.at ? (
                      <span className="text-[11px] text-muted-foreground">
                        {formatUserDateTime(error.at)}
                      </span>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}

      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t px-5 py-3 text-[11px] text-muted-foreground">
        <span>网关不提供独立启停开关；停用供应商或路由组即可停止对应流量。</span>
        <span>LAN、TLS、mTLS 由部署配置管理，页面不提供伪造的运行期开关。</span>
        {providersPath || routeGroupsPath ? (
          <span className="ms-auto flex items-center gap-3">
            {providersPath ? (
              <Link to={providersPath} className="font-medium text-primary hover:underline">
                供应商
              </Link>
            ) : null}
            {routeGroupsPath ? (
              <Link to={routeGroupsPath} className="font-medium text-primary hover:underline">
                路由组
              </Link>
            ) : null}
          </span>
        ) : null}
      </div>
    </ConsoleSection>
  );
}

function StatusMetric({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="space-y-1">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <p className="text-sm font-medium">{value}</p>
      <p className="truncate text-[11px] text-muted-foreground" title={hint}>
        {hint}
      </p>
    </div>
  );
}
