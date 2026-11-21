import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus, RefreshCw, Users } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import {
  type ContentPreview,
  ContentPreviewButton,
  ContentPreviewDialog,
} from "@/components/content/content-preview";
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
import { ResourceRowActions } from "@/modules/workspace/ai/library/components/resource-row-actions";
import { useAgentLabels } from "@/modules/workspace/ai/library/lib/agents";
import { resolveLibraryApiError } from "@/modules/workspace/ai/library/lib/api";
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

import { TeamDialog } from "./components/team-dialog";
import { TeamMembersDialog } from "./components/team-members-dialog";
import {
  AI_TEAMS_QUERY_KEY,
  type AiTeamItem,
  deleteAiTeam,
  fetchAiTeams,
  patchAiTeam,
} from "./lib/api";
import {
  isBuiltinTeam,
  teamDisplayName,
  teamMembersSummary,
  teamPreview,
  teamQuotaLabel,
  teamStatus,
  teamStatusLabel,
} from "./lib/model";

const PAGE_TITLE = "AI 团队";

export function AiTeamsView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const agentLabels = useAgentLabels();
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<AiTeamItem | null>(null);
  const [membersTarget, setMembersTarget] = useState<AiTeamItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AiTeamItem | null>(null);
  const [preview, setPreview] = useState<ContentPreview | null>(null);

  const teamsQuery = useQuery({
    queryKey: AI_TEAMS_QUERY_KEY,
    queryFn: fetchAiTeams,
    retry: false,
  });
  const teams = teamsQuery.data ?? [];

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["ai-teams"] });

  const statusMutation = useMutation({
    mutationFn: (team: AiTeamItem) =>
      patchAiTeam(team.metadata.name, [
        {
          op: "add",
          path: "/spec/status",
          value: teamStatus(team) === "ACTIVE" ? "ARCHIVED" : "ACTIVE",
        },
      ]),
    onSuccess: async (updated) => {
      await invalidate();
      toast.success(`团队已${teamStatus(updated) === "ACTIVE" ? "恢复" : "归档"}。`);
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "团队")),
  });

  const deleteMutation = useMutation({
    mutationFn: (team: AiTeamItem) => deleteAiTeam(team.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("团队已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "团队"));
    },
  });

  const refresh = () => {
    void teamsQuery.refetch();
  };

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (team: AiTeamItem) => {
    setEditing(team);
    setEditorOpen(true);
  };

  const closeEditor = () => {
    setEditorOpen(false);
    setEditing(null);
  };

  if (!canView) {
    return (
      <ConsolePage title={PAGE_TITLE}>
        <ConsoleState variant="permission" title="没有查看 AI 团队的权限。" wrapper />
      </ConsolePage>
    );
  }

  return (
    <ConsolePage
      title={PAGE_TITLE}
      description="团队是 AI 资源的作用域：决定成员可以访问哪些 Agent、对话模式与知识库，并可设置使用配额。"
      actions={
        <>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={refresh}
            disabled={teamsQuery.isFetching}
          >
            {teamsQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
          {canCreate ? (
            <Button type="button" size="sm" onClick={openCreate}>
              <Plus />
              新建团队
            </Button>
          ) : null}
        </>
      }
    >
      {teamsQuery.isPending ? (
        <ConsoleSection className="overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>团队</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>负责人</TableHead>
                <TableHead>默认 Agent</TableHead>
                <TableHead>配额</TableHead>
                <TableHead>成员</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <ConsoleTableSkeleton columns={7} />
            </TableBody>
          </Table>
        </ConsoleSection>
      ) : teamsQuery.isError ? (
        <ConsoleState
          variant="error"
          title="无法加载团队"
          description={resolveLibraryApiError(teamsQuery.error, "团队")}
          onRetry={refresh}
          wrapper
        />
      ) : teams.length === 0 ? (
        <ConsoleState
          variant="empty"
          title="还没有团队"
          description="创建团队后，即可把 Agent 与知识库等 AI 资源分配给团队成员。"
          wrapper
          action={
            canCreate ? (
              <Button type="button" size="sm" onClick={openCreate}>
                <Plus />
                新建第一个团队
              </Button>
            ) : null
          }
        />
      ) : (
        <ConsoleSection className="overflow-hidden">
          <ConsoleSectionHeader
            title="团队列表"
            description="删除默认团队会被系统拒绝；归档团队会立即停止成员访问。"
            action={<span className="text-xs text-muted-foreground">{teams.length} 个团队</span>}
          />
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>团队</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>负责人</TableHead>
                <TableHead>默认 Agent</TableHead>
                <TableHead>配额</TableHead>
                <TableHead>成员</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {teams.map((team) => {
                const status = teamStatus(team);
                const active = status === "ACTIVE";
                const toggling =
                  statusMutation.isPending &&
                  statusMutation.variables?.metadata.name === team.metadata.name;
                const agentName = team.spec.defaultAgent
                  ? (agentLabels.get(team.spec.defaultAgent) ?? team.spec.defaultAgent)
                  : "未设置";
                return (
                  <TableRow key={team.metadata.name}>
                    <TableCell>
                      <p className="font-medium">{teamDisplayName(team)}</p>
                      <span className="font-mono text-[11px] text-muted-foreground">
                        {team.metadata.name}
                      </span>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Badge variant={active ? "secondary" : "outline"}>
                          {teamStatusLabel(status)}
                        </Badge>
                        {canUpdate ? (
                          <Switch
                            checked={active}
                            disabled={toggling}
                            onCheckedChange={() => statusMutation.mutate(team)}
                            aria-label={active ? "归档团队" : "恢复团队"}
                          />
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell className="font-mono text-xs">{team.spec.ownerUserId}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{agentName}</TableCell>
                    <TableCell>
                      <span className="block max-w-44 truncate text-xs text-muted-foreground">
                        {teamQuotaLabel(team.spec.quota)}
                      </span>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {teamMembersSummary(team)}
                    </TableCell>
                    <TableCell className="text-right">
                      <ResourceRowActions
                        canUpdate={canUpdate}
                        canDelete={canDelete && !isBuiltinTeam(team.metadata.name)}
                        onEdit={() => openEdit(team)}
                        onDelete={() => setDeleteTarget(team)}
                      >
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-sm"
                          aria-label="成员"
                          onClick={() => setMembersTarget(team)}
                        >
                          <Users />
                        </Button>
                        <ContentPreviewButton
                          label="预览团队"
                          onClick={() => setPreview(teamPreview(team))}
                        />
                      </ResourceRowActions>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </ConsoleSection>
      )}

      {editorOpen ? (
        <TeamDialog team={editing ?? undefined} teams={teams} onClose={closeEditor} />
      ) : null}

      {membersTarget ? (
        <TeamMembersDialog
          team={membersTarget}
          canManage={canUpdate}
          onClose={() => setMembersTarget(null)}
        />
      ) : null}

      <ContentPreviewDialog preview={preview} onClose={() => setPreview(null)} />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除团队"
        desc={
          <>
            确定删除「
            {deleteTarget ? teamDisplayName(deleteTarget) : ""}
            」？团队删除后无法恢复，关联 Agent 的团队访问校验会立即失效。
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
