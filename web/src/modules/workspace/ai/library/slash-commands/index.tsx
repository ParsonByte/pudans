import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus } from "lucide-react";
import { toast } from "sonner";

import { ContentPreviewButton } from "@/components/content/content-preview";
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
import { SlashCommandDialog } from "./components/slash-command-dialog";
import { SlashCommandPreviewDialog } from "./components/slash-command-preview-dialog";
import {
  SLASH_COMMANDS_QUERY_KEY,
  type SlashCommandItem,
  deleteSlashCommand,
  fetchSlashCommands,
  patchSlashCommand,
} from "./lib/api";
import { slashCommandArgumentHint, slashCommandDisplayName, slashCommandLabel } from "./lib/model";

export function AiSlashCommandsView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canToggle = allowedOperations.has("toggle");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SlashCommandItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SlashCommandItem | null>(null);
  const [preview, setPreview] = useState<SlashCommandItem | null>(null);

  const commandsQuery = useQuery({
    queryKey: SLASH_COMMANDS_QUERY_KEY,
    queryFn: fetchSlashCommands,
    retry: false,
  });
  const commands = useMemo(
    () => sortBy(commandsQuery.data ?? [], slashCommandDisplayName),
    [commandsQuery.data],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: SLASH_COMMANDS_QUERY_KEY });

  const toggleMutation = useMutation({
    mutationFn: (command: SlashCommandItem) =>
      patchSlashCommand(command.metadata.name, [
        { op: "add", path: "/spec/enabled", value: command.spec.enabled === false },
      ]),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "斜杠命令")),
  });

  const deleteMutation = useMutation({
    mutationFn: (command: SlashCommandItem) => deleteSlashCommand(command.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("斜杠命令已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "斜杠命令"));
    },
  });

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (command: SlashCommandItem) => {
    setEditing(command);
    setEditorOpen(true);
  };

  return (
    <LibraryPage
      title="斜杠命令"
      heading="斜杠命令"
      description="定义聊天中输入 / 即可调用的提示词模板，支持参数展开。"
      count={commands.length}
      countUnit="个斜杠命令"
      refreshing={commandsQuery.isFetching}
      onRefresh={() => void commandsQuery.refetch()}
      actions={
        canCreate ? (
          <Button type="button" size="sm" onClick={openCreate}>
            <Plus />
            新建斜杠命令
          </Button>
        ) : null
      }
    >
      <LibraryTable
        loading={commandsQuery.isPending}
        error={commandsQuery.error}
        isEmpty={commands.length === 0}
        emptyMessage="还没有斜杠命令。创建后可在聊天输入框中直接调用。"
        errorTitle="无法加载斜杠命令"
        resolveError={(error) => resolveLibraryApiError(error, "斜杠命令")}
        onRetry={() => void commandsQuery.refetch()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>命令</TableHead>
              <TableHead>说明</TableHead>
              <TableHead>参数提示</TableHead>
              <TableHead>关联 Agent</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {commands.map((command) => {
              const enabled = command.spec.enabled !== false;
              const toggling =
                toggleMutation.isPending &&
                toggleMutation.variables?.metadata.name === command.metadata.name;
              return (
                <TableRow key={command.metadata.name}>
                  <TableCell>
                    <span className="font-mono text-xs font-medium">
                      {slashCommandLabel(command)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="block max-w-64 truncate text-xs text-muted-foreground">
                      {command.spec.description || "—"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="block max-w-48 truncate text-xs text-muted-foreground">
                      {slashCommandArgumentHint(command)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <AgentBadges agents={command.spec.agents} />
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
                          onCheckedChange={() => toggleMutation.mutate(command)}
                          aria-label={enabled ? "停用斜杠命令" : "启用斜杠命令"}
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
                      onEdit={() => openEdit(command)}
                      onDelete={() => setDeleteTarget(command)}
                    >
                      <ContentPreviewButton
                        label="预览斜杠命令"
                        onClick={() => setPreview(command)}
                      />
                    </ResourceRowActions>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </LibraryTable>

      {editorOpen ? (
        <SlashCommandDialog
          command={editing ?? undefined}
          onClose={() => {
            setEditorOpen(false);
            setEditing(null);
          }}
        />
      ) : null}

      {preview ? (
        <SlashCommandPreviewDialog command={preview} onClose={() => setPreview(null)} />
      ) : null}

      <LibraryDeleteDialog
        resourceLabel="斜杠命令"
        name={deleteTarget ? slashCommandLabel(deleteTarget) : null}
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
