import { useMemo, useState } from "react";

import type { RoleBinding } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MoreHorizontal, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import {
  DataTableFacetedFilter,
  DataTablePagination,
  DataTableToolbar,
  LongText,
} from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatBeijingDateTime } from "@/lib/date";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";
import { fetchAllUsers } from "@/modules/workspace/console/users/lib/api";
import { useUserGroups } from "@/modules/workspace/lib/user-groups";

import { deleteRoleBinding, fetchAllRoleBindings, resolveRoleBindingApiError } from "../lib/api";
import { roleBindingRoleName } from "../lib/binding-model";
import { roleDisplayName } from "../lib/role-model";
import { useRoles } from "../lib/use-roles";
import { RoleBindingDialog } from "./role-binding-dialog";

type RoleBindingsTabProps = {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
};

const SUBJECT_KIND_OPTIONS = [
  { value: "User", label: "用户" },
  { value: "UserGroup", label: "用户组" },
] as const;

export function RoleBindingsTab({ canCreate, canUpdate, canDelete }: RoleBindingsTabProps) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [roleFilters, setRoleFilters] = useState<string[]>([]);
  const [kindFilters, setKindFilters] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<RoleBinding | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RoleBinding | null>(null);

  const bindingsQuery = useQuery({
    queryKey: ["role-bindings"],
    queryFn: fetchAllRoleBindings,
    retry: false,
  });
  const rolesQuery = useRoles();
  const groupsQuery = useUserGroups();
  const usersQuery = useQuery({
    queryKey: ["users", "binding-options"],
    queryFn: () => fetchAllUsers(),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const roles = useMemo(() => rolesQuery.data ?? [], [rolesQuery.data]);
  const roleLabels = useMemo(() => {
    const labels = new Map<string, string>();
    for (const role of roles) {
      labels.set(role.metadata.name, roleDisplayName(role));
    }
    return labels;
  }, [roles]);
  const roleLabel = (roleName: string) => roleLabels.get(roleName) ?? roleName;
  const roleOptions = useMemo(
    () =>
      [...roles]
        .sort((left, right) => roleDisplayName(left).localeCompare(roleDisplayName(right), "zh-CN"))
        .map((role) => ({ value: role.metadata.name, label: roleDisplayName(role) })),
    [roles],
  );
  const columnCount = canUpdate || canDelete ? 5 : 4;
  const userNames = useMemo(() => {
    const names = new Map<string, string>();
    for (const user of usersQuery.data ?? []) {
      if (user.name) {
        names.set(user.name, user.displayName || user.name);
      }
    }
    return names;
  }, [usersQuery.data]);
  const groupNames = useMemo(() => {
    const names = new Map<string, string>();
    for (const group of groupsQuery.data ?? []) {
      names.set(group.metadata.name, group.displayName || group.metadata.name);
    }
    return names;
  }, [groupsQuery.data]);

  const bindings = useMemo(
    () => (bindingsQuery.data ?? []).filter((binding) => !binding.metadata.deletionTimestamp),
    [bindingsQuery.data],
  );
  const normalizedSearch = search.trim().toLowerCase();
  const filteredBindings = useMemo(() => {
    return bindings.filter((binding) => {
      const roleName = roleBindingRoleName(binding);
      if (roleFilters.length > 0 && !roleFilters.includes(roleName)) {
        return false;
      }
      const subjects = binding.subjects ?? [];
      if (
        kindFilters.length > 0 &&
        !subjects.some((subject) => kindFilters.includes(subject.kind ?? ""))
      ) {
        return false;
      }
      if (!normalizedSearch) {
        return true;
      }
      const subjectText = subjects
        .map((subject) => {
          const name = subject.name ?? "";
          const label =
            subject.kind === "User"
              ? (userNames.get(name) ?? name)
              : subject.kind === "UserGroup"
                ? (groupNames.get(name) ?? name)
                : name;
          return `${subject.kind ?? ""} ${name} ${label}`;
        })
        .join(" ");
      return (
        binding.metadata.name.toLowerCase().includes(normalizedSearch) ||
        roleName.toLowerCase().includes(normalizedSearch) ||
        (roleLabels.get(roleName) ?? "").toLowerCase().includes(normalizedSearch) ||
        subjectText.toLowerCase().includes(normalizedSearch)
      );
    });
  }, [bindings, groupNames, kindFilters, normalizedSearch, roleFilters, roleLabels, userNames]);

  const total = filteredBindings.length;
  const pageCount = Math.max(1, Math.ceil(total / size));
  const currentPage = Math.min(page, pageCount);
  const pageRows = filteredBindings.slice((currentPage - 1) * size, currentPage * size);
  const isFiltered = search.trim().length > 0 || roleFilters.length > 0 || kindFilters.length > 0;

  const deleteMutation = useMutation({
    mutationFn: async (binding: RoleBinding) => deleteRoleBinding(binding.metadata.name),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["role-bindings"] }),
        queryClient.invalidateQueries({ queryKey: ["user-permissions"] }),
      ]);
      setDeleteTarget(null);
      toast.success("角色绑定已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveRoleBindingApiError(error));
    },
  });

  const resetFilters = () => {
    setSearch("");
    setRoleFilters([]);
    setKindFilters([]);
    setPage(1);
  };

  const subjectLabel = (kind: string | undefined, name: string | undefined) => {
    if (!name) {
      return "—";
    }
    if (kind === "User") {
      return userNames.get(name) ?? name;
    }
    if (kind === "UserGroup") {
      return groupNames.get(name) ?? name;
    }
    return name;
  };

  return (
    <div className="flex flex-1 flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">角色绑定</h2>
          <p className="text-xs text-muted-foreground">将角色直接授予用户或用户组。</p>
        </div>
        {canCreate ? (
          <Button type="button" size="sm" onClick={() => setCreateOpen(true)}>
            <Plus />
            添加绑定
          </Button>
        ) : null}
      </div>

      <DataTableToolbar
        searchPlaceholder="筛选角色绑定..."
        searchValue={search}
        onSearchChange={(value) => {
          setSearch(value);
          setPage(1);
        }}
        filters={
          <>
            <DataTableFacetedFilter
              title="角色"
              options={roleOptions}
              selected={roleFilters}
              onSelectedChange={(values) => {
                setRoleFilters(values);
                setPage(1);
              }}
            />
            <DataTableFacetedFilter
              title="主体类型"
              options={SUBJECT_KIND_OPTIONS}
              selected={kindFilters}
              onSelectedChange={(values) => {
                setKindFilters(values);
                setPage(1);
              }}
            />
          </>
        }
        isFiltered={isFiltered}
        onReset={resetFilters}
      />

      <div className="overflow-hidden border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="px-4">名称</TableHead>
              <TableHead className="px-4">角色</TableHead>
              <TableHead className="px-4">主体</TableHead>
              <TableHead className="px-4">创建时间</TableHead>
              {canUpdate || canDelete ? (
                <TableHead className="px-4 text-end">操作</TableHead>
              ) : null}
            </TableRow>
          </TableHeader>
          <TableBody>
            {bindingsQuery.isPending ? (
              <ConsoleTableSkeleton columns={columnCount} />
            ) : bindingsQuery.isError ? (
              <TableRow>
                <TableCell colSpan={columnCount}>
                  <ConsoleState
                    variant="error"
                    title="无法加载角色绑定"
                    description={resolveRoleBindingApiError(bindingsQuery.error)}
                    onRetry={() => void bindingsQuery.refetch()}
                  />
                </TableCell>
              </TableRow>
            ) : pageRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columnCount}>
                  <ConsoleState
                    variant="empty"
                    title={isFiltered ? "没有匹配的角色绑定。" : "暂无角色绑定。"}
                  />
                </TableCell>
              </TableRow>
            ) : (
              pageRows.map((binding) => {
                const roleName = roleBindingRoleName(binding);
                return (
                  <TableRow key={binding.metadata.name}>
                    <TableCell className="px-4 py-3">
                      <LongText className="max-w-56 font-mono text-xs">
                        {binding.metadata.name}
                      </LongText>
                    </TableCell>
                    <TableCell className="px-4 py-3">
                      <div className="space-y-0.5">
                        <p className="text-sm font-medium">{roleLabel(roleName)}</p>
                        {roleName !== roleLabel(roleName) ? (
                          <p className="font-mono text-xs text-muted-foreground">{roleName}</p>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell className="px-4 py-3">
                      {(binding.subjects ?? []).length === 0 ? (
                        <span className="text-sm text-muted-foreground">—</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {(binding.subjects ?? []).map((subject, index) => (
                            <Badge
                              key={`${subject.kind}-${subject.name}-${index}`}
                              variant="secondary"
                            >
                              {subjectLabel(subject.kind, subject.name)}
                            </Badge>
                          ))}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="px-4 py-3 text-sm text-muted-foreground">
                      {formatBeijingDateTime(binding.metadata.creationTimestamp)}
                    </TableCell>
                    {canUpdate || canDelete ? (
                      <TableCell className="px-4 py-3 text-end">
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              type="button"
                              variant="ghost"
                              className="flex size-8 p-0 data-[state=open]:bg-muted"
                              aria-label="角色绑定操作"
                            >
                              <MoreHorizontal className="size-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-40">
                            {canUpdate ? (
                              <DropdownMenuItem onSelect={() => setEditTarget(binding)}>
                                <Pencil />
                                编辑绑定
                              </DropdownMenuItem>
                            ) : null}
                            {canUpdate && canDelete ? <DropdownMenuSeparator /> : null}
                            {canDelete ? (
                              <DropdownMenuItem
                                variant="destructive"
                                onSelect={() => setDeleteTarget(binding)}
                              >
                                <Trash2 />
                                删除绑定
                              </DropdownMenuItem>
                            ) : null}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    ) : null}
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </div>

      <DataTablePagination
        currentPage={currentPage}
        pageCount={pageCount}
        pageSize={size}
        onPageChange={setPage}
        onPageSizeChange={(value) => {
          setSize(value);
          setPage(1);
        }}
        disabled={bindingsQuery.isFetching}
        className="mt-auto"
      />

      {createOpen ? <RoleBindingDialog onClose={() => setCreateOpen(false)} /> : null}
      {editTarget ? (
        <RoleBindingDialog
          key={editTarget.metadata.name}
          binding={editTarget}
          onClose={() => setEditTarget(null)}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除角色绑定"
        desc={
          deleteTarget
            ? `确定删除「${deleteTarget.metadata.name}」？绑定主体将失去该角色直接授予的权限，此操作无法恢复。`
            : ""
        }
        confirmText="删除"
        destructive
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </div>
  );
}
