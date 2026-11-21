import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, MessageSquare, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { fetchKnowledgeBases } from "@/modules/workspace/ai/knowledge/lib/api";
import { ResourceRowActions } from "@/modules/workspace/ai/library/components/resource-row-actions";
import { resolveLibraryApiError } from "@/modules/workspace/ai/library/lib/api";
import { fetchAllRouteGroups } from "@/modules/workspace/ai/providers/lib/api";
import {
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { ChatModeDialog } from "./components/chat-mode-dialog";
import {
  type AiChatModeItem,
  CHAT_MODES_QUERY_KEY,
  deleteChatMode,
  fetchChatModes,
  updateChatMode,
} from "./lib/api";
import { chatModeLabel, chatModeTierLabel, isBuiltinChatMode, sortChatModes } from "./lib/model";

const PAGE_TITLE = "对话模式";

export function AiChatModesView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<AiChatModeItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AiChatModeItem | null>(null);

  const modesQuery = useQuery({
    queryKey: CHAT_MODES_QUERY_KEY,
    queryFn: fetchChatModes,
    retry: false,
  });
  const routeGroupsQuery = useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
    retry: false,
  });
  const knowledgeBasesQuery = useQuery({
    queryKey: ["knowledge-bases"],
    queryFn: fetchKnowledgeBases,
    retry: false,
  });

  const modes = sortChatModes(modesQuery.data ?? []);
  const routeGroups = routeGroupsQuery.data ?? [];
  const knowledgeBases = knowledgeBasesQuery.data ?? [];
  const groupLabels = new Map(
    routeGroups.map((group) => [
      group.metadata.name,
      group.spec.displayName || group.metadata.name,
    ]),
  );

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: CHAT_MODES_QUERY_KEY });
    await queryClient.invalidateQueries({ queryKey: ["chat-modes"] });
  };

  const toggleMutation = useMutation({
    mutationFn: (mode: AiChatModeItem) =>
      updateChatMode({
        ...mode,
        spec: { ...mode.spec, enabled: mode.spec.enabled === false },
      }),
    onSuccess: invalidate,
    onError: (error) => toast.error(resolveLibraryApiError(error, "对话模式")),
  });

  const deleteMutation = useMutation({
    mutationFn: (mode: AiChatModeItem) => deleteChatMode(mode.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("对话模式已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "对话模式"));
    },
  });

  const refresh = () => {
    void modesQuery.refetch();
    void routeGroupsQuery.refetch();
    void knowledgeBasesQuery.refetch();
  };

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (mode: AiChatModeItem) => {
    setEditing(mode);
    setEditorOpen(true);
  };

  const closeEditor = () => {
    setEditorOpen(false);
    setEditing(null);
  };

  if (!canView) {
    return (
      <ConsolePage title={PAGE_TITLE}>
        <ConsoleState variant="permission" title="没有查看对话模式的权限。" wrapper />
      </ConsolePage>
    );
  }

  return (
    <ConsolePage
      title={PAGE_TITLE}
      description="对话模式是用户可见的入口：每个模式绑定一个路由组、默认模型档位和知识库集合。"
      actions={
        <>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={refresh}
            disabled={modesQuery.isFetching}
          >
            {modesQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
          {canCreate ? (
            <Button type="button" size="sm" onClick={openCreate}>
              <Plus />
              新建模式
            </Button>
          ) : null}
        </>
      }
    >
      {modesQuery.isPending ? (
        <ConsoleSection className="overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>模式</TableHead>
                <TableHead>路由组</TableHead>
                <TableHead>默认档位</TableHead>
                <TableHead>知识库</TableHead>
                <TableHead>启用</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <ConsoleTableSkeleton columns={6} />
            </TableBody>
          </Table>
        </ConsoleSection>
      ) : modesQuery.isError ? (
        <ConsoleState
          variant="error"
          title="无法加载对话模式"
          description={resolveLibraryApiError(modesQuery.error, "对话模式")}
          onRetry={() => void modesQuery.refetch()}
          wrapper
        />
      ) : modes.length === 0 ? (
        <ConsoleState
          variant="empty"
          title="还没有对话模式"
          description="创建对话模式后，用户即可在对话入口选择它对应的路由组与知识库。"
          wrapper
          action={
            canCreate ? (
              <Button type="button" size="sm" onClick={openCreate}>
                <Plus />
                新建第一个模式
              </Button>
            ) : null
          }
        />
      ) : (
        <ConsoleSection className="overflow-hidden">
          <ConsoleSectionHeader
            title="模式列表"
            description="fast 与 pro 为内置模式，可编辑但不可删除。"
            action={<span className="text-xs text-muted-foreground">{modes.length} 个模式</span>}
          />
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>模式</TableHead>
                <TableHead>路由组</TableHead>
                <TableHead>默认档位</TableHead>
                <TableHead>知识库</TableHead>
                <TableHead>启用</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {modes.map((mode) => {
                const enabled = mode.spec.enabled !== false;
                const toggling =
                  toggleMutation.isPending &&
                  toggleMutation.variables?.metadata.name === mode.metadata.name;
                const codes = mode.spec.knowledgeBaseCodes ?? [];
                return (
                  <TableRow key={mode.metadata.name}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <MessageSquare className="size-4 text-muted-foreground" />
                        <div>
                          <p className="font-medium">{chatModeLabel(mode)}</p>
                          <span className="font-mono text-[11px] text-muted-foreground">
                            {mode.metadata.name}
                          </span>
                        </div>
                      </div>
                      {mode.spec.description ? (
                        <p className="mt-1 max-w-64 truncate text-[11px] text-muted-foreground">
                          {mode.spec.description}
                        </p>
                      ) : null}
                    </TableCell>
                    <TableCell className="text-xs">
                      {groupLabels.get(mode.spec.routeGroup) ?? mode.spec.routeGroup}
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline">{chatModeTierLabel(mode.spec.defaultTier)}</Badge>
                    </TableCell>
                    <TableCell>
                      <span className="block max-w-56 truncate text-xs text-muted-foreground">
                        {codes.length === 0 ? "未绑定" : codes.join("、")}
                      </span>
                    </TableCell>
                    <TableCell>
                      {canUpdate ? (
                        <div className="flex items-center gap-2">
                          {toggling ? (
                            <Loader2 className="size-4 animate-spin text-muted-foreground" />
                          ) : null}
                          <Switch
                            checked={enabled}
                            disabled={toggling}
                            onCheckedChange={() => toggleMutation.mutate(mode)}
                            aria-label={enabled ? "停用对话模式" : "启用对话模式"}
                          />
                        </div>
                      ) : (
                        <Badge variant={enabled ? "secondary" : "outline"}>
                          {enabled ? "已启用" : "已停用"}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <ResourceRowActions
                        canUpdate={canUpdate}
                        canDelete={canDelete && !isBuiltinChatMode(mode.metadata.name)}
                        onEdit={() => openEdit(mode)}
                        onDelete={() => setDeleteTarget(mode)}
                      />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </ConsoleSection>
      )}

      {editorOpen ? (
        <ChatModeDialog
          mode={editing ?? undefined}
          modes={modes}
          routeGroups={routeGroups}
          knowledgeBases={knowledgeBases}
          routeGroupsLoading={routeGroupsQuery.isPending}
          knowledgeBasesLoading={knowledgeBasesQuery.isPending}
          routeGroupsFailed={routeGroupsQuery.isError}
          knowledgeBasesFailed={knowledgeBasesQuery.isError}
          onClose={closeEditor}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除对话模式"
        desc={
          <>
            确定删除「{deleteTarget ? chatModeLabel(deleteTarget) : ""}
            」？删除后用户将无法再选择该模式， 此操作无法恢复。
          </>
        }
        destructive
        confirmText="删除"
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </ConsolePage>
  );
}
