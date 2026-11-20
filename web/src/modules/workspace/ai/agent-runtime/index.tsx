import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleAlert, Loader2, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTableFacetedFilter, DataTablePagination } from "@/components/data-table";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AI_AGENTS_QUERY_KEY } from "@/modules/workspace/ai/library/lib/agents";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { AgentDialog } from "./components/agent-dialog";
import { AgentRuntimeDetail } from "./components/agent-runtime-detail";
import { AgentRuntimeTable } from "./components/agent-runtime-table";
import { RuntimeLoadError, RuntimeTableSkeleton } from "./components/runtime-states";
import { SandboxProviderSettings } from "./components/sandbox-provider-settings";
import {
  AGENT_RUNTIME_AGENTS_QUERY_KEY,
  AI_RUNTIMES_QUERY_KEY,
  AI_RUNTIME_PROVIDERS_QUERY_KEY,
  AI_SANDBOX_PROVIDERS_QUERY_KEY,
  type AiRuntimeAgentItem,
  deleteRuntime,
  deleteRuntimeAgent,
  fetchAllRuntimes,
  fetchRuntimeAgents,
  fetchSandboxProviders,
  resolveAgentRuntimeError,
  restartRuntime,
  startRuntime,
  stopRuntime,
} from "./lib/api";
import {
  type AgentRuntimeRow,
  RUNTIME_ACTION_SUCCESS,
  RUNTIME_OPERATIONS,
  RUNTIME_PROVIDER_OPTIONS,
  RUNTIME_STATE_FILTER_OPTIONS,
  type RuntimeAction,
  type RuntimeConfirmAction,
  mergeAgentRuntimeRows,
  rowRuntimeProvider,
  rowRuntimeState,
} from "./lib/model";

type RuntimeConfirm = {
  name: string;
  action: RuntimeConfirmAction;
};

const RUNTIME_CONFIRM_COPY: Record<
  RuntimeConfirmAction,
  { title: string; description: (name: string) => string; confirmText: string }
> = {
  stop: {
    title: "停止运行时",
    description: (name) => `将停止并销毁 Agent ${name} 的沙箱，未持久化的文件会丢失。`,
    confirmText: "停止",
  },
  restart: {
    title: "重启运行时",
    description: (name) => `将销毁并重新创建 Agent ${name} 的沙箱，未持久化的数据会丢失。`,
    confirmText: "重启",
  },
  destroy: {
    title: "销毁运行时",
    description: (name) => `将销毁 Agent ${name} 的沙箱并删除运行时记录，Agent 配置不受影响。`,
    confirmText: "销毁",
  },
};

export function AgentRuntimeView() {
  const queryClient = useQueryClient();
  const operations = useAllowedOperations();
  const canCreate = operations.has(RUNTIME_OPERATIONS.create);
  const canUpdate = operations.has(RUNTIME_OPERATIONS.update);
  const canDelete = operations.has(RUNTIME_OPERATIONS.delete);
  const canToggle = operations.has(RUNTIME_OPERATIONS.toggle);
  const canExec = operations.has(RUNTIME_OPERATIONS.execute);
  const canFiles = operations.has(RUNTIME_OPERATIONS.files);
  const canLogs = operations.has(RUNTIME_OPERATIONS.logs);

  const [tab, setTab] = useState("agents");
  const [stateFilters, setStateFilters] = useState<string[]>([]);
  const [providerFilters, setProviderFilters] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [agentDialog, setAgentDialog] = useState<AiRuntimeAgentItem | null | undefined>(undefined);
  const [detailName, setDetailName] = useState<string | null>(null);
  const [runtimeConfirm, setRuntimeConfirm] = useState<RuntimeConfirm | null>(null);
  const [deleteAgentTarget, setDeleteAgentTarget] = useState<AiRuntimeAgentItem | null>(null);

  const agentsQuery = useQuery({
    queryKey: AGENT_RUNTIME_AGENTS_QUERY_KEY,
    queryFn: fetchRuntimeAgents,
    retry: false,
  });
  const runtimesQuery = useQuery({
    queryKey: AI_RUNTIMES_QUERY_KEY,
    queryFn: fetchAllRuntimes,
    retry: false,
  });
  const providersQuery = useQuery({
    queryKey: AI_SANDBOX_PROVIDERS_QUERY_KEY,
    queryFn: fetchSandboxProviders,
    retry: false,
  });

  const rows = useMemo(
    () => mergeAgentRuntimeRows(agentsQuery.data ?? [], runtimesQuery.data ?? []),
    [agentsQuery.data, runtimesQuery.data],
  );
  const filteredRows = useMemo(
    () =>
      rows.filter(
        (row) =>
          (stateFilters.length === 0 || stateFilters.includes(rowRuntimeState(row))) &&
          (providerFilters.length === 0 || providerFilters.includes(rowRuntimeProvider(row))),
      ),
    [rows, stateFilters, providerFilters],
  );
  const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const pagedRows = filteredRows.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const enabledProviders = useMemo(
    () =>
      new Set(
        (providersQuery.data ?? [])
          .filter((provider) => provider.spec.enabled !== false)
          .map((provider) => provider.spec.provider),
      ),
    [providersQuery.data],
  );
  const providerMissing = providersQuery.isSuccess && enabledProviders.size === 0;

  const runtimeMutation = useMutation({
    mutationFn: async ({ name, action }: { name: string; action: RuntimeAction }) => {
      if (action === "start") {
        return startRuntime(name);
      }
      if (action === "stop") {
        return stopRuntime(name);
      }
      if (action === "restart") {
        return restartRuntime(name);
      }
      await deleteRuntime(name);
      return null;
    },
    onSuccess: async (_result, variables) => {
      setRuntimeConfirm(null);
      toast.success(RUNTIME_ACTION_SUCCESS[variables.action]);
      await queryClient.invalidateQueries({ queryKey: AI_RUNTIMES_QUERY_KEY });
    },
    onError: (error) => toast.error(resolveAgentRuntimeError(error)),
  });

  const deleteAgentMutation = useMutation({
    mutationFn: async (agent: AiRuntimeAgentItem) => {
      await deleteRuntime(agent.metadata.name);
      await deleteRuntimeAgent(agent.metadata.name);
    },
    onSuccess: async (_result, agent) => {
      setDeleteAgentTarget(null);
      setDetailName(null);
      toast.success(`Agent ${agent.spec.displayName || agent.metadata.name} 已删除。`);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: AGENT_RUNTIME_AGENTS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: AI_RUNTIMES_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: AI_AGENTS_QUERY_KEY }),
      ]);
    },
    onError: (error) => toast.error(resolveAgentRuntimeError(error)),
  });

  const busyAgent = runtimeMutation.isPending
    ? runtimeMutation.variables?.name
    : deleteAgentMutation.isPending
      ? deleteAgentMutation.variables?.metadata.name
      : undefined;
  const agentsLoading = agentsQuery.isPending || runtimesQuery.isPending;
  const agentsError = agentsQuery.error ?? runtimesQuery.error;
  const refreshing =
    agentsQuery.isFetching || runtimesQuery.isFetching || providersQuery.isFetching;

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: AGENT_RUNTIME_AGENTS_QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: AI_RUNTIMES_QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: AI_SANDBOX_PROVIDERS_QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: AI_RUNTIME_PROVIDERS_QUERY_KEY });
  };

  const updateStateFilters = (values: string[]) => {
    setStateFilters(values);
    setPage(1);
  };

  const updateProviderFilters = (values: string[]) => {
    setProviderFilters(values);
    setPage(1);
  };

  const handleRuntimeAction = (name: string, action: RuntimeAction) => {
    if (action === "start") {
      runtimeMutation.mutate({ name, action });
      return;
    }
    setRuntimeConfirm({ name, action });
  };

  const handleEditAgent = (agent: AiRuntimeAgentItem) => {
    setDetailName(null);
    setAgentDialog(agent);
  };

  const handleDeleteAgent = (agent: AiRuntimeAgentItem) => {
    setDeleteAgentTarget(agent);
  };

  const detailRow: AgentRuntimeRow | undefined = detailName
    ? rows.find((row) => row.name === detailName)
    : undefined;

  return (
    <ConsolePage title="Agent Runtime">
      <div className="flex flex-col gap-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-base font-semibold">Agent 运行时</h2>
            <p className="text-xs text-muted-foreground">
              {agentsLoading
                ? "正在加载 Agent 与运行时状态。"
                : `${rows.length} 个 Agent，${runtimesQuery.data?.length ?? 0} 个运行时。`}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="outline" onClick={refresh} disabled={refreshing}>
              {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            {tab === "agents" && canCreate ? (
              <Button type="button" onClick={() => setAgentDialog(null)}>
                <Plus />
                新建 Agent
              </Button>
            ) : null}
          </div>
        </div>

        <Tabs value={tab} onValueChange={setTab} className="space-y-4">
          <TabsList variant="line" className="w-full justify-start border-b pb-1">
            <TabsTrigger value="agents" className="flex-none px-3">
              Agent 运行时
            </TabsTrigger>
            <TabsTrigger value="providers" className="flex-none px-3">
              沙箱提供方
            </TabsTrigger>
          </TabsList>

          <TabsContent value="agents" className="space-y-4">
            {providerMissing ? (
              <Alert>
                <CircleAlert />
                <AlertTitle>尚未配置沙箱提供方</AlertTitle>
                <AlertDescription>
                  启动 Agent
                  沙箱前，请先在「沙箱提供方」中配置并启用阿里云或腾讯云凭据；否则启动会失败。
                </AlertDescription>
                <AlertAction>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setTab("providers")}
                  >
                    前往配置
                  </Button>
                </AlertAction>
              </Alert>
            ) : null}

            <ConsoleSection className="overflow-hidden">
              {agentsLoading ? (
                <RuntimeTableSkeleton rows={4} />
              ) : agentsError ? (
                <RuntimeLoadError
                  title="无法加载 Agent 运行时"
                  description={resolveAgentRuntimeError(agentsError)}
                  onRetry={refresh}
                />
              ) : rows.length === 0 ? (
                <ConsoleEmpty>
                  <div className="space-y-3">
                    <p>还没有 Agent。新建 Agent 并配置沙箱运行时后，即可启动沙箱。</p>
                    {canCreate ? (
                      <Button type="button" size="sm" onClick={() => setAgentDialog(null)}>
                        <Plus />
                        新建 Agent
                      </Button>
                    ) : null}
                  </div>
                </ConsoleEmpty>
              ) : (
                <>
                  <div className="flex flex-wrap items-center gap-2 border-b px-4 py-3">
                    <DataTableFacetedFilter
                      title="运行状态"
                      options={RUNTIME_STATE_FILTER_OPTIONS}
                      selected={stateFilters}
                      onSelectedChange={updateStateFilters}
                    />
                    <DataTableFacetedFilter
                      title="提供方"
                      options={RUNTIME_PROVIDER_OPTIONS}
                      selected={providerFilters}
                      onSelectedChange={updateProviderFilters}
                    />
                    <span className="text-xs text-muted-foreground">
                      共 {filteredRows.length} 个 Agent
                    </span>
                  </div>
                  {filteredRows.length === 0 ? (
                    <ConsoleEmpty>
                      <div className="space-y-3">
                        <p>没有符合筛选条件的 Agent。</p>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            updateStateFilters([]);
                            updateProviderFilters([]);
                          }}
                        >
                          清除筛选
                        </Button>
                      </div>
                    </ConsoleEmpty>
                  ) : (
                    <>
                      <AgentRuntimeTable
                        rows={pagedRows}
                        canToggle={canToggle}
                        canUpdate={canUpdate}
                        canDelete={canDelete}
                        busyAgent={busyAgent}
                        onOpen={setDetailName}
                        onEdit={handleEditAgent}
                        onDeleteAgent={handleDeleteAgent}
                        onAction={handleRuntimeAction}
                      />
                      <DataTablePagination
                        currentPage={currentPage}
                        pageCount={pageCount}
                        pageSize={pageSize}
                        onPageChange={setPage}
                        onPageSizeChange={(size) => {
                          setPageSize(size);
                          setPage(1);
                        }}
                        disabled={refreshing}
                        className="border-t px-4 py-3"
                      />
                    </>
                  )}
                </>
              )}
            </ConsoleSection>
          </TabsContent>

          <TabsContent value="providers">
            <SandboxProviderSettings
              canCreate={canCreate}
              canUpdate={canUpdate}
              canDelete={canDelete}
            />
          </TabsContent>
        </Tabs>
      </div>

      {agentDialog !== undefined ? (
        <AgentDialog
          agent={agentDialog ?? undefined}
          configuredProviders={enabledProviders}
          onClose={() => setAgentDialog(undefined)}
        />
      ) : null}

      {detailRow ? (
        <AgentRuntimeDetail
          row={detailRow}
          canToggle={canToggle}
          canUpdate={canUpdate}
          canDelete={canDelete}
          canExec={canExec}
          canFiles={canFiles}
          canLogs={canLogs}
          busy={busyAgent === detailRow.name}
          onAction={handleRuntimeAction}
          onEdit={handleEditAgent}
          onDeleteAgent={handleDeleteAgent}
          onClose={() => setDetailName(null)}
        />
      ) : null}

      {runtimeConfirm ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setRuntimeConfirm(null)}
          title={RUNTIME_CONFIRM_COPY[runtimeConfirm.action].title}
          desc={RUNTIME_CONFIRM_COPY[runtimeConfirm.action].description(runtimeConfirm.name)}
          confirmText={RUNTIME_CONFIRM_COPY[runtimeConfirm.action].confirmText}
          destructive
          isLoading={runtimeMutation.isPending}
          handleConfirm={() => runtimeMutation.mutate(runtimeConfirm)}
        />
      ) : null}

      {deleteAgentTarget ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setDeleteAgentTarget(null)}
          title="删除 Agent"
          desc={
            <span>
              将先销毁并移除 Agent「
              {deleteAgentTarget.spec.displayName || deleteAgentTarget.metadata.name}
              」的运行时记录，再删除 Agent 本身；关联资源不会被删除。若运行时销毁失败，Agent
              不会被删除。
            </span>
          }
          confirmText="删除 Agent"
          destructive
          isLoading={deleteAgentMutation.isPending}
          handleConfirm={() => deleteAgentMutation.mutate(deleteAgentTarget)}
        />
      ) : null}
    </ConsolePage>
  );
}
