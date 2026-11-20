import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";

import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";

import {
  fetchAiSetting,
  fetchAllProviderAccounts,
  fetchAllProviderModels,
  fetchAllRouteGroups,
  fetchProviders,
  resolveAiProviderApiError,
} from "../providers/lib/api";
import { usableModels } from "../route-groups/lib/model";
import { fetchRoutingLive } from "../routing/lib/api";
import { GatewayAccess } from "./components/gateway-access";
import { GatewayKeys } from "./components/gateway-keys";
import { GatewayModels } from "./components/gateway-models";
import { GatewayRecentCalls } from "./components/gateway-recent-calls";
import { GatewaySessions } from "./components/gateway-sessions";
import { GatewaySettings } from "./components/gateway-settings";
import { GatewayStatus } from "./components/gateway-status";
import {
  type GatewayCreatedToken,
  type GatewayRecentError,
  fetchGatewayStatus,
  fetchGatewayTokens,
  gatewaySpecObserved,
} from "./lib/api";

export function AiGatewayView() {
  const providersPath = useMenuPath("ai-providers");
  const routeGroupsPath = useMenuPath("ai-route-groups");
  const sessionsPath = useMenuPath("ai-sessions");
  const allowed = useAllowedOperations();
  const canCreateKeys = allowed.has("create");
  const canManageKeys = allowed.has("update");
  const canEditSettings = allowed.has("settings");
  const canManageSessions = allowed.has("sessions");
  const [createOpen, setCreateOpen] = useState(false);
  const [createdToken, setCreatedToken] = useState<GatewayCreatedToken | null>(null);
  const [gatewaySpecRejected, setGatewaySpecRejected] = useState(false);

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
    enabled: canEditSettings,
  });
  const tokensQuery = useQuery({
    queryKey: ["ai-gateway", "tokens"],
    queryFn: fetchGatewayTokens,
    retry: false,
    enabled: canManageKeys,
  });
  const statusQuery = useQuery({
    queryKey: ["ai-gateway", "status"],
    queryFn: fetchGatewayStatus,
    retry: false,
  });
  const fallbackLiveQuery = useQuery({
    queryKey: ["ai-gateway", "status-fallback"],
    queryFn: () => fetchRoutingLive(7, 50),
    retry: false,
    enabled: statusQuery.isError,
  });

  const providers = useMemo(() => providersQuery.data?.items ?? [], [providersQuery.data]);
  const accounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
  const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const routeGroups = useMemo(() => routeGroupsQuery.data ?? [], [routeGroupsQuery.data]);
  const tokens = useMemo(() => tokensQuery.data ?? [], [tokensQuery.data]);
  const usable = useMemo(
    () => usableModels(models, providers, accounts),
    [models, providers, accounts],
  );
  const enabledGroups = useMemo(
    () => routeGroups.filter((group) => group.spec.enabled !== false),
    [routeGroups],
  );
  const defaultRouteGroup = useMemo(() => {
    const configured = settingQuery.data?.spec.defaultRouteGroup;
    if (configured && enabledGroups.some((group) => group.metadata.name === configured)) {
      return configured;
    }
    return (
      enabledGroups.find((group) => group.spec.isDefault)?.metadata.name ??
      enabledGroups[0]?.metadata.name
    );
  }, [enabledGroups, settingQuery.data]);
  const fallbackErrors = useMemo<GatewayRecentError[]>(
    () =>
      (fallbackLiveQuery.data?.requests ?? [])
        .flatMap((request) =>
          request.status === "FAILED" && request.errorCode
            ? [{ code: request.errorCode, at: request.createdAt }]
            : [],
        )
        .slice(0, 5),
    [fallbackLiveQuery.data],
  );

  const advancedSupported =
    !gatewaySpecRejected && (statusQuery.isSuccess || gatewaySpecObserved(tokens));
  const configLoading =
    providersQuery.isPending ||
    accountsQuery.isPending ||
    modelsQuery.isPending ||
    routeGroupsQuery.isPending;
  const refreshing =
    providersQuery.isFetching ||
    accountsQuery.isFetching ||
    modelsQuery.isFetching ||
    routeGroupsQuery.isFetching ||
    statusQuery.isFetching ||
    (canManageKeys && tokensQuery.isFetching) ||
    (canEditSettings && settingQuery.isFetching) ||
    (statusQuery.isError && fallbackLiveQuery.isFetching);
  const refresh = () => {
    void providersQuery.refetch();
    void accountsQuery.refetch();
    void modelsQuery.refetch();
    void routeGroupsQuery.refetch();
    void statusQuery.refetch();
    if (canManageKeys) {
      void tokensQuery.refetch();
    }
    if (canEditSettings) {
      void settingQuery.refetch();
    }
    if (statusQuery.isError) {
      void fallbackLiveQuery.refetch();
    }
  };
  const coreError =
    providersQuery.error ?? accountsQuery.error ?? modelsQuery.error ?? routeGroupsQuery.error;

  return (
    <ConsolePage title="网关">
      <div className="flex flex-col gap-5">
        <GatewayStatus
          status={statusQuery.data}
          statusLoading={statusQuery.isPending}
          statusError={statusQuery.error}
          routeGroups={routeGroups}
          fallback={{
            usableModelCount: usable.length,
            routeGroupCount: enabledGroups.length,
            defaultRouteGroup,
            rateLimitEnabled: settingQuery.data?.spec.gateway?.rateLimitEnabled,
            recentErrors: fallbackErrors,
            recentErrorsLoading: statusQuery.isError && fallbackLiveQuery.isPending,
            recentErrorsUnavailable: fallbackLiveQuery.isError,
          }}
          loading={configLoading}
          refreshing={refreshing}
          providersPath={providersPath}
          routeGroupsPath={routeGroupsPath}
          onRefresh={refresh}
        />

        {coreError ? (
          <div className="border border-destructive/30 bg-destructive/5 px-4 py-3 text-xs text-destructive">
            无法加载网关配置：{resolveAiProviderApiError(coreError)}
          </div>
        ) : null}

        <GatewayAccess
          models={usable}
          providers={providers}
          entryUrl={statusQuery.data?.entryUrl}
          tokens={tokens}
          tokensLoading={canManageKeys && tokensQuery.isPending}
          canListTokens={canManageKeys}
          createdToken={createdToken}
          canCreateToken={canCreateKeys}
          onCreateToken={() => setCreateOpen(true)}
        />

        <GatewayModels
          models={usable}
          providers={providers}
          loading={configLoading}
          providersPath={providersPath}
        />

        <GatewayKeys
          tokens={canManageKeys ? tokensQuery.data : undefined}
          loading={tokensQuery.isPending}
          error={canManageKeys ? tokensQuery.error : null}
          canCreate={canCreateKeys}
          canManage={canManageKeys}
          models={usable}
          advancedSupported={advancedSupported}
          createdToken={createdToken}
          onCreated={(result) => {
            setCreatedToken(result.created);
            if (!result.gatewayApplied) {
              setGatewaySpecRejected(true);
            }
          }}
          onDismissCreated={() => setCreatedToken(null)}
          onRetry={() => void tokensQuery.refetch()}
          createOpen={createOpen}
          onCreateOpenChange={setCreateOpen}
        />

        <GatewaySettings
          setting={settingQuery.data}
          loading={settingQuery.isPending}
          error={canEditSettings ? settingQuery.error : null}
          canEdit={canEditSettings}
          onRetry={() => void settingQuery.refetch()}
        />

        <GatewayRecentCalls providers={providers} models={models} />

        {canManageSessions ? <GatewaySessions canDelete sessionsPath={sessionsPath} /> : null}
      </div>
    </ConsolePage>
  );
}
