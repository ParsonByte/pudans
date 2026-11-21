import { useMemo, useState } from "react";

import { type Role, type UserGroupBinding, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Lock,
  MoreHorizontal,
  Pencil,
  Plus,
  RefreshCw,
  ShieldCheck,
  Trash2,
  UsersRound,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { MarkdownContent } from "@/components/content/markdown";
import { DataTablePagination } from "@/components/data-table";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  isBuiltinRole,
  roleDescription,
  roleDisplayName,
} from "@/modules/workspace/console/roles/lib/role-model";
import { useRoles } from "@/modules/workspace/console/roles/lib/use-roles";
import { UserAvatar } from "@/modules/workspace/console/users/components/user-avatar";
import { type UserListItem, fetchAllUsers } from "@/modules/workspace/console/users/lib/api";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";
import { USER_GROUPS_QUERY_KEY } from "@/modules/workspace/lib/user-groups";

import { EditUserGroupDialog } from "./components/edit-user-group-dialog";
import { UserGroupRoleBindingDialog } from "./components/role-binding-dialog";
import {
  deleteGroupRoleBinding,
  fetchGroupRoleBindings,
  fetchMembershipBindings,
  resolveUserGroupApiError,
  resolveUserGroupBindingApiError,
} from "./lib/api";
import {
  isBuiltinUserGroup,
  isRoleAdjustableBuiltinGroup,
  isSuperAdminsGroup,
  membershipBindingUsernames,
  roleBindingNames,
  userGroupDisplayName,
  userGroupRoleNames,
} from "./lib/user-group-model";

type UserGroupDetailViewProps = {
  name: string;
};

type MemberRow = {
  name: string;
  displayName: string;
  disabled?: boolean;
  registeredAt?: string;
  hasProfile: boolean;
};

export function UserGroupDetailView({ name }: UserGroupDetailViewProps) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const allowedOperations = useAllowedOperations();
  const groupsPath = useMenuPath("user-groups");
  const usersPath = useMenuPath("users");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const [editOpen, setEditOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [roleBindingForm, setRoleBindingForm] = useState<{ binding?: UserGroupBinding } | null>(
    null,
  );
  const [roleBindingDeleteTarget, setRoleBindingDeleteTarget] = useState<UserGroupBinding | null>(
    null,
  );
  const [memberPage, setMemberPage] = useState(1);
  const [memberSize, setMemberSize] = useState(10);

  const groupQuery = useQuery({
    queryKey: ["user-group", "detail", name],
    queryFn: async () => (await apiClient.userGroup.getUserGroup({ name })).data,
    retry: false,
  });
  const membershipQuery = useQuery({
    queryKey: ["user-group", name, "membership-bindings"],
    queryFn: () => fetchMembershipBindings(name),
    retry: false,
  });
  const roleBindingsQuery = useQuery({
    queryKey: ["user-group", name, "role-bindings"],
    queryFn: () => fetchGroupRoleBindings(name),
    retry: false,
  });
  const usersQuery = useQuery({
    queryKey: ["users", "user-group", name, "members"],
    queryFn: () => fetchAllUsers({ userGroup: name }),
    retry: false,
    enabled: groupQuery.isSuccess,
  });
  const rolesQuery = useRoles();

  const group = groupQuery.data;
  const builtin = group ? isBuiltinUserGroup(group) : false;
  const superAdmins = group ? isSuperAdminsGroup(group) : false;
  const roleAdjustable = group ? isRoleAdjustableBuiltinGroup(group) : false;
  const displayName = group ? userGroupDisplayName(group) : name;
  const memberProfiles = useMemo(() => {
    const profiles = new Map<string, UserListItem>();
    for (const user of usersQuery.data ?? []) {
      if (user.name) {
        profiles.set(user.name, user);
      }
    }
    return profiles;
  }, [usersQuery.data]);

  const memberNames = useMemo(() => {
    if (membershipQuery.data) {
      return membershipBindingUsernames(membershipQuery.data, name);
    }
    if (membershipQuery.isError) {
      return (usersQuery.data ?? [])
        .map((user) => user.name)
        .filter((value): value is string => Boolean(value));
    }
    return [];
  }, [membershipQuery.data, membershipQuery.isError, name, usersQuery.data]);

  const members = useMemo<MemberRow[]>(
    () =>
      memberNames
        .map((memberName) => {
          const profile = memberProfiles.get(memberName);
          return {
            name: memberName,
            displayName: profile?.displayName || memberName,
            disabled: profile?.disabled,
            registeredAt: profile?.registeredAt,
            hasProfile: Boolean(profile),
          };
        })
        .sort(
          (left, right) =>
            left.displayName.localeCompare(right.displayName, "zh-CN") ||
            left.name.localeCompare(right.name),
        ),
    [memberNames, memberProfiles],
  );

  const roleByName = useMemo(() => {
    const roles = new Map<string, Role>();
    for (const role of rolesQuery.data ?? []) {
      roles.set(role.metadata.name, role);
    }
    return roles;
  }, [rolesQuery.data]);

  const roleBindingsByRole = useMemo(() => {
    const bindings = new Map<string, UserGroupBinding>();
    for (const binding of roleBindingsQuery.data ?? []) {
      const roleName = binding.roleRef?.kind === "Role" ? binding.roleRef.name : undefined;
      if (roleName && !bindings.has(roleName)) {
        bindings.set(roleName, binding);
      }
    }
    return bindings;
  }, [roleBindingsQuery.data]);

  const bindingRoleLabel = (binding: UserGroupBinding | null): string => {
    const roleName = binding?.roleRef?.name;
    if (!roleName) {
      return "—";
    }
    const role = roleByName.get(roleName);
    return role ? roleDisplayName(role) : roleName;
  };

  const boundRoleNames = useMemo(() => {
    const names = new Set(group ? userGroupRoleNames(group) : []);
    for (const roleName of roleBindingNames(roleBindingsQuery.data ?? [], name)) {
      names.add(roleName);
    }
    return [...names].sort((left, right) => {
      const leftRole = roleByName.get(left);
      const rightRole = roleByName.get(right);
      const leftLabel = leftRole ? roleDisplayName(leftRole) : left;
      const rightLabel = rightRole ? roleDisplayName(rightRole) : right;
      return leftLabel.localeCompare(rightLabel, "zh-CN") || left.localeCompare(right);
    });
  }, [group, name, roleBindingsQuery.data, roleByName]);

  const membersPending =
    membershipQuery.isPending || (membershipQuery.isError && usersQuery.isPending);
  const membersError = membershipQuery.isError && usersQuery.isError;
  const membersPageCount = Math.max(1, Math.ceil(members.length / memberSize));
  const currentMemberPage = Math.min(memberPage, membersPageCount);
  const pageMembers = members.slice(
    (currentMemberPage - 1) * memberSize,
    currentMemberPage * memberSize,
  );
  const rolesPending =
    boundRoleNames.length === 0 && (roleBindingsQuery.isPending || rolesQuery.isPending);

  const deleteMutation = useMutation({
    mutationFn: async () => {
      await apiClient.userGroup.deleteUserGroup({ name });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: USER_GROUPS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ["users"] }),
      ]);
      toast.success("用户组已删除。");
      if (groupsPath) {
        router.push(groupsPath);
      }
    },
    onError: (error) => {
      setDeleteConfirmOpen(false);
      toast.error(resolveUserGroupApiError(error));
    },
  });

  const roleBindingDeleteMutation = useMutation({
    mutationFn: async (binding: UserGroupBinding) => deleteGroupRoleBinding(binding.metadata.name),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-group"] }),
        queryClient.invalidateQueries({ queryKey: USER_GROUPS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ["user-permissions"] }),
      ]);
      setRoleBindingDeleteTarget(null);
      toast.success("角色绑定已删除。");
    },
    onError: (error) => {
      setRoleBindingDeleteTarget(null);
      toast.error(resolveUserGroupBindingApiError(error));
    },
  });

  if (groupQuery.isPending) {
    return (
      <ConsolePage title="用户组详情">
        <div className="space-y-4">
          <Skeleton className="h-10 w-40" />
          <div className="grid gap-4 xl:grid-cols-3">
            <Skeleton className="h-64 xl:col-span-2" />
            <Skeleton className="h-64" />
            <Skeleton className="h-64 xl:col-span-3" />
          </div>
        </div>
      </ConsolePage>
    );
  }

  if (groupQuery.isError || !group) {
    return (
      <ConsolePage title="用户组详情">
        <ConsoleSection className="flex min-h-64 flex-col items-center justify-center gap-4 p-8 text-center">
          <UsersRound className="size-8 text-muted-foreground" />
          <div className="space-y-1">
            <h2 className="text-sm font-semibold">无法加载用户组</h2>
            <p className="text-xs text-muted-foreground">
              {resolveUserGroupApiError(groupQuery.error)}
            </p>
          </div>
          <div className="flex gap-2">
            {groupsPath ? (
              <Button variant="outline" size="sm" asChild>
                <Link href={groupsPath}>
                  <ArrowLeft />
                  返回用户组列表
                </Link>
              </Button>
            ) : null}
            <Button variant="outline" size="sm" onClick={() => void groupQuery.refetch()}>
              <RefreshCw />
              重试
            </Button>
          </div>
        </ConsoleSection>
      </ConsolePage>
    );
  }

  return (
    <ConsolePage title={displayName}>
      <div className="flex flex-wrap items-center gap-2">
        {groupsPath ? (
          <Button variant="ghost" size="sm" asChild>
            <Link href={groupsPath}>
              <ArrowLeft />
              返回用户组列表
            </Link>
          </Button>
        ) : null}
        <div className="ms-auto flex flex-wrap items-center gap-2">
          {superAdmins ? (
            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Lock className="size-3.5" />
              超级管理员用户组不可修改
            </span>
          ) : (
            <>
              {canUpdate ? (
                <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
                  <Pencil />
                  {roleAdjustable ? "调整角色" : "编辑用户组"}
                </Button>
              ) : null}
              {canDelete && !builtin ? (
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => setDeleteConfirmOpen(true)}
                  disabled={deleteMutation.isPending}
                >
                  <Trash2 />
                  删除用户组
                </Button>
              ) : null}
            </>
          )}
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <ConsoleSection className="xl:col-span-2">
          <ConsoleSectionHeader
            title="用户组资料"
            description="用户组通过绑定的角色获得权限，成员用户会继承这些角色。"
            action={
              <Badge variant={builtin ? "secondary" : "outline"}>
                {builtin ? "系统内置" : "自定义"}
              </Badge>
            }
          />
          <div className="grid gap-x-6 gap-y-4 p-5 sm:grid-cols-2">
            <InfoRow
              label="用户组标识"
              value={<span className="font-mono text-xs">{group.metadata.name}</span>}
            />
            <InfoRow label="显示名称" value={userGroupDisplayName(group)} />
            <InfoRow
              label="创建时间"
              value={formatUserDateTime(group.metadata.creationTimestamp)}
            />
            <InfoRow
              label="成员数量"
              value={membersPending ? "加载中..." : membersError ? "—" : `${members.length} 人`}
            />
          </div>
          {group.description ? (
            <div className="border-t px-5 py-4">
              <p className="mb-2 text-xs font-medium text-muted-foreground">描述</p>
              <MarkdownContent content={group.description} />
            </div>
          ) : null}
        </ConsoleSection>

        <ConsoleSection>
          <ConsoleSectionHeader
            title="绑定角色"
            description="用户组通过这些角色获得权限。"
            action={
              <div className="flex flex-wrap items-center gap-2">
                {!rolesPending && boundRoleNames.length > 0 ? (
                  <span className="text-xs text-muted-foreground">
                    共 {boundRoleNames.length} 个
                  </span>
                ) : null}
                {canUpdate && !superAdmins ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setRoleBindingForm({})}
                  >
                    <Plus />
                    添加绑定
                  </Button>
                ) : null}
              </div>
            }
          />
          {roleBindingsQuery.isError ? (
            <p className="border-b px-5 py-2 text-xs text-muted-foreground">
              无法加载角色绑定，仅显示用户组直接绑定的角色。
            </p>
          ) : null}
          {rolesPending ? (
            <div className="space-y-3 p-5">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : boundRoleNames.length === 0 ? (
            <ConsoleEmpty>该用户组没有绑定角色。</ConsoleEmpty>
          ) : (
            <>
              {rolesQuery.isError ? (
                <p className="border-b px-5 py-2 text-xs text-muted-foreground">
                  无法加载角色详情，仅显示角色标识。
                </p>
              ) : null}
              <div className="divide-y">
                {boundRoleNames.map((roleName) => {
                  const role = roleByName.get(roleName);
                  const roleBinding = roleBindingsByRole.get(roleName);

                  return (
                    <div key={roleName} className="flex items-start gap-3 px-5 py-3">
                      <span className="flex size-9 shrink-0 items-center justify-center bg-muted">
                        <ShieldCheck className="size-4 text-muted-foreground" />
                      </span>
                      <div className="min-w-0 flex-1 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="text-sm font-medium">
                            {role ? roleDisplayName(role) : roleName}
                          </p>
                          {role && isBuiltinRole(role) ? (
                            <Badge variant="secondary">内置</Badge>
                          ) : null}
                        </div>
                        <p className="truncate font-mono text-xs text-muted-foreground">
                          {roleName}
                        </p>
                        {role ? (
                          <p className="text-xs leading-5 text-muted-foreground">
                            {roleDescription(role)}
                          </p>
                        ) : rolesQuery.isError ? null : (
                          <p className="text-xs leading-5 text-muted-foreground">
                            角色不存在或已被删除。
                          </p>
                        )}
                      </div>
                      {canUpdate && !superAdmins && roleBinding ? (
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              type="button"
                              variant="ghost"
                              className="size-8 shrink-0 p-0 data-[state=open]:bg-muted"
                              aria-label="角色绑定操作"
                            >
                              <MoreHorizontal className="size-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-40">
                            <DropdownMenuItem
                              onSelect={() => setRoleBindingForm({ binding: roleBinding })}
                            >
                              <Pencil />
                              编辑绑定
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              onSelect={() => setRoleBindingDeleteTarget(roleBinding)}
                            >
                              <Trash2 />
                              删除绑定
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      ) : null}
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </ConsoleSection>

        <ConsoleSection className="xl:col-span-3">
          <ConsoleSectionHeader
            title="成员"
            description="直接属于该用户组的用户。"
            action={
              membersPending || membersError ? null : (
                <span className="text-xs text-muted-foreground">共 {members.length} 人</span>
              )
            }
          />
          {membersPending ? (
            <div className="space-y-3 p-5">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : membersError ? (
            <ConsoleEmpty>{resolveUserGroupApiError(membershipQuery.error)}</ConsoleEmpty>
          ) : members.length === 0 ? (
            <ConsoleEmpty>该用户组暂无成员。</ConsoleEmpty>
          ) : (
            <>
              {usersQuery.isError ? (
                <p className="border-b px-5 py-2 text-xs text-muted-foreground">
                  无法加载成员资料，仅显示用户名。
                </p>
              ) : null}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="px-5">成员</TableHead>
                    <TableHead className="px-5">状态</TableHead>
                    <TableHead className="px-5">注册时间</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pageMembers.map((member) => {
                    const memberContent = (
                      <>
                        <UserAvatar
                          name={member.name}
                          displayName={member.displayName}
                          className="size-8 shrink-0"
                        />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">{member.displayName}</p>
                          <p className="truncate font-mono text-xs text-muted-foreground">
                            @{member.name}
                          </p>
                        </div>
                      </>
                    );

                    return (
                      <TableRow key={member.name}>
                        <TableCell className="px-5 py-3">
                          {usersPath ? (
                            <Link
                              href={`${usersPath}/${member.name}`}
                              className="flex min-w-0 items-center gap-2"
                            >
                              {memberContent}
                            </Link>
                          ) : (
                            <div className="flex min-w-0 items-center gap-2">{memberContent}</div>
                          )}
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          {member.hasProfile ? (
                            <Badge variant={member.disabled ? "destructive" : "secondary"}>
                              {member.disabled ? "已禁用" : "正常"}
                            </Badge>
                          ) : (
                            <span className="text-sm text-muted-foreground">—</span>
                          )}
                        </TableCell>
                        <TableCell className="px-5 py-3 text-sm text-muted-foreground">
                          {member.hasProfile ? formatUserDateTime(member.registeredAt) : "—"}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
              <DataTablePagination
                currentPage={currentMemberPage}
                pageCount={membersPageCount}
                pageSize={memberSize}
                onPageChange={setMemberPage}
                onPageSizeChange={(value) => {
                  setMemberSize(value);
                  setMemberPage(1);
                }}
                className="border-t px-4 py-3"
              />
            </>
          )}
        </ConsoleSection>
      </div>

      {editOpen ? <EditUserGroupDialog group={group} onClose={() => setEditOpen(false)} /> : null}
      {roleBindingForm ? (
        <UserGroupRoleBindingDialog
          key={roleBindingForm.binding?.metadata.name ?? "create"}
          groupName={name}
          groupDisplayName={displayName}
          binding={roleBindingForm.binding}
          boundRoleNames={boundRoleNames}
          onClose={() => setRoleBindingForm(null)}
        />
      ) : null}

      <ConfirmDialog
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        title="删除用户组"
        desc={`确定删除「${displayName}」？该用户组的成员将失去该组继承的角色，此操作无法恢复。`}
        confirmText="删除"
        destructive
        isLoading={deleteMutation.isPending}
        handleConfirm={() => deleteMutation.mutate()}
      />
      <ConfirmDialog
        open={Boolean(roleBindingDeleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setRoleBindingDeleteTarget(null);
          }
        }}
        title="删除角色绑定"
        desc={
          roleBindingDeleteTarget
            ? `确定删除「${displayName}」绑定的「${bindingRoleLabel(roleBindingDeleteTarget)}」角色？成员用户将失去该角色，此操作无法恢复。`
            : ""
        }
        confirmText="删除"
        destructive
        isLoading={roleBindingDeleteMutation.isPending}
        handleConfirm={() => {
          if (roleBindingDeleteTarget) {
            roleBindingDeleteMutation.mutate(roleBindingDeleteTarget);
          }
        }}
      />
    </ConsolePage>
  );
}
