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
import { SubagentDialog } from "./components/subagent-dialog";
import {
  SUBAGENTS_QUERY_KEY,
  type SubagentItem,
  deleteSubagent,
  fetchSubagents,
  updateSubagent,
} from "./lib/api";
import { subagentDisplayName, subagentPromptExcerpt, subagentTargetLabel } from "./lib/model";

export function AiSubagentsView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canToggle = allowedOperations.has("toggle");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SubagentItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SubagentItem | null>(null);

  const subagentsQuery = useQuery({
    queryKey: SUBAGENTS_QUERY_KEY,
    queryFn: fetchSubagents,
    retry: false,
  });
  const subagents = useMemo(
    () => sortBy(subagentsQuery.data ?? [], subagentDisplayName),
    [subagentsQuery.data],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: SUBAGENTS_QUERY_KEY });

  const toggleMutation = useMutation({
    mutationFn: (subagent: SubagentItem) =>
      updateSubagent({
        ...subagent,
        spec: { ...subagent.spec, enabled: subagent.spec.enabled === false },
      }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "子代理")),
  });

  const deleteMutation = useMutation({
    mutationFn: (subagent: SubagentItem) => deleteSubagent(subagent.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("子代理已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "子代理"));
    },
  });

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (subagent: SubagentItem) => {
    setEditing(subagent);
    setEditorOpen(true);
  };

  return (
    <LibraryPage
      title="子代理"
      heading="子代理"
      description="定义可被主 Agent 调用的专用子代理、提示词与可用资源。"
      count={subagents.length}
      countUnit="个子代理"
      refreshing={subagentsQuery.isFetching}
      onRefresh={() => void subagentsQuery.refetch()}
      actions={
        canCreate ? (
          <Button type="button" size="sm" onClick={openCreate}>
            <Plus />
            新建子代理
          </Button>
        ) : null
      }
    >
      <LibraryTable
        loading={subagentsQuery.isPending}
        error={subagentsQuery.error}
        isEmpty={subagents.length === 0}
        emptyMessage="还没有子代理。创建后可将专用任务交给子代理处理。"
        errorTitle="无法加载子代理"
        resolveError={(error) => resolveLibraryApiError(error, "子代理")}
        onRetry={() => void subagentsQuery.refetch()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>提示词</TableHead>
              <TableHead>路由 / 模型</TableHead>
              <TableHead>关联 Agent</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {subagents.map((subagent) => {
              const enabled = subagent.spec.enabled !== false;
              const toggling =
                toggleMutation.isPending &&
                toggleMutation.variables?.metadata.name === subagent.metadata.name;
              return (
                <TableRow key={subagent.metadata.name}>
                  <TableCell>
                    <div className="space-y-0.5">
                      <p className="font-medium">{subagentDisplayName(subagent)}</p>
                      {subagent.spec.description ? (
                        <p className="max-w-56 truncate text-[11px] text-muted-foreground">
                          {subagent.spec.description}
                        </p>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell>
                    <span className="block max-w-64 truncate text-xs text-muted-foreground">
                      {subagentPromptExcerpt(subagent)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="block max-w-48 truncate font-mono text-xs text-muted-foreground">
                      {subagentTargetLabel(subagent)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <AgentBadges agents={subagent.spec.agents} />
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
                          onCheckedChange={() => toggleMutation.mutate(subagent)}
                          aria-label={enabled ? "停用子代理" : "启用子代理"}
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
                      onEdit={() => openEdit(subagent)}
                      onDelete={() => setDeleteTarget(subagent)}
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </LibraryTable>

      {editorOpen ? (
        <SubagentDialog
          subagent={editing ?? undefined}
          onClose={() => {
            setEditorOpen(false);
            setEditing(null);
          }}
        />
      ) : null}

      <LibraryDeleteDialog
        resourceLabel="子代理"
        name={deleteTarget ? subagentDisplayName(deleteTarget) : null}
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
