import { useMemo } from "react";

import { useQuery } from "@tanstack/react-query";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import {
  fetchAiSetting,
  fetchAllProviderAccounts,
  fetchAllProviderModels,
  fetchAllRouteGroups,
  fetchProviders,
  resolveAiProviderApiError,
} from "../providers/lib/api";
import { RoutingAccounts } from "./components/routing-accounts";
import { RoutingContext } from "./components/routing-context";
import { RoutingDecision } from "./components/routing-decision";
import { RoutingHistory } from "./components/routing-history";
import { RoutingRequests } from "./components/routing-requests";
import { RoutingTopology } from "./components/routing-topology";
import { fetchRoutingLive } from "./lib/api";
import { buildRoutingLookups } from "./lib/model";

export function AiRoutingView() {
  const providersPath = useMenuPath("ai-providers");
  const routeGroupsPath = useMenuPath("ai-route-groups");
  const usagePath = useMenuPath("ai-usage-overview");

  const liveQuery = useQuery({
    queryKey: ["ai-routing", "live"],
    queryFn: () => fetchRoutingLive(7, 50),
    refetchInterval: 5_000,
    retry: false,
  });
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
  const routeGroupsQuery = useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
    retry: false,
  });
  const settingQuery = useQuery({
    queryKey: ["ai-settings", "ai-setting"],
    queryFn: fetchAiSetting,
    retry: false,
  });

  const providers = useMemo(() => providersQuery.data?.items ?? [], [providersQuery.data]);
  const accounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
  const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const routeGroups = useMemo(() => routeGroupsQuery.data ?? [], [routeGroupsQuery.data]);
  const lookups = useMemo(
    () =>
      buildRoutingLookups(providers, accounts, models, routeGroups, liveQuery.data?.users ?? []),
    [providers, accounts, models, routeGroups, liveQuery.data?.users],
  );
  const defaultGroupName = useMemo(() => {
    const enabled = routeGroups.filter((group) => group.spec.enabled !== false);
    const configured = settingQuery.data?.spec.defaultRouteGroup;
    if (configured && enabled.some((group) => group.metadata.name === configured)) {
      return configured;
    }
    return (
      enabled.find((group) => group.spec.isDefault)?.metadata.name ?? enabled[0]?.metadata.name
    );
  }, [routeGroups, settingQuery.data]);
  const defaultGroup = defaultGroupName ? lookups.routeGroups.get(defaultGroupName) : undefined;

  const loading =
    liveQuery.isPending ||
    providersQuery.isPending ||
    accountsQuery.isPending ||
    modelsQuery.isPending ||
    routeGroupsQuery.isPending;
  const error =
    liveQuery.isError ||
    providersQuery.isError ||
    accountsQuery.isError ||
    modelsQuery.isError ||
    routeGroupsQuery.isError;
  const refreshing =
    liveQuery.isFetching ||
    providersQuery.isFetching ||
    accountsQuery.isFetching ||
    modelsQuery.isFetching ||
    routeGroupsQuery.isFetching;

  const refresh = () => {
    void liveQuery.refetch();
    void providersQuery.refetch();
    void accountsQuery.refetch();
    void modelsQuery.refetch();
    void routeGroupsQuery.refetch();
    void settingQuery.refetch();
  };

  return (
    <ConsolePage title="路由">
      <Tabs defaultValue="live" className="gap-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <TabsList>
            <TabsTrigger value="live">实时</TabsTrigger>
            <TabsTrigger value="history">历史</TabsTrigger>
          </TabsList>
          <p className="text-xs text-muted-foreground">
            实时视图每 5 秒刷新；历史视图支持条件筛选与游标分页。
          </p>
        </div>
        <TabsContent value="live">
          <div className="flex flex-col gap-5">
            {liveQuery.isError ? (
              <div className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-xs text-destructive">
                无法加载实时路由数据：{resolveAiProviderApiError(liveQuery.error)}
              </div>
            ) : null}

            <RoutingTopology
              providers={providers}
              accounts={accounts}
              models={models}
              routeGroups={routeGroups}
              defaultGroup={defaultGroup}
              live={liveQuery.data}
              providersPath={providersPath}
              routeGroupsPath={routeGroupsPath}
              loading={loading}
              refreshing={refreshing}
              onRefresh={refresh}
            />

            <RoutingDecision
              live={liveQuery.data}
              lookups={lookups}
              defaultGroupName={defaultGroupName}
              loading={liveQuery.isPending}
              error={liveQuery.isError}
            />

            <RoutingContext
              live={liveQuery.data}
              lookups={lookups}
              loading={liveQuery.isPending}
              error={liveQuery.isError}
            />

            <RoutingRequests
              live={liveQuery.data}
              lookups={lookups}
              loading={liveQuery.isPending}
              error={liveQuery.isError}
              usagePath={usagePath}
            />

            <RoutingAccounts
              providers={providers}
              accounts={accounts}
              live={liveQuery.data}
              loading={loading}
              error={error}
              providersPath={providersPath}
              usagePath={usagePath}
            />
          </div>
        </TabsContent>
        <TabsContent value="history">
          <RoutingHistory
            providers={providers}
            models={models}
            routeGroups={routeGroups}
            lookups={lookups}
          />
        </TabsContent>
      </Tabs>
    </ConsolePage>
  );
}
