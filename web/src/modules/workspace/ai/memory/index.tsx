import { useMemo, useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Settings2, Upload, UserPlus } from "lucide-react";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { agentHint, agentLabel } from "@/modules/workspace/ai/library/lib/agents";
import { updateAiSetting } from "@/modules/workspace/ai/providers/lib/api";
import { useUsageAdminAccess } from "@/modules/workspace/ai/usage/lib/admin";
import { ConsolePage, ConsoleSection } from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { AllocateMemoryDialog } from "./components/allocate-memory-dialog";
import { AssetCreateDialog } from "./components/asset-create-dialog";
import { ImportMemoryDialog } from "./components/import-memory-dialog";
import { MemoryAssetDetailDialog } from "./components/memory-asset-detail-dialog";
import {
  MemoryAssetDeleteDialog,
  MemoryClearDialog,
  MemoryConfirmDialog,
} from "./components/memory-confirm-dialogs";
import { MemoryDetailPanel } from "./components/memory-detail-panel";
import { MemoryListPanel } from "./components/memory-list-panel";
import { MemoryLogsTab } from "./components/memory-logs-tab";
import { MemoryPromptsTab } from "./components/memory-prompts-tab";
import { MemoryReviewTab } from "./components/memory-review-tab";
import { MemorySettingsDialog } from "./components/memory-settings-dialog";
import { MemorySplitLayout } from "./components/memory-split-layout";
import {
  type MemoryAssetScope,
  type MemoryAssetView,
  type MemoryImportMessage,
  bindMemoryAsset,
  clearMemoryAsset,
  createMemoryAsset,
  deleteMemoryAsset,
  importMemoryMessages,
  patchMemoryAsset,
  resolveMemoryApiError,
  unbindMemoryAsset,
} from "./lib/api";
import { MEMORY_VISIBILITY_LABELS, memoryAssetTitle } from "./lib/model";
import {
  invalidateMemoryAssetQueries,
  useCurrentUser,
  useMemoryAssets,
  useMemoryCounts,
  useMemorySetting,
  useOwnedAgents,
} from "./lib/queries";

type MemoryDialog = "create" | "import" | "settings" | null;

type Selection = {
  key: string;
  name: string;
};

export function MemoryView() {
  const queryClient = useQueryClient();
  const { isAdmin } = useUsageAdminAccess();
  const allowedOperations = useAllowedOperations();
  const canReview = allowedOperations.has("update");
  const canExecute = allowedOperations.has("execute");
  const canDelete = allowedOperations.has("delete");
  const canExport = allowedOperations.has("export");
  const showReview = canReview || canExecute || canDelete || canExport;
  const userQuery = useCurrentUser();
  const settingQuery = useMemorySetting();
  const currentUserId = userQuery.data?.spec.uid;
  const { agents: ownedAgents } = useOwnedAgents(currentUserId);

  const [scope, setScope] = useState<MemoryAssetScope>("team");
  const [agentFilter, setAgentFilter] = useState("ALL");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [dialog, setDialog] = useState<MemoryDialog>(null);
  const [allocateTarget, setAllocateTarget] = useState<MemoryAssetView | null>(null);
  const [unbindTarget, setUnbindTarget] = useState<{
    asset: MemoryAssetView;
    agent: string;
  } | null>(null);
  const [clearTarget, setClearTarget] = useState<MemoryAssetView | null>(null);
  const [visibilityTarget, setVisibilityTarget] = useState<MemoryAssetView | null>(null);
  const [detailTarget, setDetailTarget] = useState<MemoryAssetView | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<MemoryAssetView | null>(null);

  const selectedAgent = ownedAgents.find((agent) => agent.metadata.name === agentFilter);
  const defaultTeam = settingQuery.data?.spec.defaults?.team || "default";
  const team = selectedAgent?.spec.team || defaultTeam;
  const selectionKey = `${team}|${scope}|${agentFilter}`;

  const assetsQuery = useMemoryAssets(
    {
      team,
      scope,
      agent: agentFilter === "ALL" ? undefined : agentFilter,
    },
    Boolean(team),
  );
  const assets = useMemo(() => assetsQuery.data?.items ?? [], [assetsQuery.data]);
  const total = assetsQuery.data?.total ?? assets.length;
  const selectedAsset =
    selection?.key === selectionKey
      ? assets.find((asset) => asset.name === selection.name)
      : undefined;

  const countsQuery = useMemoryCounts(selectedAsset?.name, team, Boolean(selectedAsset));

  const ownerAssets = useMemo(
    () => assets.filter((asset) => asset.ownerUserId === currentUserId),
    [assets, currentUserId],
  );

  const allocateMutation = useMutation({
    mutationFn: (input: { asset: MemoryAssetView; agent: string }) =>
      bindMemoryAsset(input.asset.name, team, input.agent),
    onSuccess: async (_, input) => {
      await invalidateMemoryAssetQueries(queryClient, input.asset.name);
      toast.success("已分配记忆块");
      setAllocateTarget(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const unbindMutation = useMutation({
    mutationFn: (input: { asset: MemoryAssetView; agent: string }) =>
      unbindMemoryAsset(input.asset.name, team, input.agent),
    onSuccess: async (_, input) => {
      await invalidateMemoryAssetQueries(queryClient, input.asset.name);
      toast.success("已解绑 Agent");
      setUnbindTarget(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const visibilityMutation = useMutation({
    mutationFn: (input: { asset: MemoryAssetView; visibility: "TEAM" | "PRIVATE" }) =>
      patchMemoryAsset(input.asset.name, team, { visibility: input.visibility }),
    onSuccess: async (_, input) => {
      await invalidateMemoryAssetQueries(queryClient, input.asset.name);
      toast.success("记忆块可见性已更新");
      setVisibilityTarget(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const clearMutation = useMutation({
    mutationFn: (asset: MemoryAssetView) => clearMemoryAsset(asset.name, team),
    onSuccess: async (_, asset) => {
      await invalidateMemoryAssetQueries(queryClient, asset.name);
      toast.success("记忆块已清空");
      setClearTarget(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (input: { asset: MemoryAssetView; clear: boolean }) =>
      deleteMemoryAsset(input.asset.name, team, input.clear),
    onSuccess: async (_, input) => {
      await invalidateMemoryAssetQueries(queryClient, input.asset.name);
      if (selection?.name === input.asset.name) {
        setSelection(null);
      }
      toast.success(input.clear ? "记忆资产与其记忆内容已删除" : "记忆资产已删除");
      setDeleteTarget(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const createMutation = useMutation({
    mutationFn: createMemoryAsset,
    onSuccess: async (name) => {
      await invalidateMemoryAssetQueries(queryClient, name);
      setSelection({ key: selectionKey, name });
      toast.success("记忆资产已创建");
      setDialog(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const importMutation = useMutation({
    mutationFn: (input: { assetName: string; messages: MemoryImportMessage[] }) =>
      importMemoryMessages(input.assetName, team, input.messages),
    onSuccess: async (_, input) => {
      await invalidateMemoryAssetQueries(queryClient, input.assetName);
      setSelection({ key: selectionKey, name: input.assetName });
      toast.success(`已导入 ${input.messages.length} 条记忆`);
      setDialog(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const settingMutation = useMutation({
    mutationFn: async (enabled: boolean) => {
      const setting = settingQuery.data;
      if (!setting) {
        throw new Error("设置未加载完成。");
      }
      await updateAiSetting({
        ...setting,
        spec: {
          ...setting.spec,
          memory: { ...(setting.spec.memory ?? {}), enabled },
        },
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-settings"] });
      toast.success("记忆设置已保存");
      setDialog(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const memoryDisabled = settingQuery.data?.spec.memory?.enabled === false;
  const refreshing = assetsQuery.isFetching || countsQuery.isFetching;
  const pendingUnbindKey =
    unbindMutation.isPending && unbindMutation.variables
      ? `${unbindMutation.variables.asset.name}:${unbindMutation.variables.agent}`
      : undefined;

  function refreshSelected() {
    if (selectedAsset) {
      void invalidateMemoryAssetQueries(queryClient, selectedAsset.name);
      return;
    }
    void assetsQuery.refetch();
  }

  return (
    <ConsolePage title="记忆">
      <div className="flex flex-col gap-4">
        <ConsoleSection className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="min-w-0">
            <h1 className="truncate text-lg font-semibold">Chat Memory · 原子记忆块</h1>
            <p className="text-xs text-muted-foreground">
              {team} · 共 {total} 条记忆
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <ToggleGroup
              type="single"
              variant="outline"
              size="sm"
              value={scope}
              onValueChange={(value) => {
                if (value === "team" || value === "fixed") {
                  setScope(value);
                }
              }}
            >
              <ToggleGroupItem value="team">团队</ToggleGroupItem>
              <ToggleGroupItem value="fixed">Agent</ToggleGroupItem>
            </ToggleGroup>
            <Select value={agentFilter} onValueChange={setAgentFilter}>
              <SelectTrigger className="w-44" size="sm" aria-label="按 Agent 筛选记忆块">
                <SelectValue placeholder="全部 Agent" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部 Agent</SelectItem>
                {ownedAgents.map((agent) => {
                  const hint = agentHint(agent);
                  return (
                    <SelectItem key={agent.metadata.name} value={agent.metadata.name}>
                      {agentLabel(agent)}
                      {hint ? ` · ${hint}` : ""}
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!currentUserId || createMutation.isPending}
              onClick={() => setDialog("create")}
            >
              <Plus />
              创建记忆块
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={
                !selectedAsset ||
                !currentUserId ||
                selectedAsset.ownerUserId !== currentUserId ||
                allocateMutation.isPending
              }
              onClick={() => {
                if (selectedAsset) {
                  setAllocateTarget(selectedAsset);
                }
              }}
            >
              <UserPlus />
              分配到 Agent
            </Button>
            <Button
              type="button"
              size="sm"
              disabled={!currentUserId || importMutation.isPending}
              onClick={() => setDialog("import")}
            >
              <Upload />
              导入记忆
            </Button>
            {isAdmin ? (
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="记忆设置"
                onClick={() => setDialog("settings")}
              >
                <Settings2 />
              </Button>
            ) : null}
          </div>
        </ConsoleSection>

        {memoryDisabled ? (
          <Alert>
            <AlertTitle>记忆功能已关闭</AlertTitle>
            <AlertDescription>
              聊天不会召回或抽取记忆。
              {isAdmin ? "可在记忆设置中重新开启。" : "请联系管理员重新开启。"}
            </AlertDescription>
          </Alert>
        ) : null}

        <Tabs defaultValue="assets" className="gap-3">
          <TabsList variant="line" className="w-full justify-start border-b">
            <TabsTrigger value="assets" className="flex-none">
              记忆资产
            </TabsTrigger>
            <TabsTrigger value="logs" className="flex-none">
              生成日志
            </TabsTrigger>
            <TabsTrigger value="prompts" className="flex-none">
              提示词
            </TabsTrigger>
            {showReview ? (
              <TabsTrigger value="review" className="flex-none">
                审核
              </TabsTrigger>
            ) : null}
          </TabsList>
          <TabsContent value="assets">
            <MemorySplitLayout
              list={
                <MemoryListPanel
                  assets={assets}
                  total={total}
                  loading={assetsQuery.isLoading}
                  errorMessage={
                    assetsQuery.isError ? resolveMemoryApiError(assetsQuery.error) : undefined
                  }
                  selectedName={selectedAsset?.name}
                  currentUserId={currentUserId}
                  pendingUnbindKey={pendingUnbindKey}
                  onSelect={(asset) => setSelection({ key: selectionKey, name: asset.name })}
                  onRetry={() => void assetsQuery.refetch()}
                  onUnbind={(asset, agent) => setUnbindTarget({ asset, agent })}
                />
              }
              detail={
                <MemoryDetailPanel
                  asset={selectedAsset}
                  team={team}
                  counts={countsQuery.data}
                  currentUserId={currentUserId}
                  canDelete={canDelete}
                  refreshing={refreshing}
                  onRefresh={refreshSelected}
                  onShowDetail={() => {
                    if (selectedAsset) {
                      setDetailTarget(selectedAsset);
                    }
                  }}
                  onToggleVisibility={() => {
                    if (selectedAsset) {
                      setVisibilityTarget(selectedAsset);
                    }
                  }}
                  onDelete={() => {
                    if (selectedAsset) {
                      setDeleteTarget(selectedAsset);
                    }
                  }}
                  onClear={() => {
                    if (selectedAsset) {
                      setClearTarget(selectedAsset);
                    }
                  }}
                />
              }
            />
          </TabsContent>
          <TabsContent value="logs">
            <MemoryLogsTab assets={assets} team={team} />
          </TabsContent>
          <TabsContent value="prompts">
            <MemoryPromptsTab team={team} canManage={isAdmin} currentUserId={currentUserId} />
          </TabsContent>
          {showReview ? (
            <TabsContent value="review">
              <MemoryReviewTab
                currentUserId={currentUserId}
                canReview={canReview}
                canExecute={canExecute}
                canDelete={canDelete}
                canExport={canExport}
              />
            </TabsContent>
          ) : null}
        </Tabs>
      </div>

      {dialog === "create" && currentUserId ? (
        <AssetCreateDialog
          team={team}
          currentUserId={currentUserId}
          ownedAgents={ownedAgents}
          pending={createMutation.isPending}
          onCreate={(input) => createMutation.mutate(input)}
          onClose={() => setDialog(null)}
        />
      ) : null}

      {dialog === "import" ? (
        <ImportMemoryDialog
          assets={ownerAssets}
          initialAssetName={
            selectedAsset && selectedAsset.ownerUserId === currentUserId
              ? selectedAsset.name
              : undefined
          }
          pending={importMutation.isPending}
          onConfirm={(assetName, messages) => importMutation.mutate({ assetName, messages })}
          onClose={() => setDialog(null)}
        />
      ) : null}

      {dialog === "settings" ? (
        <MemorySettingsDialog
          setting={settingQuery.data}
          loading={settingQuery.isLoading}
          errorMessage={
            settingQuery.isError ? resolveMemoryApiError(settingQuery.error) : undefined
          }
          pending={settingMutation.isPending}
          onRetry={() => void settingQuery.refetch()}
          onSave={(enabled) => settingMutation.mutate(enabled)}
          onClose={() => setDialog(null)}
        />
      ) : null}

      {allocateTarget ? (
        <AllocateMemoryDialog
          asset={allocateTarget}
          ownedAgents={ownedAgents}
          pending={allocateMutation.isPending}
          onConfirm={(agent) => allocateMutation.mutate({ asset: allocateTarget, agent })}
          onClose={() => setAllocateTarget(null)}
        />
      ) : null}

      {unbindTarget ? (
        <MemoryConfirmDialog
          title="解绑 Agent"
          description={`将解除「${memoryAssetTitle(unbindTarget.asset)}」与 Agent「${unbindTarget.agent}」的绑定。记忆内容不会被删除，但该 Agent 将不再注入此记忆块。`}
          confirmLabel="解绑"
          destructive
          pending={unbindMutation.isPending}
          onConfirm={() => unbindMutation.mutate(unbindTarget)}
          onClose={() => setUnbindTarget(null)}
        />
      ) : null}

      {visibilityTarget ? (
        <MemoryConfirmDialog
          title={visibilityTarget.visibility === "TEAM" ? "切换为仅自己" : "切换为团队可见"}
          description={
            visibilityTarget.visibility === "TEAM"
              ? `切换后「${memoryAssetTitle(visibilityTarget)}」仅你可见，其他团队成员将无法查看。已绑定 Agent 不受影响。`
              : `切换后「${memoryAssetTitle(visibilityTarget)}」将对团队可见（${
                  MEMORY_VISIBILITY_LABELS.TEAM
                }）。`
          }
          confirmLabel="确认切换"
          pending={visibilityMutation.isPending}
          onConfirm={() =>
            visibilityMutation.mutate({
              asset: visibilityTarget,
              visibility: visibilityTarget.visibility === "TEAM" ? "PRIVATE" : "TEAM",
            })
          }
          onClose={() => setVisibilityTarget(null)}
        />
      ) : null}

      {clearTarget ? (
        <MemoryClearDialog
          assetTitle={memoryAssetTitle(clearTarget)}
          counts={clearTarget.name === selectedAsset?.name ? countsQuery.data : undefined}
          pending={clearMutation.isPending}
          onConfirm={() => clearMutation.mutate(clearTarget)}
          onClose={() => setClearTarget(null)}
        />
      ) : null}

      {detailTarget ? (
        <MemoryAssetDetailDialog
          assetName={detailTarget.name}
          team={team}
          onClose={() => setDetailTarget(null)}
        />
      ) : null}

      {deleteTarget ? (
        <MemoryAssetDeleteDialog
          assetTitle={memoryAssetTitle(deleteTarget)}
          counts={deleteTarget.name === selectedAsset?.name ? countsQuery.data : undefined}
          pending={deleteMutation.isPending}
          onConfirm={(clear) => deleteMutation.mutate({ asset: deleteTarget, clear })}
          onClose={() => setDeleteTarget(null)}
        />
      ) : null}
    </ConsolePage>
  );
}
