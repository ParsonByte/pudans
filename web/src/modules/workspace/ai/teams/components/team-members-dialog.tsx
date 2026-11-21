import { useState } from "react";
import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Trash2, UserPlus, Users } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { FormField } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { resolveLibraryApiError } from "@/modules/workspace/ai/library/lib/api";

import {
  type AiTeamItem,
  type AiTeamMember,
  type AiTeamRole,
  addAiTeamMember,
  aiTeamQueryKey,
  fetchAiTeam,
  removeAiTeamMember,
} from "../lib/api";
import { TEAM_ROLE_OPTIONS, memberStatusLabel, teamDisplayName, teamRoleLabel } from "../lib/model";

const TEAM_ROLE_VALUES = TEAM_ROLE_OPTIONS.map((option) => option.value) as [
  AiTeamRole,
  ...AiTeamRole[],
];

const memberSchema = z.object({
  userId: z.string().trim().min(1, "请输入用户 UID。").max(128, "用户 UID 过长。"),
  role: z.enum(TEAM_ROLE_VALUES),
});

type MemberFormValues = z.infer<typeof memberSchema>;

type TeamMembersDialogProps = {
  team: AiTeamItem;
  canManage: boolean;
  onClose: () => void;
};

export function TeamMembersDialog({ team, canManage, onClose }: TeamMembersDialogProps) {
  const queryClient = useQueryClient();
  const [removeTarget, setRemoveTarget] = useState<AiTeamMember | null>(null);
  const teamQuery = useQuery({
    queryKey: aiTeamQueryKey(team.metadata.name),
    queryFn: () => fetchAiTeam(team.metadata.name),
    initialData: team,
    retry: false,
  });
  const current = teamQuery.data ?? team;
  const members = current.spec.members ?? [];

  const form = useForm<MemberFormValues>({
    resolver: zodResolver(memberSchema) as Resolver<MemberFormValues>,
    defaultValues: { userId: "", role: "MEMBER" },
  });

  const syncTeam = async (updated: AiTeamItem) => {
    queryClient.setQueryData(aiTeamQueryKey(updated.metadata.name), updated);
    await queryClient.invalidateQueries({ queryKey: ["ai-teams"] });
  };

  const addMutation = useMutation({
    mutationFn: (values: MemberFormValues) => addAiTeamMember(team.metadata.name, values),
    onSuccess: async (updated) => {
      await syncTeam(updated);
      form.reset();
      toast.success("团队成员已添加。");
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "团队成员")),
  });

  const removeMutation = useMutation({
    mutationFn: (member: AiTeamMember) => removeAiTeamMember(team.metadata.name, member.userId),
    onSuccess: async (updated) => {
      await syncTeam(updated);
      setRemoveTarget(null);
      toast.success("团队成员已移除。");
    },
    onError: (error) => {
      setRemoveTarget(null);
      toast.error(resolveLibraryApiError(error, "团队成员"));
    },
  });

  const onAdd = form.handleSubmit(
    (values) => addMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <>
      <Dialog
        open
        onOpenChange={(open) => {
          if (!open) {
            onClose();
          }
        }}
      >
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <div className="flex items-start gap-3 pe-8">
              <span className="flex size-10 shrink-0 items-center justify-center bg-muted">
                <Users className="size-5" />
              </span>
              <div className="space-y-1">
                <DialogTitle>{teamDisplayName(current)} · 成员</DialogTitle>
                <DialogDescription>
                  成员通过团队成员接口即时增删；移除后历史记录保留但不再获得团队访问权。
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>

          {canManage ? (
            <form onSubmit={onAdd} className="space-y-4 border p-4" noValidate>
              <div className="grid gap-4 sm:grid-cols-[1fr_160px_auto] sm:items-end">
                <FormField
                  label="用户 UID"
                  htmlFor="team-member-user"
                  error={form.formState.errors.userId?.message}
                >
                  <Input
                    id="team-member-user"
                    placeholder="输入已存在用户的 UID"
                    {...form.register("userId")}
                  />
                </FormField>
                <FormField
                  label="角色"
                  htmlFor="team-member-role"
                  error={form.formState.errors.role?.message}
                >
                  <Controller
                    control={form.control}
                    name="role"
                    render={({ field }) => (
                      <Select
                        value={field.value}
                        onValueChange={(value) => field.onChange(value as AiTeamRole)}
                      >
                        <SelectTrigger id="team-member-role" className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {TEAM_ROLE_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </FormField>
                <Button type="submit" disabled={addMutation.isPending}>
                  {addMutation.isPending ? <Loader2 className="animate-spin" /> : <UserPlus />}
                  添加成员
                </Button>
              </div>
            </form>
          ) : (
            <div className="border bg-muted px-4 py-3 text-xs text-muted-foreground">
              当前账号只能查看团队成员，调整成员需要更新权限。
            </div>
          )}

          {members.length === 0 ? (
            <div className="flex min-h-24 items-center justify-center border text-sm text-muted-foreground">
              该团队还没有成员。
            </div>
          ) : (
            <div className="border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>用户 UID</TableHead>
                    <TableHead>角色</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {members.map((member) => {
                    const active = member.status !== "REMOVED";
                    return (
                      <TableRow key={member.userId}>
                        <TableCell className="font-mono text-xs">{member.userId}</TableCell>
                        <TableCell>
                          <Badge variant={member.role === "ADMIN" ? "secondary" : "outline"}>
                            {teamRoleLabel(member.role)}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={active ? "secondary" : "outline"}>
                            {memberStatusLabel(member.status)}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          {canManage && active ? (
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              aria-label="移除成员"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => setRemoveTarget(member)}
                            >
                              <Trash2 />
                            </Button>
                          ) : (
                            <span className="text-xs text-muted-foreground">—</span>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={Boolean(removeTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setRemoveTarget(null);
          }
        }}
        title="移除团队成员"
        desc={
          <>
            确定将用户「{removeTarget?.userId}」从团队「{teamDisplayName(current)}
            」中移除？该用户将立即失去 该团队的访问权限。
          </>
        }
        destructive
        confirmText="移除"
        isLoading={removeMutation.isPending}
        handleConfirm={() => {
          if (removeTarget) {
            removeMutation.mutate(removeTarget);
          }
        }}
      />
    </>
  );
}
