import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { fetchChatModes } from "@/modules/workspace/index/lib/chat-api";
import { chatModeLabel } from "@/modules/workspace/index/lib/chat-functions";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";

import {
  type AiRouteGroupItem,
  type AiRouteGroupTier,
  createRouteGroup,
  deleteRouteGroup,
  fetchAiSetting,
  fetchAllProviderAccounts,
  fetchAllProviderModels,
  fetchAllRouteGroups,
  fetchProviders,
  resolveAiProviderApiError,
  updateAiSetting,
  updateRouteGroup,
} from "../providers/lib/api";
import { ChatModeRoutes } from "./components/chat-mode-routes";
import { RouteGroupCard } from "./components/route-group-card";
import { RouteGroupDialog } from "./components/route-group-dialog";
import { RouteSettings } from "./components/route-settings";
import { RouteTemplateGrid } from "./components/route-template-grid";
import {
  type RouteTemplate,
  buildRouteTemplates,
  groupFromTemplate,
  usableModels,
} from "./lib/model";

type SettingsPatch = {
  defaultRouteGroup?: string | null;
  classifierEnabled?: boolean;
  classifierFallbackTier?: AiRouteGroupTier;
};

export function AiRouteGroupsView() {
  const queryClient = useQueryClient();
  const allowed = useAllowedOperations();
  const canCreate = allowed.has("create");
  const canUpdate = allowed.has("update");
  const canDelete = allowed.has("delete");
  const providersPath = useMenuPath("ai-providers");

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<AiRouteGroupItem | null>(null);
  const [template, setTemplate] = useState<RouteTemplate | undefined>(undefined);
  const [deleteTarget, setDeleteTarget] = useState<AiRouteGroupItem | null>(null);

  const routeGroupsQuery = useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
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
  const settingQuery = useQuery({
    queryKey: ["ai-settings", "ai-setting"],
    queryFn: fetchAiSetting,
    retry: false,
  });
  const chatModesQuery = useQuery({
    queryKey: ["chat-modes"],
    queryFn: fetchChatModes,
    retry: false,
  });

  const routeGroups = useMemo(() => routeGroupsQuery.data ?? [], [routeGroupsQuery.data]);
  const providers = useMemo(() => providersQuery.data?.items ?? [], [providersQuery.data]);
  const accounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
  const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const setting = settingQuery.data;
  const chatModes = useMemo(() => chatModesQuery.data ?? [], [chatModesQuery.data]);
  const modeByGroup = useMemo(() => {
    const map = new Map<string, string>();
    for (const mode of chatModes) {
      if (mode.routeGroup) {
        map.set(mode.routeGroup, chatModeLabel(mode));
      }
    }
    return map;
  }, [chatModes]);
  const usable = useMemo(
    () => usableModels(models, providers, accounts),
    [models, providers, accounts],
  );
  const templates = useMemo(
    () => buildRouteTemplates(usable, providers, accounts),
    [usable, providers, accounts],
  );
  const existingNames = useMemo(
    () => new Set(routeGroups.map((group) => group.metadata.name)),
    [routeGroups],
  );
  const settingDefault = setting?.spec.defaultRouteGroup;
  const defaultGroupName =
    settingDefault && routeGroups.some((group) => group.metadata.name === settingDefault)
      ? settingDefault
      : (routeGroups.find((group) => group.spec.isDefault)?.metadata.name ??
        routeGroups[0]?.metadata.name);

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ["ai-route-groups"] });
    await queryClient.invalidateQueries({ queryKey: ["ai-settings"] });
  };

  const addTemplateMutation = useMutation({
    mutationFn: (selected: RouteTemplate) =>
      createRouteGroup(groupFromTemplate(selected, existingNames)),
    onSuccess: async (saved) => {
      await invalidate();
      toast.success(`已添加「${saved.spec.displayName}」。`);
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const toggleMutation = useMutation({
    mutationFn: (group: AiRouteGroupItem) =>
      updateRouteGroup({
        ...group,
        spec: { ...group.spec, enabled: group.spec.enabled === false },
      }),
    onSuccess: invalidate,
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteRouteGroup,
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("路由组已删除。");
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const settingsMutation = useMutation({
    mutationFn: async (patch: SettingsPatch) => {
      if (!setting) {
        throw new Error("AI 设置尚未加载。");
      }
      const spec = {
        ...setting.spec,
        routing: { ...(setting.spec.routing ?? {}) },
      };
      if ("defaultRouteGroup" in patch) {
        if (patch.defaultRouteGroup) {
          spec.defaultRouteGroup = patch.defaultRouteGroup;
        } else {
          delete spec.defaultRouteGroup;
        }
      }
      if (patch.classifierEnabled != null) {
        spec.routing.classifierEnabled = patch.classifierEnabled;
      }
      if (patch.classifierFallbackTier) {
        spec.routing.classifierFallbackTier = patch.classifierFallbackTier;
      }
      return updateAiSetting({ ...setting, spec });
    },
    onSuccess: async () => {
      await invalidate();
      toast.success("路由设置已更新。");
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const openCreate = () => {
    setEditingGroup(null);
    setTemplate(undefined);
    setDialogOpen(true);
  };

  const openTemplate = (selected: RouteTemplate) => {
    setEditingGroup(null);
    setTemplate(selected);
    setDialogOpen(true);
  };

  const openEdit = (group: AiRouteGroupItem) => {
    setEditingGroup(group);
    setTemplate(undefined);
    setDialogOpen(true);
  };

  const closeDialog = () => {
    setDialogOpen(false);
    setEditingGroup(null);
    setTemplate(undefined);
  };

  const refreshing =
    routeGroupsQuery.isFetching ||
    providersQuery.isFetching ||
    accountsQuery.isFetching ||
    modelsQuery.isFetching ||
    settingQuery.isFetching ||
    chatModesQuery.isFetching;
  const refresh = () => {
    void routeGroupsQuery.refetch();
    void providersQuery.refetch();
    void accountsQuery.refetch();
    void modelsQuery.refetch();
    void settingQuery.refetch();
    void chatModesQuery.refetch();
  };
  const coreError =
    routeGroupsQuery.error ?? providersQuery.error ?? accountsQuery.error ?? modelsQuery.error;
  const templateLoading =
    providersQuery.isPending || accountsQuery.isPending || modelsQuery.isPending;

  return (
    <ConsolePage title="路由组">
      <div className="flex flex-col gap-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground">
            管理网关可以选择的模型集合：按顺序、权重、成本或速度把请求分给成员。
          </p>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={refresh} disabled={refreshing}>
              {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            {canCreate ? (
              <Button size="sm" onClick={openCreate}>
                <Plus />
                新建组
              </Button>
            ) : null}
          </div>
        </div>

        {coreError ? (
          <div className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-xs text-destructive">
            无法加载路由组数据：{resolveAiProviderApiError(coreError)}
          </div>
        ) : null}

        <ChatModeRoutes
          modes={chatModes}
          routeGroups={routeGroups}
          loading={chatModesQuery.isPending}
          error={chatModesQuery.isError}
          canUpdate={canUpdate}
          onEdit={openEdit}
        />

        <ConsoleSection>
          <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
            <div className="space-y-1">
              <h2 className="text-sm font-semibold">
                当前路由组 <span className="text-muted-foreground">{routeGroups.length} 个</span>
              </h2>
              <p className="text-xs text-muted-foreground">
                默认组会接收没有显式指定路由组的请求。
              </p>
            </div>
          </div>

          {routeGroupsQuery.isPending ? (
            <div className="grid gap-3 px-5 py-5 sm:grid-cols-2 xl:grid-cols-3">
              {Array.from({ length: 3 }, (_, index) => (
                <Skeleton key={index} className="h-56 rounded-2xl" />
              ))}
            </div>
          ) : routeGroupsQuery.isError ? (
            <p className="px-5 py-5 text-xs text-muted-foreground">无法加载路由组。</p>
          ) : routeGroups.length === 0 ? (
            <ConsoleEmpty>
              <span className="space-y-2">
                <span className="block">还没有路由组。可以从模板新建，或者手动创建。</span>
                {canCreate ? (
                  <button
                    type="button"
                    onClick={openCreate}
                    className="text-sm font-medium text-primary hover:underline"
                  >
                    新建第一个路由组
                  </button>
                ) : null}
              </span>
            </ConsoleEmpty>
          ) : (
            <div className="grid gap-3 px-5 py-5 sm:grid-cols-2 xl:grid-cols-3">
              {routeGroups.map((group) => (
                <RouteGroupCard
                  key={group.metadata.name}
                  group={group}
                  models={models}
                  providers={providers}
                  isDefault={defaultGroupName === group.metadata.name}
                  usedByMode={modeByGroup.get(group.metadata.name)}
                  canUpdate={canUpdate}
                  canDelete={canDelete}
                  toggling={
                    toggleMutation.isPending &&
                    toggleMutation.variables?.metadata.name === group.metadata.name
                  }
                  onEdit={() => openEdit(group)}
                  onToggle={() => toggleMutation.mutate(group)}
                  onDelete={() => setDeleteTarget(group)}
                />
              ))}
            </div>
          )}
        </ConsoleSection>

        <RouteTemplateGrid
          templates={templates}
          providers={providers}
          loading={templateLoading}
          canCreate={canCreate}
          providersPath={providersPath}
          pendingTemplateId={
            addTemplateMutation.isPending ? addTemplateMutation.variables?.id : undefined
          }
          onAdd={(selected) => addTemplateMutation.mutate(selected)}
          onCustomize={openTemplate}
        />

        <RouteSettings
          setting={setting}
          routeGroups={routeGroups}
          loading={settingQuery.isPending}
          error={settingQuery.isError}
          canUpdate={canUpdate}
          saving={settingsMutation.isPending}
          onChange={(patch) => settingsMutation.mutate(patch)}
        />
      </div>

      {dialogOpen ? (
        <RouteGroupDialog
          group={editingGroup ?? undefined}
          template={template}
          routeGroups={routeGroups}
          models={models}
          providers={providers}
          accounts={accounts}
          onClose={closeDialog}
          onSaved={() => {
            closeDialog();
          }}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除路由组"
        desc={
          <>
            确定删除「
            {deleteTarget?.spec.displayName || deleteTarget?.metadata.name}
            」？该组会立即从 Agent 的可用路由中移除，此操作无法恢复。
          </>
        }
        destructive
        confirmText="删除"
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (!deleteTarget) {
            return;
          }
          if (deleteTarget.metadata.name === defaultGroupName) {
            toast.error("默认路由组不能删除，请先在设置里更换默认组。");
            return;
          }
          deleteMutation.mutate(deleteTarget.metadata.name);
        }}
      />
    </ConsolePage>
  );
}
