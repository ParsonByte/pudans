import { useMemo, useState } from "react";

import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { Loader2, Network, RefreshCw, Settings2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ConsolePage, ConsoleSection } from "@/modules/workspace/console/components/console-page";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import {
  fetchAllProviderAccounts,
  fetchAllProviderModels,
  fetchProviders,
  resolveAiProviderApiError,
} from "../providers/lib/api";
import { usableModels } from "../route-groups/lib/model";
import { fetchRoutingLive } from "../routing/lib/api";
import { CopyField } from "./components/copy-field";
import { GatewayAccess } from "./components/gateway-access";
import { GatewayModels } from "./components/gateway-models";
import { GatewayRecentCalls } from "./components/gateway-recent-calls";
import { type GatewayToken, createGatewayToken } from "./lib/api";

const RECENT_LIMIT_OPTIONS = [10, 20, 50];

export function AiGatewayView() {
  const providersPath = useMenuPath("ai-providers");
  const routeGroupsPath = useMenuPath("ai-route-groups");
  const [token, setToken] = useState<GatewayToken | null>(null);
  const [recentLimit, setRecentLimit] = useState(20);

  const providersQuery = useQuery({
    queryKey: ["ai-providers", "all"],
    queryFn: () => fetchProviders(1, 100),
    retry: false,
  });
  const accountsQuery = useQuery({
    queryKey: ["ai-provider-accounts", "all"],
    queryFn: fetchAllProviderAccounts,
    retry: false,
  });
  const modelsQuery = useQuery({
    queryKey: ["ai-provider-models", "all"],
    queryFn: fetchAllProviderModels,
    retry: false,
  });
  const liveQuery = useQuery({
    queryKey: ["ai-gateway", "recent", recentLimit],
    queryFn: () => fetchRoutingLive(7, recentLimit),
    retry: false,
    refetchInterval: 15_000,
  });

  const providers = useMemo(() => providersQuery.data?.items ?? [], [providersQuery.data]);
  const accounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
  const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const usable = useMemo(
    () => usableModels(models, providers, accounts),
    [models, providers, accounts],
  );
  const live = liveQuery.data;
  const agents = useMemo(
    () => new Set((live?.requests ?? []).map((request) => request.agentSource)).size,
    [live?.requests],
  );
  const origin = typeof window === "undefined" ? "" : window.location.origin;

  const tokenMutation = useMutation({
    mutationFn: createGatewayToken,
    onSuccess: (created) => {
      setToken(created);
      toast.success("网关密钥已生成，请立即复制保存。");
    },
    onError: (error) => {
      const message = resolveAiProviderApiError(error);
      toast.error(
        message.includes("没有授予") || message.includes("角色")
          ? "当前账号没有 AI Agent 网关角色，请让管理员先授予后再生成密钥。"
          : message,
      );
    },
  });

  const loading = providersQuery.isPending || accountsQuery.isPending || modelsQuery.isPending;
  const refreshing =
    providersQuery.isFetching ||
    accountsQuery.isFetching ||
    modelsQuery.isFetching ||
    liveQuery.isFetching;
  const refresh = () => {
    void providersQuery.refetch();
    void accountsQuery.refetch();
    void modelsQuery.refetch();
    void liveQuery.refetch();
  };
  const coreError = providersQuery.error ?? accountsQuery.error ?? modelsQuery.error;

  return (
    <ConsolePage title="网关">
      <div className="flex flex-col gap-5">
        <ConsoleSection className="overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-5">
            <div className="flex items-start gap-3">
              <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <Network className="size-5" />
              </span>
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-base font-semibold">网关</h2>
                  <Badge variant={usable.length > 0 ? "secondary" : "outline"}>
                    <span
                      className={`size-1.5 rounded-full ${
                        usable.length > 0 ? "bg-emerald-500" : "bg-muted-foreground"
                      }`}
                    />
                    {usable.length > 0 ? "运行中" : "待配置"}
                  </Badge>
                </div>
                <p className="text-xs text-muted-foreground">
                  {usable.length} 个可用模型 · 最近 {agents} 个 Agent · 2 种 API，一个地址
                </p>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="outline" size="sm" asChild>
                <Link to="/">聊天页面</Link>
              </Button>
              <Button size="sm" onClick={refresh} disabled={refreshing}>
                {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
                刷新
              </Button>
            </div>
          </div>

          <div className="grid gap-4 border-t px-5 py-4 lg:grid-cols-[1fr_auto] lg:items-end">
            <div className="max-w-2xl">
              <CopyField
                label="访问地址"
                value={origin}
                hint="同一网络内的设备能否访问，取决于部署方式、端口和反向代理；外部设备请使用网关密钥。"
              />
            </div>
            <div className="flex flex-wrap items-center gap-4 text-[11px] text-muted-foreground">
              <span>OpenAI · Anthropic</span>
              {routeGroupsPath ? (
                <Link
                  to={routeGroupsPath}
                  className="inline-flex items-center gap-1 font-medium text-primary hover:underline"
                >
                  <Settings2 className="size-3" />
                  路由组
                </Link>
              ) : null}
            </div>
          </div>
        </ConsoleSection>

        {coreError ? (
          <div className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-xs text-destructive">
            无法加载网关配置：{resolveAiProviderApiError(coreError)}
          </div>
        ) : null}

        <GatewayAccess
          models={usable}
          providers={providers}
          token={token}
          creatingToken={tokenMutation.isPending}
          onCreateToken={() => tokenMutation.mutate()}
        />

        <GatewayModels
          models={usable}
          providers={providers}
          loading={loading}
          providersPath={providersPath}
        />

        <GatewayRecentCalls
          live={live}
          providers={providers}
          models={models}
          loading={liveQuery.isPending}
          error={liveQuery.isError}
          limit={recentLimit}
        />

        <ConsoleSection>
          <div className="flex items-start gap-3 border-b px-5 py-4">
            <span className="flex size-8 items-center justify-center rounded-lg bg-muted">
              <Settings2 className="size-4 text-muted-foreground" />
            </span>
            <div className="space-y-1">
              <h2 className="text-sm font-semibold">设置</h2>
              <p className="text-xs text-muted-foreground">网关页面的显示选项。</p>
            </div>
          </div>
          <div className="divide-y">
            <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">每次显示的最近调用</p>
                <p className="text-xs text-muted-foreground">
                  页面显示最近 7 天内的多少条网关调用记录。
                </p>
              </div>
              <Select
                value={String(recentLimit)}
                onValueChange={(value) => setRecentLimit(Number(value))}
              >
                <SelectTrigger className="w-24">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RECENT_LIMIT_OPTIONS.map((option) => (
                    <SelectItem key={option} value={String(option)}>
                      {option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">协议与模型</p>
                <p className="text-xs text-muted-foreground">
                  网关提供 OpenAI 和 Anthropic 两种兼容 API；模型由路由组选择。
                </p>
              </div>
              <div className="flex items-center gap-2">
                {providersPath ? (
                  <Button variant="outline" size="sm" asChild>
                    <Link to={providersPath}>管理供应商</Link>
                  </Button>
                ) : null}
                {routeGroupsPath ? (
                  <Button variant="outline" size="sm" asChild>
                    <Link to={routeGroupsPath}>管理路由组</Link>
                  </Button>
                ) : null}
              </div>
            </div>
          </div>
        </ConsoleSection>
      </div>
    </ConsolePage>
  );
}
