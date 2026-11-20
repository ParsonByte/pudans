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
import { EnvironmentVariableDialog } from "./components/environment-variable-dialog";
import {
  ENVIRONMENT_VARIABLES_QUERY_KEY,
  type EnvironmentVariableItem,
  deleteEnvironmentVariable,
  fetchEnvironmentVariables,
  updateEnvironmentVariable,
} from "./lib/api";
import { environmentVariableConfigured, environmentVariableDisplayName } from "./lib/model";

export function AiEnvironmentVariablesView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canToggle = allowedOperations.has("toggle");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<EnvironmentVariableItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<EnvironmentVariableItem | null>(null);

  const variablesQuery = useQuery({
    queryKey: ENVIRONMENT_VARIABLES_QUERY_KEY,
    queryFn: fetchEnvironmentVariables,
    retry: false,
  });
  const variables = useMemo(
    () => sortBy(variablesQuery.data ?? [], environmentVariableDisplayName),
    [variablesQuery.data],
  );

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ENVIRONMENT_VARIABLES_QUERY_KEY });

  const toggleMutation = useMutation({
    mutationFn: (variable: EnvironmentVariableItem) =>
      updateEnvironmentVariable({
        ...variable,
        spec: { ...variable.spec, enabled: variable.spec.enabled === false },
      }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "环境变量")),
  });

  const deleteMutation = useMutation({
    mutationFn: (variable: EnvironmentVariableItem) =>
      deleteEnvironmentVariable(variable.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("环境变量已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "环境变量"));
    },
  });

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (variable: EnvironmentVariableItem) => {
    setEditing(variable);
    setEditorOpen(true);
  };

  return (
    <LibraryPage
      title="环境变量"
      heading="环境变量"
      description="为 Agent 注入运行时环境变量；值只写不读，保存后不再展示。"
      count={variables.length}
      countUnit="个环境变量"
      refreshing={variablesQuery.isFetching}
      onRefresh={() => void variablesQuery.refetch()}
      actions={
        canCreate ? (
          <Button type="button" size="sm" onClick={openCreate}>
            <Plus />
            新建环境变量
          </Button>
        ) : null
      }
    >
      <LibraryTable
        loading={variablesQuery.isPending}
        error={variablesQuery.error}
        isEmpty={variables.length === 0}
        emptyMessage="还没有环境变量。创建后可按 Agent 注入运行时配置。"
        errorTitle="无法加载环境变量"
        resolveError={(error) => resolveLibraryApiError(error, "环境变量")}
        onRetry={() => void variablesQuery.refetch()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>说明</TableHead>
              <TableHead>是否已配置</TableHead>
              <TableHead>关联 Agent</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {variables.map((variable) => {
              const enabled = variable.spec.enabled !== false;
              const configured = environmentVariableConfigured(variable);
              const toggling =
                toggleMutation.isPending &&
                toggleMutation.variables?.metadata.name === variable.metadata.name;
              return (
                <TableRow key={variable.metadata.name}>
                  <TableCell>
                    <span className="font-mono text-xs font-medium">
                      {environmentVariableDisplayName(variable)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="block max-w-64 truncate text-xs text-muted-foreground">
                      {variable.spec.description || "—"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge variant={configured ? "secondary" : "outline"}>
                      {configured ? "已配置" : "未配置"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <AgentBadges agents={variable.spec.agents} />
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
                          onCheckedChange={() => toggleMutation.mutate(variable)}
                          aria-label={enabled ? "停用环境变量" : "启用环境变量"}
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
                      onEdit={() => openEdit(variable)}
                      onDelete={() => setDeleteTarget(variable)}
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </LibraryTable>

      {editorOpen ? (
        <EnvironmentVariableDialog
          variable={editing ?? undefined}
          onClose={() => {
            setEditorOpen(false);
            setEditing(null);
          }}
        />
      ) : null}

      <LibraryDeleteDialog
        resourceLabel="环境变量"
        name={deleteTarget ? environmentVariableDisplayName(deleteTarget) : null}
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
