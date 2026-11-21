import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, CircleArrowUp, Download, History, Loader2, Plus } from "lucide-react";
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
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { AgentBadges } from "../components/agent-badges";
import { LibraryDeleteDialog } from "../components/library-delete-dialog";
import { LibraryPage } from "../components/library-page";
import { LibraryTable } from "../components/library-table";
import { ResourceRowActions } from "../components/resource-row-actions";
import { resolveLibraryApiError } from "../lib/api";
import { sortBy } from "../lib/model";
import { SkillDialog } from "./components/skill-dialog";
import { SkillGovernanceDialog } from "./components/skill-governance-dialog";
import { SkillImportDialog } from "./components/skill-import-dialog";
import {
  SKILLS_QUERY_KEY,
  SKILL_AUDIT_QUERY_KEY,
  SKILL_USAGE_QUERY_KEY,
  type SkillDraft,
  type SkillItem,
  archiveSkill,
  deleteSkill,
  fetchSkills,
  patchSkill,
  promoteSkill,
} from "./lib/api";
import {
  SKILL_SOURCE_LABELS,
  SKILL_STATUS_LABELS,
  skillDisplayName,
  skillStatus,
  skillStatusBadgeVariant,
} from "./lib/model";

export function AiSkillsView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canToggle = allowedOperations.has("toggle");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SkillItem | null>(null);
  const [draft, setDraft] = useState<SkillDraft | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<SkillItem | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<SkillItem | null>(null);
  const [governanceOpen, setGovernanceOpen] = useState(false);
  const [preview, setPreview] = useState<ContentPreview | null>(null);

  const skillsQuery = useQuery({
    queryKey: SKILLS_QUERY_KEY,
    queryFn: fetchSkills,
    retry: false,
  });
  const skills = useMemo(
    () => sortBy(skillsQuery.data ?? [], skillDisplayName),
    [skillsQuery.data],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: SKILLS_QUERY_KEY });

  const toggleMutation = useMutation({
    mutationFn: (skill: SkillItem) =>
      patchSkill(skill.metadata.name, [
        { op: "add", path: "/spec/enabled", value: skill.spec.enabled === false },
      ]),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "技能")),
  });

  const invalidateGovernance = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: SKILL_USAGE_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: SKILL_AUDIT_QUERY_KEY }),
    ]);

  const promoteMutation = useMutation({
    mutationFn: (skill: SkillItem) => promoteSkill(skill.metadata.name),
    onSuccess: async (result) => {
      await invalidate();
      await invalidateGovernance();
      if (result.status === "INVALID") {
        toast.error(result.message || "技能发布失败。");
        return;
      }
      toast.success(result.message || "技能已发布。");
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "技能")),
  });

  const archiveMutation = useMutation({
    mutationFn: (skill: SkillItem) => archiveSkill(skill.metadata.name),
    onSuccess: async (result) => {
      await invalidate();
      await invalidateGovernance();
      setArchiveTarget(null);
      if (result.status === "INVALID") {
        toast.error(result.message || "技能归档失败。");
        return;
      }
      toast.success(result.message || "技能已归档。");
    },
    onError: (error) => {
      setArchiveTarget(null);
      toast.error(resolveLibraryApiError(error, "技能"));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (skill: SkillItem) => deleteSkill(skill.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("技能已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "技能"));
    },
  });

  const openCreate = () => {
    setEditing(null);
    setDraft(null);
    setEditorOpen(true);
  };

  const openEdit = (skill: SkillItem) => {
    setEditing(skill);
    setDraft(null);
    setEditorOpen(true);
  };

  const closeEditor = () => {
    setEditorOpen(false);
    setEditing(null);
    setDraft(null);
  };

  return (
    <LibraryPage
      title="技能"
      heading="技能"
      description="维护 SKILL.md 指令包，并绑定到需要这些能力的 Agent。"
      count={skills.length}
      countUnit="个技能"
      refreshing={skillsQuery.isFetching}
      onRefresh={() => void skillsQuery.refetch()}
      actions={
        <>
          {canUpdate ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setGovernanceOpen(true)}
            >
              <History />
              治理记录
            </Button>
          ) : null}
          {canCreate ? (
            <>
              <Button type="button" variant="outline" size="sm" onClick={() => setImportOpen(true)}>
                <Download />从 URL 导入
              </Button>
              <Button type="button" size="sm" onClick={openCreate}>
                <Plus />
                新建技能
              </Button>
            </>
          ) : null}
        </>
      }
    >
      <LibraryTable
        loading={skillsQuery.isPending}
        error={skillsQuery.error}
        isEmpty={skills.length === 0}
        emptyMessage="还没有技能。创建内联技能或从 URL 导入 SKILL.md。"
        errorTitle="无法加载技能"
        resolveError={(error) => resolveLibraryApiError(error, "技能")}
        onRetry={() => void skillsQuery.refetch()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>来源</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>关联 Agent</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {skills.map((skill) => {
              const enabled = skill.spec.enabled !== false;
              const status = skillStatus(skill);
              const toggling =
                toggleMutation.isPending &&
                toggleMutation.variables?.metadata.name === skill.metadata.name;
              const promoting =
                promoteMutation.isPending &&
                promoteMutation.variables?.metadata.name === skill.metadata.name;
              const archiving =
                archiveMutation.isPending &&
                archiveMutation.variables?.metadata.name === skill.metadata.name;
              return (
                <TableRow key={skill.metadata.name}>
                  <TableCell>
                    <div className="space-y-0.5">
                      <p className="font-medium">{skillDisplayName(skill)}</p>
                      {skill.spec.description ? (
                        <p className="max-w-64 truncate text-[11px] text-muted-foreground">
                          {skill.spec.description}
                        </p>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{SKILL_SOURCE_LABELS[skill.spec.source]}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={skillStatusBadgeVariant(status)}>
                      {SKILL_STATUS_LABELS[status]}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <AgentBadges agents={skill.spec.agents} />
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
                          onCheckedChange={() => toggleMutation.mutate(skill)}
                          aria-label={enabled ? "停用技能" : "启用技能"}
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
                      onEdit={() => openEdit(skill)}
                      onDelete={() => setDeleteTarget(skill)}
                    >
                      <ContentPreviewButton
                        label="预览技能"
                        onClick={() =>
                          setPreview({
                            title: skillDisplayName(skill),
                            description: `${SKILL_SOURCE_LABELS[skill.spec.source]} · ${
                              enabled ? "已启用" : "已停用"
                            }`,
                            content:
                              skill.spec.content || "该技能来自远端来源，没有内联 SKILL.md 内容。",
                          })
                        }
                      />
                      {canUpdate && status === "DRAFT" ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-sm"
                          aria-label="发布技能"
                          title="发布技能"
                          disabled={promoting}
                          onClick={() => promoteMutation.mutate(skill)}
                        >
                          {promoting ? <Loader2 className="animate-spin" /> : <CircleArrowUp />}
                        </Button>
                      ) : null}
                      {canUpdate && status !== "ARCHIVED" ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-sm"
                          aria-label="归档技能"
                          title="归档技能"
                          disabled={archiving}
                          onClick={() => setArchiveTarget(skill)}
                        >
                          {archiving ? <Loader2 className="animate-spin" /> : <Archive />}
                        </Button>
                      ) : null}
                    </ResourceRowActions>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </LibraryTable>

      {importOpen ? (
        <SkillImportDialog
          onClose={() => setImportOpen(false)}
          onImported={(nextDraft) => {
            setImportOpen(false);
            setEditing(null);
            setDraft(nextDraft);
            setEditorOpen(true);
          }}
        />
      ) : null}

      {editorOpen ? (
        <SkillDialog
          skill={editing ?? undefined}
          draft={editing ? undefined : (draft ?? undefined)}
          onClose={closeEditor}
        />
      ) : null}

      <ContentPreviewDialog preview={preview} onClose={() => setPreview(null)} />

      {governanceOpen ? <SkillGovernanceDialog onClose={() => setGovernanceOpen(false)} /> : null}

      <ConfirmDialog
        open={archiveTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setArchiveTarget(null);
          }
        }}
        title={`归档技能 ${archiveTarget ? skillDisplayName(archiveTarget) : ""}`}
        desc="归档后技能会被停用并标记为已归档，已绑定的 Agent 将不再使用该技能。"
        confirmText="归档技能"
        destructive
        isLoading={archiveMutation.isPending}
        handleConfirm={() => {
          if (archiveTarget) {
            archiveMutation.mutate(archiveTarget);
          }
        }}
      />

      <LibraryDeleteDialog
        resourceLabel="技能"
        name={deleteTarget ? skillDisplayName(deleteTarget) : null}
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
