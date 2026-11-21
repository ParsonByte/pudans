import { useMemo } from "react";

import { Icon } from "@iconify/react";
import { Loader2, RefreshCw, Settings2 } from "lucide-react";
import Link from "next/link";

import {
  SigmaGraph,
  type SigmaGraphEdge,
  type SigmaGraphNode,
} from "@/components/graph/sigma-graph";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";

import type {
  AiModelItem,
  AiProviderAccountItem,
  AiProviderItem,
  AiRouteGroupItem,
} from "../../providers/lib/api";
import { formatNumber } from "../../providers/lib/model";
import {
  effectiveParentMode,
  parentModeLabel,
  routeGroupRules,
} from "../../route-groups/lib/model";
import type { AiRoutingLive } from "../lib/api";
import { formatTokenCount, routeGroupDisplayName, routeModeLabel } from "../lib/model";

const GATEWAY_NODE_ID = "gateway";

type TopologyTone = "idle" | "active" | "failed";

type RoutingTopologyProps = {
  providers: AiProviderItem[];
  accounts: AiProviderAccountItem[];
  models: AiModelItem[];
  routeGroups: AiRouteGroupItem[];
  defaultGroup: AiRouteGroupItem | undefined;
  live: AiRoutingLive | undefined;
  providersPath: string | undefined;
  routeGroupsPath: string | undefined;
  loading: boolean;
  refreshing: boolean;
  onRefresh: () => void;
};

export function RoutingTopology({
  providers,
  accounts,
  models,
  routeGroups,
  defaultGroup,
  live,
  providersPath,
  routeGroupsPath,
  loading,
  refreshing,
  onRefresh,
}: RoutingTopologyProps) {
  const enabledProviders = useMemo(
    () => providers.filter((provider) => provider.spec.enabled !== false),
    [providers],
  );
  const enabledAccounts = useMemo(
    () => accounts.filter((account) => account.spec.enabled),
    [accounts],
  );
  const liveUsers = useMemo(() => live?.users ?? [], [live?.users]);
  const latest = live?.requests[0];
  const providerUsage = useMemo(() => {
    const usage = new Map<string, number>();
    for (const activity of live?.accounts ?? []) {
      usage.set(activity.provider, (usage.get(activity.provider) ?? 0) + activity.requests);
    }
    return usage;
  }, [live?.accounts]);
  const groupModels = new Set(
    (defaultGroup?.spec.members ?? [])
      .filter((member) => member.enabled !== false)
      .map((member) => member.model),
  );
  const routingAccounts = enabledAccounts.filter((account) => {
    const provider = providers.find(
      (item) => item.metadata.name === account.spec.provider && item.spec.enabled !== false,
    );
    if (!provider) {
      return false;
    }
    return models.some(
      (model) =>
        groupModels.has(model.metadata.name) &&
        model.spec.enabled !== false &&
        model.spec.provider === provider.metadata.name,
    );
  });

  const graph = useMemo(() => {
    const nodes: SigmaGraphNode[] = [];
    const edges: SigmaGraphEdge[] = [];
    const rows = Math.max(liveUsers.length, enabledProviders.length, 1);

    liveUsers.forEach((user) => {
      const tone = requestTone(latest?.userId === user.userId, latest?.status);
      const failed = user.failed > 0 ? ` · ${formatNumber(user.failed)} 失败` : "";
      nodes.push({
        id: userNodeId(user.userId),
        label: `${user.displayName || user.username || "未知用户"} · ${formatNumber(
          user.requests,
        )} 请求${failed}`,
        tone: tone === "failed" ? "destructive" : tone === "active" ? "primary" : undefined,
        size: 10,
      });
      edges.push({
        source: userNodeId(user.userId),
        target: GATEWAY_NODE_ID,
        tone: tone === "failed" ? "destructive" : tone === "active" ? "primary" : "muted",
        size: tone === "idle" ? 1 : 2,
      });
    });

    const gatewayTone = requestTone(true, latest?.status);
    nodes.push({
      id: GATEWAY_NODE_ID,
      label: `PUDANS 网关 · ${enabledProviders.length} 供应商`,
      tone:
        gatewayTone === "failed" ? "destructive" : gatewayTone === "active" ? "primary" : undefined,
      size: 18,
    });

    enabledProviders.forEach((provider) => {
      const providerAccounts = enabledAccounts.filter(
        (account) => account.spec.provider === provider.metadata.name,
      );
      const providerModels = models.filter(
        (model) => model.spec.provider === provider.metadata.name,
      );
      const liveHere = latest?.provider === provider.metadata.name;
      const tone = requestTone(liveHere, latest?.status);
      const usage = providerUsage.get(provider.metadata.name) ?? 0;
      nodes.push({
        id: providerNodeId(provider.metadata.name),
        label: `${provider.spec.displayName || provider.metadata.name} · ${
          providerAccounts.length
        } 账号/${providerModels.length} 模型${usage > 0 ? ` · ${formatNumber(usage)} 次` : ""}`,
        tone: tone === "failed" ? "destructive" : tone === "active" ? "primary" : undefined,
        size: 14,
      });
      edges.push({
        source: GATEWAY_NODE_ID,
        target: providerNodeId(provider.metadata.name),
        tone: tone === "failed" ? "destructive" : tone === "active" ? "primary" : "muted",
        size: tone === "idle" ? 1 : 2,
      });
    });

    return {
      nodes,
      edges,
      height: Math.min(520, Math.max(360, rows * 70 + 96)),
    };
  }, [enabledAccounts, enabledProviders, latest, liveUsers, models, providerUsage]);

  return (
    <ConsoleSection className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
        <div className="flex items-center gap-2">
          <span className="relative flex size-2">
            <span className="absolute inline-flex size-full animate-ping bg-success opacity-60" />
            <span className="relative inline-flex size-2 bg-success" />
          </span>
          <span className="text-sm font-medium">实时</span>
          <span className="text-xs text-muted-foreground">· 网关正在做的决定</span>
        </div>
        <div className="flex items-center gap-2">
          {routeGroupsPath ? (
            <Button variant="ghost" size="sm" asChild>
              <Link href={routeGroupsPath}>
                <Settings2 />
                路由组
              </Link>
            </Button>
          ) : null}
          <Button variant="outline" size="sm" onClick={onRefresh} disabled={refreshing}>
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>
      </div>

      <div className="px-5 py-6">
        {loading ? (
          <Skeleton className="h-[330px] w-full" />
        ) : enabledProviders.length === 0 ? (
          <div className="flex min-h-40 flex-col items-center justify-center border border-dashed px-4 py-8 text-center text-xs text-muted-foreground">
            还没有启用的供应商。
            {providersPath ? (
              <Link href={providersPath} className="mt-1 font-medium text-primary hover:underline">
                去供应商配置
              </Link>
            ) : null}
          </div>
        ) : (
          <div className="relative w-full" style={{ height: graph.height }}>
            {live && liveUsers.length === 0 ? (
              <div className="pointer-events-none absolute top-4 left-4 z-10 border bg-background/90 px-3 py-1 text-[11px] text-muted-foreground">
                最近还没有用户调用
              </div>
            ) : null}
            <SigmaGraph
              nodes={graph.nodes}
              edges={graph.edges}
              height="100%"
              layout="force"
              ariaLabel="AI 路由实时拓扑"
            />
          </div>
        )}

        <p className="mt-4 max-w-3xl text-xs leading-5 text-muted-foreground">
          {caption(defaultGroup, providers, routeGroups, routingAccounts.length)}
        </p>
      </div>

      <div className="flex flex-wrap items-end justify-between gap-4 border-t px-5 py-4">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-muted-foreground">
          <span>
            {enabledProviders.length} 家供应商 · {enabledAccounts.length} 个启用账号
          </span>
          {defaultGroup ? (
            <span>
              默认路由组「{routeGroupDisplayName(defaultGroup, defaultGroup.metadata.name)}」·{" "}
              {routeModeLabel(defaultGroup.spec.routeMode)} ·{" "}
              {parentModeLabel(effectiveParentMode(defaultGroup))} · 规则{" "}
              {routeGroupRules(defaultGroup).length}
              {defaultGroup.spec.sink ? " · Sink" : ""}
            </span>
          ) : (
            <span>未设置默认路由组</span>
          )}
        </div>
        <div className="flex items-center gap-7">
          <Stat
            value={formatNumber(live?.summary.todayRequests)}
            label="今日请求"
            icon="lucide:arrow-left-right"
          />
          <Stat
            value={formatNumber(live?.summary.todayFailed)}
            label="失败"
            icon="lucide:circle-alert"
            tone={live?.summary.todayFailed ? "destructive" : undefined}
          />
          <Stat
            value={formatTokenCount(live?.summary.todayTokens)}
            label="今日 Token"
            icon="lucide:binary"
          />
        </div>
      </div>
    </ConsoleSection>
  );
}

function requestTone(matchesLatest: boolean, status: string | undefined): TopologyTone {
  if (!matchesLatest) {
    return "idle";
  }
  return status === "FAILED" ? "failed" : "active";
}

function userNodeId(userId: string): string {
  return `user-${userId}`;
}

function providerNodeId(provider: string): string {
  return `provider-${provider}`;
}

function caption(
  defaultGroup: AiRouteGroupItem | undefined,
  providers: AiProviderItem[],
  routeGroups: AiRouteGroupItem[],
  routingAccountCount: number,
): string {
  if (providers.filter((provider) => provider.spec.enabled !== false).length === 0) {
    return "还没有启用的供应商。先在供应商页添加账号，网关才能为 Agent 选择模型。";
  }
  if (!defaultGroup) {
    return routeGroups.length > 0
      ? "还没有标记默认路由组。到路由组页把其中一个设为默认。"
      : "还没有路由组。到路由组页创建成员后，网关才能为请求选择模型。";
  }
  const mode = routeModeLabel(defaultGroup.spec.routeMode);
  if (routingAccountCount === 0) {
    return `默认路由组「${routeGroupDisplayName(defaultGroup, defaultGroup.metadata.name)}」以${mode}方式路由，但组内模型还没有可用账号。`;
  }
  if (routingAccountCount === 1) {
    return "默认路由组只匹配到一个可用账号或 Key，路由没有可选项；在供应商编辑页再开一个账号，路由方式就会生效。";
  }
  return `默认路由组「${routeGroupDisplayName(defaultGroup, defaultGroup.metadata.name)}」以${mode}方式在 ${routingAccountCount} 个账号之间选择；网关每次请求的取舍会显示在下方。`;
}

type StatProps = {
  value: string;
  label: string;
  icon: string;
  tone?: "destructive";
};

function Stat({ value, label, icon, tone }: StatProps) {
  return (
    <div className="flex items-center gap-2">
      <Icon
        icon={icon}
        className={`size-4 ${tone === "destructive" ? "text-destructive" : "text-muted-foreground"}`}
        aria-hidden="true"
      />
      <div>
        <p
          className={`text-lg leading-5 font-semibold tabular-nums ${
            tone === "destructive" ? "text-destructive" : ""
          }`}
        >
          {value}
        </p>
        <p className="text-[11px] text-muted-foreground">{label}</p>
      </div>
    </div>
  );
}
