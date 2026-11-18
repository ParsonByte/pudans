import { useCallback, useMemo, useState } from "react";

import { type UserGroup, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { type ColumnDef, createColumnHelper, tableFeatures, useTable } from "@tanstack/react-table";
import {
  Eye,
  Loader2,
  MoreHorizontal,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
  UsersRound,
} from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import {
  DataTableBulkActions,
  DataTableColumnHeader,
  DataTableFacetedFilter,
  DataTablePagination,
  type DataTableSortDirection,
  DataTableToolbar,
  DataTableViewOptions,
  LongText,
} from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import { cn } from "@/lib/utils";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { roleDisplayName } from "@/modules/workspace/console/roles/lib/role-model";
import { useRoles } from "@/modules/workspace/console/roles/lib/use-roles";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";
import { USER_GROUPS_QUERY_KEY, useUserGroups } from "@/modules/workspace/lib/user-groups";

import { CreateUserGroupDialog } from "./components/create-user-group-dialog";
import { EditUserGroupDialog } from "./components/edit-user-group-dialog";
import { resolveUserGroupApiError } from "./lib/api";
import {
  isBuiltinUserGroup,
  isRoleAdjustableBuiltinGroup,
  userGroupDisplayName,
  userGroupRoleNames,
} from "./lib/user-group-model";

const USER_GROUP_TABLE_FEATURES = tableFeatures({});
const userGroupColumnHelper = createColumnHelper<typeof USER_GROUP_TABLE_FEATURES, UserGroup>();

type UserGroupColumn = ColumnDef<typeof USER_GROUP_TABLE_FEATURES, UserGroup, unknown>;

type SortField = "name" | "displayName";

type SortState = {
  field: SortField;
  direction: DataTableSortDirection;
};

const TYPE_OPTIONS = [
  { label: "系统内置", value: "builtin" },
  { label: "自定义", value: "custom" },
] as const;

const COLUMN_OPTIONS = [
  { key: "displayName", label: "显示名称" },
  { key: "description", label: "描述" },
  { key: "roles", label: "绑定角色" },
  { key: "type", label: "类型" },
] as const;

export function UserGroupsView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const groupsPath = useMenuPath("user-groups");
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");

  const [search, setSearch] = useState("");
  const [typeFilters, setTypeFilters] = useState<string[]>([]);
  const [roleFilters, setRoleFilters] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [sort, setSort] = useState<SortState>({ field: "displayName", direction: "asc" });
  const [visibleColumns, setVisibleColumns] = useState<Record<string, boolean>>({
    displayName: true,
    description: true,
    roles: true,
    type: true,
  });
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<UserGroup | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<UserGroup | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);
  const canSelect = canDelete;

  const groupsQuery = useUserGroups();
  const rolesQuery = useRoles();

  const groups = useMemo(
    () => (groupsQuery.data ?? []).filter((group) => !group.metadata.deletionTimestamp),
    [groupsQuery.data],
  );

  const roleLabels = useMemo(() => {
    const labels = new Map<string, string>();
    for (const role of rolesQuery.data ?? []) {
      labels.set(role.metadata.name, roleDisplayName(role));
    }
    return labels;
  }, [rolesQuery.data]);

  const roleFilterOptions = useMemo(() => {
    const names = new Set<string>();
    for (const group of groups) {
      for (const name of userGroupRoleNames(group)) {
        names.add(name);
      }
    }
    return [...names]
      .map((name) => ({ value: name, label: roleLabels.get(name) ?? name }))
      .sort((left, right) => left.label.localeCompare(right.label, "zh-CN"));
  }, [groups, roleLabels]);

  const normalizedSearch = search.trim().toLowerCase();
  const filteredGroups = useMemo(() => {
    const typeSet = new Set(typeFilters);
    const roleSet = new Set(roleFilters);

    return groups.filter((group) => {
      const roleNames = userGroupRoleNames(group);
      if (typeSet.size > 0 && !typeSet.has(isBuiltinUserGroup(group) ? "builtin" : "custom")) {
        return false;
      }
      if (roleSet.size > 0 && !roleNames.some((name) => roleSet.has(name))) {
        return false;
      }
      if (!normalizedSearch) {
        return true;
      }
      return [
        group.metadata.name,
        group.displayName,
        group.description,
        ...roleNames.map((name) => roleLabels.get(name) ?? name),
      ]
        .filter(Boolean)
        .join("\n")
        .toLowerCase()
        .includes(normalizedSearch);
    });
  }, [groups, normalizedSearch, roleFilters, roleLabels, typeFilters]);

  const sortedGroups = useMemo(() => {
    const direction = sort.direction === "asc" ? 1 : -1;
    return [...filteredGroups].sort((left, right) => {
      if (sort.field === "name") {
        return left.metadata.name.localeCompare(right.metadata.name) * direction;
      }
      return (
        (userGroupDisplayName(left).localeCompare(userGroupDisplayName(right), "zh-CN") ||
          left.metadata.name.localeCompare(right.metadata.name)) * direction
      );
    });
  }, [filteredGroups, sort]);

  const total = sortedGroups.length;
  const pageCount = Math.max(1, Math.ceil(total / size));
  const currentPage = Math.min(page, pageCount);
  const pageGroups = sortedGroups.slice((currentPage - 1) * size, currentPage * size);
  const isFiltered = search.trim().length > 0 || typeFilters.length > 0 || roleFilters.length > 0;
  const selectableNames = pageGroups
    .filter((group) => !isBuiltinUserGroup(group))
    .map((group) => group.metadata.name);
  const allSelected =
    selectableNames.length > 0 && selectableNames.every((name) => selected.has(name));
  const someSelected = selectableNames.some((name) => selected.has(name)) && !allSelected;

  const toggleAll = useCallback(() => {
    setSelected((current) => {
      const next = new Set(current);
      if (allSelected) {
        selectableNames.forEach((name) => next.delete(name));
      } else {
        selectableNames.forEach((name) => next.add(name));
      }
      return next;
    });
  }, [allSelected, selectableNames]);

  const toggleOne = useCallback((name: string, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(name);
      } else {
        next.delete(name);
      }
      return next;
    });
  }, []);

  const deleteMutation = useMutation({
    mutationFn: async (group: UserGroup) => {
      await apiClient.userGroup.deleteUserGroup({ name: group.metadata.name });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: USER_GROUPS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ["users"] }),
      ]);
      setDeleteTarget(null);
      setSelected(new Set());
      toast.success("用户组已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveUserGroupApiError(error));
    },
  });

  const bulkDeleteMutation = useMutation({
    mutationFn: async (names: string[]) => {
      const results = await Promise.allSettled(
        names.map((name) => apiClient.userGroup.deleteUserGroup({ name })),
      );
      return {
        total: names.length,
        failed: results.filter((result) => result.status === "rejected").length,
      };
    },
    onSuccess: async ({ total: count, failed }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: USER_GROUPS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ["users"] }),
      ]);
      setSelected(new Set());
      setBulkDeleteOpen(false);
      if (failed > 0) {
        toast.error(`${failed} / ${count} 个用户组删除失败。`);
      } else {
        toast.success(`已删除 ${count} 个用户组。`);
      }
    },
    onError: (error) => {
      setBulkDeleteOpen(false);
      toast.error(resolveUserGroupApiError(error));
    },
  });

  const resetFilters = () => {
    setSearch("");
    setTypeFilters([]);
    setRoleFilters([]);
    setPage(1);
    setSelected(new Set());
  };

  const columns = useMemo<UserGroupColumn[]>(() => {
    const allColumns = [
      {
        key: "select",
        column: userGroupColumnHelper.display({
          id: "select",
          header: () => (
            <Checkbox
              checked={allSelected ? true : someSelected ? "indeterminate" : false}
              disabled={selectableNames.length === 0}
              onCheckedChange={toggleAll}
              aria-label="选择当前页全部自定义用户组"
              className="translate-y-0.5"
            />
          ),
          cell: ({ row }) => {
            const group = row.original;
            const selectable = !isBuiltinUserGroup(group);

            return (
              <Checkbox
                checked={selectable && selected.has(group.metadata.name)}
                disabled={!selectable}
                onCheckedChange={(checked) => toggleOne(group.metadata.name, checked === true)}
                aria-label={`选择用户组 ${group.metadata.name}`}
                title={selectable ? undefined : "系统内置用户组不可选择"}
                className="translate-y-0.5"
              />
            );
          },
        }),
      },
      {
        key: "name",
        column: userGroupColumnHelper.accessor((group) => group.metadata.name, {
          id: "name",
          header: () => (
            <DataTableColumnHeader
              title="用户组标识"
              direction={sort.field === "name" ? sort.direction : undefined}
              onSort={(direction) => {
                setSort({ field: "name", direction });
                setPage(1);
              }}
            />
          ),
          cell: ({ row }) => (
            <LongText className="max-w-36 ps-3 font-mono text-sm">
              {row.original.metadata.name}
            </LongText>
          ),
        }),
      },
      {
        key: "displayName",
        column: userGroupColumnHelper.accessor((group) => group.displayName, {
          id: "displayName",
          header: () => (
            <DataTableColumnHeader
              title="显示名称"
              direction={sort.field === "displayName" ? sort.direction : undefined}
              onSort={(direction) => {
                setSort({ field: "displayName", direction });
                setPage(1);
              }}
              canHide
              onHide={() => setVisibleColumns((current) => ({ ...current, displayName: false }))}
            />
          ),
          cell: ({ row }) => {
            const content = (
              <>
                <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
                  <UsersRound className="size-4" />
                </span>
                <LongText className="max-w-36 text-sm font-medium">
                  {userGroupDisplayName(row.original)}
                </LongText>
              </>
            );
            return groupsPath ? (
              <Link
                to={`${groupsPath}/${row.original.metadata.name}` as string}
                className="flex min-w-0 items-center gap-2"
              >
                {content}
              </Link>
            ) : (
              <div className="flex min-w-0 items-center gap-2">{content}</div>
            );
          },
        }),
      },
      {
        key: "description",
        column: userGroupColumnHelper.accessor((group) => group.description, {
          id: "description",
          header: "描述",
          cell: ({ row }) => (
            <LongText className="max-w-64 text-sm text-muted-foreground">
              {row.original.description || "—"}
            </LongText>
          ),
        }),
      },
      {
        key: "roles",
        column: userGroupColumnHelper.accessor((group) => userGroupRoleNames(group), {
          id: "roles",
          header: "绑定角色",
          cell: ({ row }) => {
            const roleNames = userGroupRoleNames(row.original);
            if (roleNames.length === 0) {
              return <span className="text-sm text-muted-foreground">—</span>;
            }
            const shown = roleNames.slice(0, 2);
            const remaining = roleNames.slice(shown.length);

            return (
              <div className="flex flex-wrap items-center gap-1">
                {shown.map((name) => (
                  <Badge key={name} variant="secondary" className="font-normal">
                    {roleLabels.get(name) ?? name}
                  </Badge>
                ))}
                {remaining.length > 0 ? (
                  <Badge
                    variant="outline"
                    title={remaining.map((name) => roleLabels.get(name) ?? name).join("、")}
                  >
                    +{remaining.length}
                  </Badge>
                ) : null}
              </div>
            );
          },
        }),
      },
      {
        key: "type",
        column: userGroupColumnHelper.accessor((group) => isBuiltinUserGroup(group), {
          id: "type",
          header: "类型",
          cell: ({ row }) => (
            <Badge variant={isBuiltinUserGroup(row.original) ? "secondary" : "outline"}>
              {isBuiltinUserGroup(row.original) ? "系统内置" : "自定义"}
            </Badge>
          ),
        }),
      },
      {
        key: "actions",
        column: userGroupColumnHelper.display({
          id: "actions",
          header: () => <span className="sr-only">操作</span>,
          cell: ({ row }) => {
            const group = row.original;
            const builtin = isBuiltinUserGroup(group);
            const roleAdjustable = isRoleAdjustableBuiltinGroup(group);
            const editAllowed = canUpdate && (!builtin || roleAdjustable);
            const deleteAllowed = canDelete && !builtin;

            if (!groupsPath && !editAllowed && !deleteAllowed) {
              return <span className="text-sm text-muted-foreground">—</span>;
            }

            return (
              <DropdownMenu modal={false}>
                <DropdownMenuTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    className="flex h-8 w-8 p-0 data-[state=open]:bg-muted"
                    aria-label="用户组操作"
                  >
                    <MoreHorizontal className="size-4" />
                    <span className="sr-only">打开菜单</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-40">
                  {groupsPath ? (
                    <DropdownMenuItem asChild>
                      <Link to={`${groupsPath}/${group.metadata.name}` as string}>
                        <Eye />
                        查看详情
                      </Link>
                    </DropdownMenuItem>
                  ) : null}
                  {editAllowed ? (
                    <DropdownMenuItem onSelect={() => setEditTarget(group)}>
                      <Pencil />
                      {roleAdjustable ? "调整角色" : "编辑用户组"}
                    </DropdownMenuItem>
                  ) : null}
                  {deleteAllowed ? (
                    <>
                      {groupsPath || editAllowed ? <DropdownMenuSeparator /> : null}
                      <DropdownMenuItem
                        variant="destructive"
                        onSelect={() => setDeleteTarget(group)}
                      >
                        <Trash2 />
                        删除用户组
                      </DropdownMenuItem>
                    </>
                  ) : null}
                </DropdownMenuContent>
              </DropdownMenu>
            );
          },
        }),
      },
    ];

    return allColumns
      .filter((item) => {
        if (item.key === "select") {
          return canSelect;
        }
        if (item.key === "actions") {
          return Boolean(groupsPath) || canUpdate || canDelete;
        }
        return visibleColumns[item.key] !== false;
      })
      .map((item) => item.column) as unknown as UserGroupColumn[];
  }, [
    allSelected,
    canDelete,
    canSelect,
    canUpdate,
    groupsPath,
    roleLabels,
    selectableNames,
    selected,
    someSelected,
    sort,
    toggleAll,
    toggleOne,
    visibleColumns,
  ]);

  const table = useTable({
    features: USER_GROUP_TABLE_FEATURES,
    columns,
    data: pageGroups,
  });

  return (
    <ConsolePage title="用户组">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-2xl font-bold tracking-tight">用户组列表</h2>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => void groupsQuery.refetch()}
              disabled={groupsQuery.isFetching}
            >
              {groupsQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            {canCreate ? (
              <Button type="button" onClick={() => setCreateOpen(true)}>
                <Plus />
                创建用户组
              </Button>
            ) : null}
          </div>
        </div>

        <DataTableToolbar
          searchPlaceholder="筛选用户组..."
          searchValue={search}
          onSearchChange={(value) => {
            setSearch(value);
            setPage(1);
            setSelected(new Set());
          }}
          filters={
            <>
              <DataTableFacetedFilter
                title="类型"
                options={TYPE_OPTIONS}
                selected={typeFilters}
                onSelectedChange={(values) => {
                  setTypeFilters(values);
                  setPage(1);
                  setSelected(new Set());
                }}
              />
              {roleFilterOptions.length > 0 ? (
                <DataTableFacetedFilter
                  title="绑定角色"
                  options={roleFilterOptions}
                  selected={roleFilters}
                  onSelectedChange={(values) => {
                    setRoleFilters(values);
                    setPage(1);
                    setSelected(new Set());
                  }}
                />
              ) : null}
            </>
          }
          isFiltered={isFiltered}
          onReset={resetFilters}
          viewOptions={
            <DataTableViewOptions
              options={COLUMN_OPTIONS}
              visible={visibleColumns}
              onToggle={(key, visible) =>
                setVisibleColumns((current) => ({ ...current, [key]: visible }))
              }
            />
          }
        />

        {groupsQuery.isError ? (
          <div className="flex min-h-48 flex-col items-center justify-center gap-3 p-6 text-center">
            <UsersRound className="size-8 text-muted-foreground" />
            <div className="space-y-1">
              <p className="text-sm font-medium">无法加载用户组</p>
              <p className="text-xs text-muted-foreground">
                {resolveUserGroupApiError(groupsQuery.error)}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={() => void groupsQuery.refetch()}>
              重试
            </Button>
          </div>
        ) : (
          <div className="overflow-hidden rounded-md border">
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id} className="group/row">
                    {headerGroup.headers.map((header) => (
                      <TableHead
                        key={header.id}
                        colSpan={header.colSpan}
                        className={cn(
                          "bg-background px-4 group-hover/row:bg-muted group-data-[state=selected]/row:bg-muted",
                        )}
                      >
                        {header.isPlaceholder ? null : <table.FlexRender header={header} />}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {groupsQuery.isPending ? (
                  Array.from({ length: Math.min(size, 6) }, (_, index) => (
                    <TableRow key={index}>
                      <TableCell colSpan={columns.length}>
                        <Skeleton className="h-10 w-full" />
                      </TableCell>
                    </TableRow>
                  ))
                ) : table.getRowModel().rows.length === 0 ? (
                  <TableRow>
                    <TableCell
                      colSpan={columns.length}
                      className="h-32 text-center text-muted-foreground"
                    >
                      {groups.length === 0
                        ? "暂无用户组，先创建一个用户组。"
                        : "没有找到匹配的用户组。"}
                    </TableCell>
                  </TableRow>
                ) : (
                  table.getRowModel().rows.map((row) => {
                    const selectedRow = selected.has(row.original.metadata.name);

                    return (
                      <TableRow
                        key={row.id}
                        data-state={selectedRow ? "selected" : undefined}
                        className="group/row"
                      >
                        {row.getAllCells().map((cell) => (
                          <TableCell
                            key={cell.id}
                            className="bg-background px-4 group-hover/row:bg-muted group-data-[state=selected]/row:bg-muted"
                          >
                            <table.FlexRender cell={cell} />
                          </TableCell>
                        ))}
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>
        )}

        <DataTablePagination
          currentPage={currentPage}
          pageCount={pageCount}
          pageSize={size}
          onPageChange={setPage}
          onPageSizeChange={(value) => {
            setSize(value);
            setPage(1);
            setSelected(new Set());
          }}
          disabled={groupsQuery.isFetching}
          className="mt-auto"
        />

        {canSelect ? (
          <DataTableBulkActions
            selectedCount={selected.size}
            entityName="用户组"
            onClear={() => setSelected(new Set())}
          >
            <Button
              type="button"
              variant="destructive"
              size="sm"
              disabled={bulkDeleteMutation.isPending}
              onClick={() => setBulkDeleteOpen(true)}
            >
              {bulkDeleteMutation.isPending ? <Loader2 className="animate-spin" /> : <Trash2 />}
              删除
            </Button>
          </DataTableBulkActions>
        ) : null}
      </div>

      {createOpen ? <CreateUserGroupDialog onClose={() => setCreateOpen(false)} /> : null}
      {editTarget ? (
        <EditUserGroupDialog group={editTarget} onClose={() => setEditTarget(null)} />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除用户组"
        desc={
          deleteTarget
            ? `确定删除「${userGroupDisplayName(deleteTarget)}」？该用户组的成员将失去该组继承的角色，此操作无法恢复。`
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

      <ConfirmDialog
        open={bulkDeleteOpen}
        onOpenChange={setBulkDeleteOpen}
        title="批量删除用户组"
        desc={`确定删除选中的 ${selected.size} 个用户组？这些用户组的成员将失去对应角色，此操作无法恢复。`}
        confirmText="删除"
        destructive
        isLoading={bulkDeleteMutation.isPending}
        handleConfirm={() => bulkDeleteMutation.mutate([...selected])}
      />
    </ConsolePage>
  );
}
