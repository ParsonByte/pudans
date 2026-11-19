import { useMemo } from "react";

import { Icon } from "@iconify/react";
import { Link } from "@tanstack/react-router";
import {
  Background,
  BaseEdge,
  Controls,
  type Edge,
  type EdgeProps,
  Handle,
  type Node,
  type NodeProps,
  Position,
  ReactFlow,
  getSmoothStepPath,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Loader2, RefreshCw, Settings2, UserRound } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type {
  AiModelItem,
  AiProviderAccountItem,
  AiProviderItem,
  AiRouteGroupItem,
} from "../../providers/lib/api";
import { formatNumber, modelDisplayName } from "../../providers/lib/model";
import type { AiRoutingLive, AiRoutingUser } from "../lib/api";
import {
  formatTokenCount,
  routeGroupDisplayName,
  routeModeLabel,
  shortIdentifier,
} from "../lib/model";

const GATEWAY_NODE_ID = "gateway";

const USER_NODE_X = 0;

const GATEWAY_NODE_X = 330;

const PROVIDER_NODE_X = 660;

const NODE_ROW_GAP = 116;

const USER_NODE_HEIGHT = 88;

const GATEWAY_NODE_HEIGHT = 136;

const PROVIDER_NODE_HEIGHT = 88;

type TopologyTone = "idle" | "active" | "failed";

type TopologyNodeData = {
  kind: "USER" | "GATEWAY" | "PROVIDER";
  user?: AiRoutingUser;
  provider?: AiProviderItem;
  tone?: TopologyTone;
  running?: boolean;
  providerCount?: number;
  accountCount?: number;
  modelCount?: number;
  modelLabel?: string;
  usage?: number;
};

type TopologyEdgeData = {
  tone: TopologyTone;
};

type TopologyFlowNode = Node<TopologyNodeData, "topologyNode">;

type TopologyFlowEdge = Edge<TopologyEdgeData, "topologyEdge">;

const nodeTypes = { topologyNode: TopologyNodeView };

const edgeTypes = { topologyEdge: TopologyEdgeView };

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
    const nodes: TopologyFlowNode[] = [];
    const edges: TopologyFlowEdge[] = [];
    const rows = Math.max(liveUsers.length, enabledProviders.length, 1);
    const center = ((rows - 1) * NODE_ROW_GAP) / 2;

    liveUsers.forEach((user, index) => {
      const tone = requestTone(latest?.userId === user.userId, latest?.status);
      nodes.push({
        id: userNodeId(user.userId),
        type: "topologyNode",
        position: {
          x: USER_NODE_X,
          y: center + (index - (liveUsers.length - 1) / 2) * NODE_ROW_GAP - USER_NODE_HEIGHT / 2,
        },
        data: { kind: "USER", user, tone },
        draggable: false,
        selectable: false,
      });
      edges.push({
        id: `${userNodeId(user.userId)}-${GATEWAY_NODE_ID}`,
        source: userNodeId(user.userId),
        target: GATEWAY_NODE_ID,
        type: "topologyEdge",
        data: { tone },
      });
    });

    nodes.push({
      id: GATEWAY_NODE_ID,
      type: "topologyNode",
      position: { x: GATEWAY_NODE_X, y: center - GATEWAY_NODE_HEIGHT / 2 },
      data: {
        kind: "GATEWAY",
        tone: requestTone(true, latest?.status),
        running: enabledProviders.length > 0,
        providerCount: enabledProviders.length,
      },
      draggable: false,
      selectable: false,
    });

    enabledProviders.forEach((provider, index) => {
      const providerAccounts = enabledAccounts.filter(
        (account) => account.spec.provider === provider.metadata.name,
      );
      const providerModels = models.filter(
        (model) => model.spec.provider === provider.metadata.name,
      );
      const liveHere = latest?.provider === provider.metadata.name;
      const latestModel = liveHere ? latest.model : providerModels[0]?.metadata.name;
      const latestModelItem = latestModel
        ? models.find((model) => model.metadata.name === latestModel)
        : undefined;
      const tone = requestTone(liveHere, latest?.status);
      nodes.push({
        id: providerNodeId(provider.metadata.name),
        type: "topologyNode",
        position: {
          x: PROVIDER_NODE_X,
          y:
            center +
            (index - (enabledProviders.length - 1) / 2) * NODE_ROW_GAP -
            PROVIDER_NODE_HEIGHT / 2,
        },
        data: {
          kind: "PROVIDER",
          provider,
          tone,
          running: liveHere && latest?.status === "RUNNING",
          accountCount: providerAccounts.length,
          modelCount: providerModels.length,
          modelLabel: latestModelItem ? modelDisplayName(latestModelItem) : "未添加模型",
          usage: providerUsage.get(provider.metadata.name) ?? 0,
        },
        draggable: false,
        selectable: false,
      });
      edges.push({
        id: `${GATEWAY_NODE_ID}-${providerNodeId(provider.metadata.name)}`,
        source: GATEWAY_NODE_ID,
        target: providerNodeId(provider.metadata.name),
        type: "topologyEdge",
        data: { tone },
      });
    });

    return {
      nodes,
      edges,
      height: Math.min(520, Math.max(330, rows * NODE_ROW_GAP + 96)),
    };
  }, [enabledAccounts, enabledProviders, latest, liveUsers, models, providerUsage]);

  const nodeSignature = useMemo(
    () =>
      `${liveUsers.map((user) => user.userId).join("|")}::${enabledProviders
        .map((provider) => provider.metadata.name)
        .join("|")}`,
    [enabledProviders, liveUsers],
  );

  return (
    <ConsoleSection className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
        <div className="flex items-center gap-2">
          <span className="relative flex size-2">
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-60" />
            <span className="relative inline-flex size-2 rounded-full bg-emerald-500" />
          </span>
          <span className="text-sm font-medium">实时</span>
          <span className="text-xs text-muted-foreground">· 网关正在做的决定</span>
        </div>
        <div className="flex items-center gap-2">
          {routeGroupsPath ? (
            <Button variant="ghost" size="sm" asChild>
              <Link to={routeGroupsPath}>
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
          <Skeleton className="h-[330px] w-full rounded-2xl" />
        ) : enabledProviders.length === 0 ? (
          <div className="flex min-h-40 flex-col items-center justify-center rounded-2xl border border-dashed px-4 py-8 text-center text-xs text-muted-foreground">
            还没有启用的供应商。
            {providersPath ? (
              <Link to={providersPath} className="mt-1 font-medium text-primary hover:underline">
                去供应商配置
              </Link>
            ) : null}
          </div>
        ) : (
          <div
            className="relative w-full overflow-hidden rounded-2xl border bg-background [&_.react-flow__controls-button]:border-border [&_.react-flow__controls-button]:bg-background [&_.react-flow__controls-button]:text-foreground [&_.react-flow__controls-button:hover]:bg-muted"
            style={{ height: graph.height }}
          >
            {live && liveUsers.length === 0 ? (
              <div className="pointer-events-none absolute top-4 left-4 z-10 rounded-full border bg-background/90 px-3 py-1 text-[11px] text-muted-foreground">
                最近还没有用户调用
              </div>
            ) : null}
            <ReactFlow
              key={nodeSignature}
              nodes={graph.nodes}
              edges={graph.edges}
              nodeTypes={nodeTypes}
              edgeTypes={edgeTypes}
              fitView
              fitViewOptions={{ padding: 0.18 }}
              minZoom={0.35}
              maxZoom={1.4}
              nodesConnectable={false}
              nodesDraggable={false}
              elementsSelectable={false}
              zoomOnScroll={false}
              proOptions={{ hideAttribution: true }}
            >
              <Background gap={22} size={1} color="var(--border)" />
              <Controls showInteractive={false} />
            </ReactFlow>
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
              {routeModeLabel(defaultGroup.spec.routeMode)}
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

function TopologyNodeView({ data }: NodeProps<TopologyFlowNode>) {
  if (data.kind === "USER") {
    return <UserNode data={data} />;
  }
  if (data.kind === "GATEWAY") {
    return <GatewayNode data={data} />;
  }
  return <ProviderNode data={data} />;
}

function UserNode({ data }: { data: TopologyNodeData }) {
  const user = data.user;
  if (!user) {
    return null;
  }
  const tone = data.tone ?? "idle";

  return (
    <div
      className={cn(
        "relative flex h-[88px] w-60 items-center gap-3 rounded-2xl border bg-card px-4 py-3 shadow-sm transition-colors",
        tone === "active" && "border-primary/60 ring-2 ring-primary/15",
        tone === "failed" && "border-destructive/60 ring-2 ring-destructive/15",
      )}
    >
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
        <UserRound className="size-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">
          {user.displayName || user.username || "未知用户"}
        </p>
        <p className="truncate text-[11px] text-muted-foreground">
          {user.username ? `@${user.username}` : shortIdentifier(user.userId, 16)} ·{" "}
          {formatNumber(user.requests)} 次请求
        </p>
      </div>
      {user.failed > 0 ? (
        <span className="shrink-0 text-[10px] font-medium text-destructive">
          {formatNumber(user.failed)} 失败
        </span>
      ) : null}
      {tone !== "idle" ? (
        <span
          className={cn(
            "absolute top-1/2 -right-1 size-2 -translate-y-1/2 rounded-full",
            tone === "failed" ? "bg-destructive" : "bg-primary",
          )}
        />
      ) : null}
      <Handle type="source" position={Position.Right} className="opacity-0" />
    </div>
  );
}

function GatewayNode({ data }: { data: TopologyNodeData }) {
  const tone = data.tone ?? "idle";

  return (
    <div
      className={cn(
        "relative flex h-[136px] w-56 flex-col justify-center gap-3 rounded-2xl border bg-card px-4 py-4 shadow-sm transition-colors",
        tone === "active" && "border-primary/60 ring-2 ring-primary/15",
        tone === "failed" && "border-destructive/60 ring-2 ring-destructive/15",
      )}
    >
      <div className="flex items-center gap-3">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
          <img
            src={`${import.meta.env.BASE_URL}brand/icon-large.svg`}
            alt=""
            className="size-8"
            aria-hidden="true"
          />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">PUDANS</p>
          <p className="truncate text-[11px] text-muted-foreground">
            AI 网关 · {data.providerCount ?? 0} 家供应商
          </p>
        </div>
      </div>
      <Badge variant={data.running ? "secondary" : "outline"} className="w-fit">
        <span
          className={cn(
            "size-1.5 rounded-full",
            data.running ? "animate-pulse bg-emerald-500" : "bg-muted-foreground",
          )}
        />
        {data.running ? "运行中" : "待配置"}
      </Badge>
      <Handle type="target" position={Position.Left} className="opacity-0" />
      <Handle type="source" position={Position.Right} className="opacity-0" />
    </div>
  );
}

function ProviderNode({ data }: { data: TopologyNodeData }) {
  const provider = data.provider;
  if (!provider) {
    return null;
  }
  const tone = data.tone ?? "idle";
  const usage = data.usage ?? 0;
  const status = data.running
    ? "回答中"
    : tone !== "idle"
      ? "最近调用"
      : usage > 0
        ? "今日有请求"
        : "待调用";

  return (
    <div
      className={cn(
        "relative flex h-[88px] w-64 items-center gap-3 rounded-2xl border bg-card px-4 py-3 shadow-sm transition-colors",
        tone === "active" && "border-primary/60 ring-2 ring-primary/15",
        tone === "failed" && "border-destructive/60 ring-2 ring-destructive/15",
      )}
    >
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
        <ProviderBrandIcon provider={provider} className="size-6" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-semibold">
            {provider.spec.displayName || provider.metadata.name}
          </p>
          <span
            className={cn(
              "inline-flex items-center gap-1 text-[10px] text-muted-foreground",
              data.running && "text-emerald-600 dark:text-emerald-400",
              tone === "failed" && "text-destructive",
            )}
          >
            <span
              className={cn(
                "size-1.5 rounded-full",
                data.running
                  ? "animate-pulse bg-emerald-500"
                  : tone === "failed"
                    ? "bg-destructive"
                    : "bg-muted-foreground/60",
              )}
            />
            {status}
          </span>
        </div>
        <p className="truncate text-[11px] text-muted-foreground">
          {data.accountCount ?? 0} 个账号 · {data.modelCount ?? 0} 个模型
        </p>
        <p className="truncate font-mono text-[10px] text-muted-foreground">{data.modelLabel}</p>
      </div>
      {tone !== "idle" ? (
        <span
          className={cn(
            "absolute top-1/2 -left-1 size-2 -translate-y-1/2 rounded-full",
            tone === "failed" ? "bg-destructive" : "bg-primary",
          )}
        />
      ) : null}
      <Handle type="target" position={Position.Left} className="opacity-0" />
    </div>
  );
}

function TopologyEdgeView({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
}: EdgeProps) {
  const tone = (data as TopologyEdgeData | undefined)?.tone ?? "idle";
  const [path] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 24,
  });
  const color =
    tone === "failed"
      ? "var(--destructive)"
      : tone === "active"
        ? "var(--primary)"
        : "var(--muted-foreground)";
  const opacity = tone === "idle" ? 0.35 : 0.85;
  const duration = tone === "idle" ? 3.6 : 2;
  const dotCount = tone === "idle" ? 1 : 2;

  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        style={{ stroke: color, strokeWidth: tone === "idle" ? 1.5 : 2.25, opacity }}
      />
      {Array.from({ length: dotCount }, (_, index) => (
        <circle key={index} r={tone === "idle" ? 2 : 3} fill={color} opacity={opacity}>
          <animateMotion
            dur={`${duration}s`}
            begin={`${index * (duration / 2)}s`}
            repeatCount="indefinite"
            path={path}
          />
        </circle>
      ))}
    </>
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
