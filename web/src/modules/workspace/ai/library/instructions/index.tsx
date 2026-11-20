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
import { InstructionDialog } from "./components/instruction-dialog";
import {
  INSTRUCTIONS_QUERY_KEY,
  type InstructionItem,
  deleteInstruction,
  fetchInstructions,
  updateInstruction,
} from "./lib/api";
import {
  INSTRUCTION_SCOPE_LABELS,
  instructionContentExcerpt,
  instructionDisplayName,
} from "./lib/model";

export function AiInstructionsView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canToggle = allowedOperations.has("toggle");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<InstructionItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<InstructionItem | null>(null);

  const instructionsQuery = useQuery({
    queryKey: INSTRUCTIONS_QUERY_KEY,
    queryFn: fetchInstructions,
    retry: false,
  });
  const instructions = useMemo(
    () => sortBy(instructionsQuery.data ?? [], instructionDisplayName),
    [instructionsQuery.data],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: INSTRUCTIONS_QUERY_KEY });

  const toggleMutation = useMutation({
    mutationFn: (instruction: InstructionItem) =>
      updateInstruction({
        ...instruction,
        spec: { ...instruction.spec, enabled: instruction.spec.enabled === false },
      }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "指令")),
  });

  const deleteMutation = useMutation({
    mutationFn: (instruction: InstructionItem) => deleteInstruction(instruction.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("指令已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "指令"));
    },
  });

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (instruction: InstructionItem) => {
    setEditing(instruction);
    setEditorOpen(true);
  };

  return (
    <LibraryPage
      title="指令"
      heading="指令"
      description="维护注入 Agent 系统提示词的指令，可全局共享或按 Agent 下发。"
      count={instructions.length}
      countUnit="条指令"
      refreshing={instructionsQuery.isFetching}
      onRefresh={() => void instructionsQuery.refetch()}
      actions={
        canCreate ? (
          <Button type="button" size="sm" onClick={openCreate}>
            <Plus />
            新建指令
          </Button>
        ) : null
      }
    >
      <LibraryTable
        loading={instructionsQuery.isPending}
        error={instructionsQuery.error}
        isEmpty={instructions.length === 0}
        emptyMessage="还没有指令。创建共享指令或按 Agent 绑定的指令。"
        errorTitle="无法加载指令"
        resolveError={(error) => resolveLibraryApiError(error, "指令")}
        onRetry={() => void instructionsQuery.refetch()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>范围</TableHead>
              <TableHead>内容摘要</TableHead>
              <TableHead>关联 Agent</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {instructions.map((instruction) => {
              const enabled = instruction.spec.enabled !== false;
              const shared = instruction.spec.scope === "SHARED";
              const toggling =
                toggleMutation.isPending &&
                toggleMutation.variables?.metadata.name === instruction.metadata.name;
              return (
                <TableRow key={instruction.metadata.name}>
                  <TableCell>
                    <p className="font-medium">{instructionDisplayName(instruction)}</p>
                  </TableCell>
                  <TableCell>
                    <Badge variant={shared ? "secondary" : "outline"}>
                      {INSTRUCTION_SCOPE_LABELS[instruction.spec.scope]}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <span className="block max-w-64 truncate text-xs text-muted-foreground">
                      {instructionContentExcerpt(instruction)}
                    </span>
                  </TableCell>
                  <TableCell>
                    {shared ? (
                      <span className="text-xs text-muted-foreground">全部 Agent</span>
                    ) : (
                      <AgentBadges agents={instruction.spec.agents} />
                    )}
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
                          onCheckedChange={() => toggleMutation.mutate(instruction)}
                          aria-label={enabled ? "停用指令" : "启用指令"}
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
                      onEdit={() => openEdit(instruction)}
                      onDelete={() => setDeleteTarget(instruction)}
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </LibraryTable>

      {editorOpen ? (
        <InstructionDialog
          instruction={editing ?? undefined}
          onClose={() => {
            setEditorOpen(false);
            setEditing(null);
          }}
        />
      ) : null}

      <LibraryDeleteDialog
        resourceLabel="指令"
        name={deleteTarget ? instructionDisplayName(deleteTarget) : null}
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
