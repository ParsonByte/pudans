import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus } from "lucide-react";
import { toast } from "sonner";

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
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { AgentBadges } from "../components/agent-badges";
import { LibraryDeleteDialog } from "../components/library-delete-dialog";
import { LibraryPage } from "../components/library-page";
import { LibraryTable } from "../components/library-table";
import { ResourceRowActions } from "../components/resource-row-actions";
import { resolveLibraryApiError } from "../lib/api";
import { sortBy } from "../lib/model";
import { HookDialog } from "./components/hook-dialog";
import { HOOKS_QUERY_KEY, type HookItem, deleteHook, fetchHooks, updateHook } from "./lib/api";
import { HOOK_EVENT_LABELS, hookDisplayName, hookMatcherLabel } from "./lib/model";

export function AiHooksView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canToggle = allowedOperations.has("toggle");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<HookItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<HookItem | null>(null);

  const hooksQuery = useQuery({
    queryKey: HOOKS_QUERY_KEY,
    queryFn: fetchHooks,
    retry: false,
  });
  const hooks = useMemo(() => sortBy(hooksQuery.data ?? [], hookDisplayName), [hooksQuery.data]);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: HOOKS_QUERY_KEY });

  const toggleMutation = useMutation({
    mutationFn: (hook: HookItem) =>
      updateHook({ ...hook, spec: { ...hook.spec, enabled: hook.spec.enabled === false } }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "Hook")),
  });

  const deleteMutation = useMutation({
    mutationFn: (hook: HookItem) => deleteHook(hook.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("Hook 已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "Hook"));
    },
  });

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (hook: HookItem) => {
    setEditing(hook);
    setEditorOpen(true);
  };

  return (
    <LibraryPage
      title="Hooks"
      heading="Hooks"
      description="订阅 Agent 会话与工具事件，把触发信息交给外部自动化处理。"
      count={hooks.length}
      countUnit="个 Hook"
      refreshing={hooksQuery.isFetching}
      onRefresh={() => void hooksQuery.refetch()}
      actions={
        canCreate ? (
          <Button type="button" size="sm" onClick={openCreate}>
            <Plus />
            新建 Hook
          </Button>
        ) : null
      }
    >
      <LibraryTable
        loading={hooksQuery.isPending}
        error={hooksQuery.error}
        isEmpty={hooks.length === 0}
        emptyMessage="还没有 Hook。创建后可按事件订阅 Agent 行为。"
        errorTitle="无法加载 Hook"
        resolveError={(error) => resolveLibraryApiError(error, "Hook")}
        onRetry={() => void hooksQuery.refetch()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>事件</TableHead>
              <TableHead>匹配器</TableHead>
              <TableHead>超时</TableHead>
              <TableHead>关联 Agent</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {hooks.map((hook) => {
              const enabled = hook.spec.enabled !== false;
              const toggling =
                toggleMutation.isPending &&
                toggleMutation.variables?.metadata.name === hook.metadata.name;
              return (
                <TableRow key={hook.metadata.name}>
                  <TableCell>
                    <div className="space-y-0.5">
                      <p className="font-medium">{hookDisplayName(hook)}</p>
                      {hook.spec.description ? (
                        <p className="max-w-64 truncate text-[11px] text-muted-foreground">
                          {hook.spec.description}
                        </p>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{HOOK_EVENT_LABELS[hook.spec.event]}</Badge>
                  </TableCell>
                  <TableCell>
                    <span className="block max-w-48 truncate font-mono text-xs text-muted-foreground">
                      {hookMatcherLabel(hook)}
                    </span>
                  </TableCell>
                  <TableCell>{hook.spec.timeoutSeconds} 秒</TableCell>
                  <TableCell>
                    <AgentBadges agents={hook.spec.agents} />
                  </TableCell>
                  <TableCell>
                    {canToggle ? (
                      <div className="flex items-center gap-2">
                        {toggling ? (
                          <Loader2 className="size-4 animate-spin text-muted-foreground" />
                        ) : null}
                        <Switch
                          checked={enabled}
                          disabled={toggling}
                          onCheckedChange={() => toggleMutation.mutate(hook)}
                          aria-label={enabled ? "停用 Hook" : "启用 Hook"}
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
                      canDelete={canDelete}
                      onEdit={() => openEdit(hook)}
                      onDelete={() => setDeleteTarget(hook)}
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </LibraryTable>

      {editorOpen ? (
        <HookDialog
          hook={editing ?? undefined}
          onClose={() => {
            setEditorOpen(false);
            setEditing(null);
          }}
        />
      ) : null}

      <LibraryDeleteDialog
        resourceLabel="Hook"
        name={deleteTarget ? hookDisplayName(deleteTarget) : null}
        pending={deleteMutation.isPending}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </LibraryPage>
  );
}
