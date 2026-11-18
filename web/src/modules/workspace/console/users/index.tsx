import { useCallback, useEffect, useMemo, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { type ColumnDef, createColumnHelper, tableFeatures, useTable } from "@tanstack/react-table";
import { format } from "date-fns";
import {
  Ban,
  CheckCircle2,
  Download,
  Eye,
  ListPlus,
  Loader2,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Upload,
  Users,
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
import { downloadCsv } from "@/lib/csv";
import { cn } from "@/lib/utils";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";
import { useUserGroups } from "@/modules/workspace/lib/user-groups";

import { BatchCreateUsersDialog } from "./components/batch-create-users-dialog";
import { CreateUserDialog } from "./components/create-user-dialog";
import { ImportUsersDialog } from "./components/import-users-dialog";
import { UserAvatar } from "./components/user-avatar";
import { type UserListItem, fetchAllUsers, fetchUsers, resolveUserApiError } from "./lib/api";
import { usersToCsv } from "./lib/user-csv";

const USER_TABLE_FEATURES = tableFeatures({});
const userColumnHelper = createColumnHelper<typeof USER_TABLE_FEATURES, UserListItem>();
const EMPTY_USERS: UserListItem[] = [];

type UserColumn = ColumnDef<typeof USER_TABLE_FEATURES, UserListItem, unknown>;

type SortField = "metadata.name" | "spec.email";

type SortState = {
  field: SortField;
  direction: DataTableSortDirection;
};

const STATUS_OPTIONS = [
  { label: "正常", value: "active" },
  { label: "已禁用", value: "disabled" },
] as const;

const COLUMN_OPTIONS = [
  { key: "name", label: "用户名" },
  { key: "displayName", label: "姓名" },
  { key: "email", label: "邮箱" },
  { key: "phone", label: "手机号" },
  { key: "disabled", label: "状态" },
  { key: "userGroup", label: "用户组" },
] as const;

export function UsersView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const usersPath = useMenuPath("users");
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statusFilters, setStatusFilters] = useState<string[]>([]);
  const [groupFilters, setGroupFilters] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [sort, setSort] = useState<SortState>({ field: "metadata.name", direction: "asc" });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [visibleColumns, setVisibleColumns] = useState<Record<string, boolean>>({
    name: true,
    displayName: true,
    email: true,
    phone: true,
    disabled: true,
    userGroup: true,
  });
  const [createOpen, setCreateOpen] = useState(false);
  const [batchCreateOpen, setBatchCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [statusTarget, setStatusTarget] = useState<UserListItem | null>(null);

  const canCreate = allowedOperations.has("create");
  const canBatchCreate = allowedOperations.has("batch-create");
  const canImport = allowedOperations.has("import");
  const canExport = allowedOperations.has("export");
  const canBulk = allowedOperations.has("bulk");
  const canChangeStatus = allowedOperations.has("toggle-status");
  const canSelect = canBulk || canExport;

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(searchInput.trim());
      setPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const usersQuery = useQuery({
    queryKey: ["users", page, size, keyword, statusFilters, groupFilters, sort],
    queryFn: () =>
      fetchUsers({
        page,
        size,
        keyword: keyword || undefined,
        status: statusFilters.length > 0 ? statusFilters.join(",") : undefined,
        userGroup: groupFilters.length > 0 ? groupFilters.join(",") : undefined,
        sort: `${sort.field},${sort.direction}`,
      }),
    placeholderData: keepPreviousData,
    retry: false,
  });

  const userGroupsQuery = useUserGroups();
  const userGroupOptions = (userGroupsQuery.data ?? []).map((group) => ({
    value: group.metadata.name,
    label: group.displayName || group.metadata.name,
  }));

  const users = usersQuery.data?.items ?? EMPTY_USERS;
  const total = usersQuery.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / size));
  const currentPage = Math.min(page, pageCount);
  const visibleNames = users
    .map((user) => user.name)
    .filter((name): name is string => Boolean(name));
  const allSelected = visibleNames.length > 0 && visibleNames.every((name) => selected.has(name));
  const someSelected = visibleNames.some((name) => selected.has(name)) && !allSelected;
  const isFiltered =
    searchInput.trim().length > 0 || statusFilters.length > 0 || groupFilters.length > 0;

  const statusMutation = useMutation({
    mutationFn: async (user: UserListItem) => {
      if (!user.name) {
        throw new Error("用户名为空。");
      }
      if (user.disabled) {
        await apiClient.user.enableUser({ username: user.name });
      } else {
        await apiClient.user.disableUser({ username: user.name });
      }
    },
    onSuccess: async (_result, user) => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      setStatusTarget(null);
      toast.success(user.disabled ? "用户已启用。" : "用户已禁用。");
    },
    onError: (error) => {
      setStatusTarget(null);
      toast.error(resolveUserApiError(error));
    },
  });

  const bulkMutation = useMutation({
    mutationFn: async ({ action, names }: { action: "enable" | "disable"; names: string[] }) => {
      const results = await Promise.allSettled(
        names.map((name) =>
          action === "enable"
            ? apiClient.user.enableUser({ username: name })
            : apiClient.user.disableUser({ username: name }),
        ),
      );
      return {
        total: names.length,
        failed: results.filter((result) => result.status === "rejected").length,
      };
    },
    onSuccess: async ({ total: count, failed }) => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      setSelected(new Set());
      if (failed > 0) {
        toast.error(`${failed} / ${count} 个用户操作失败。`);
      } else {
        toast.success(`已处理 ${count} 个用户。`);
      }
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  const downloadUsersCsv = (items: UserListItem[], prefix: string) => {
    if (items.length === 0) {
      return 0;
    }
    downloadCsv(`users-${prefix}-${format(new Date(), "yyyyMMdd-HHmm")}.csv`, usersToCsv(items));
    return items.length;
  };

  const notifyExported = (count: number) => {
    if (count === 0) {
      toast.info("没有可导出的用户。");
    } else {
      toast.success(`已导出 ${count} 个用户。`);
    }
  };

  const exportMutation = useMutation({
    mutationFn: async () => {
      const allUsers = await fetchAllUsers({
        keyword: keyword || undefined,
        status: statusFilters.length > 0 ? statusFilters.join(",") : undefined,
        userGroup: groupFilters.length > 0 ? groupFilters.join(",") : undefined,
        sort: `${sort.field},${sort.direction}`,
      });
      return downloadUsersCsv(allUsers, "filtered");
    },
    onSuccess: notifyExported,
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  const exportSelectedMutation = useMutation({
    mutationFn: async (names: string[]) => {
      if (names.length === 0) {
        return 0;
      }
      const allUsers = await fetchAllUsers({
        keyword: keyword || undefined,
        status: statusFilters.length > 0 ? statusFilters.join(",") : undefined,
        userGroup: groupFilters.length > 0 ? groupFilters.join(",") : undefined,
        sort: `${sort.field},${sort.direction}`,
      });
      const selectedNames = new Set(names);
      const selectedUsers = allUsers.filter((user) => user.name && selectedNames.has(user.name));
      return downloadUsersCsv(selectedUsers, "selected");
    },
    onSuccess: notifyExported,
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  const toggleAll = useCallback(() => {
    setSelected((current) => {
      const next = new Set(current);
      if (allSelected) {
        visibleNames.forEach((name) => next.delete(name));
      } else {
        visibleNames.forEach((name) => next.add(name));
      }
      return next;
    });
  }, [allSelected, visibleNames]);

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

  const resetFilters = () => {
    setSearchInput("");
    setKeyword("");
    setStatusFilters([]);
    setGroupFilters([]);
    setPage(1);
    setSelected(new Set());
  };

  const columns = useMemo<UserColumn[]>(() => {
    const allColumns = [
      {
        key: "select",
        column: userColumnHelper.display({
          id: "select",
          header: () => (
            <Checkbox
              checked={allSelected ? true : someSelected ? "indeterminate" : false}
              onCheckedChange={toggleAll}
              aria-label="选择当前页全部用户"
              className="translate-y-0.5"
            />
          ),
          cell: ({ row }) => {
            const name = row.original.name;
            return (
              <Checkbox
                checked={Boolean(name && selected.has(name))}
                disabled={!name}
                onCheckedChange={(checked) => {
                  if (name) {
                    toggleOne(name, Boolean(checked));
                  }
                }}
                aria-label={`选择用户 ${name ?? ""}`}
                className="translate-y-0.5"
              />
            );
          },
        }),
      },
      {
        key: "name",
        column: userColumnHelper.accessor("name", {
          header: () => (
            <DataTableColumnHeader
              title="用户名"
              direction={sort.field === "metadata.name" ? sort.direction : undefined}
              onSort={(direction) => {
                setSort({ field: "metadata.name", direction });
                setPage(1);
              }}
              canHide
              onHide={() => setVisibleColumns((current) => ({ ...current, name: false }))}
            />
          ),
          cell: ({ row }) => (
            <LongText className="max-w-36 ps-3 font-mono text-sm">
              {row.original.name || "—"}
            </LongText>
          ),
        }),
      },
      {
        key: "displayName",
        column: userColumnHelper.accessor("displayName", {
          header: "姓名",
          cell: ({ row }) => {
            const content = (
              <>
                <UserAvatar
                  name={row.original.name}
                  displayName={row.original.displayName}
                  className="size-8 shrink-0"
                />
                <LongText className="max-w-36 text-sm font-medium">
                  {row.original.displayName || "未命名用户"}
                </LongText>
              </>
            );
            return usersPath ? (
              <Link
                to={`${usersPath}/${row.original.name ?? ""}` as string}
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
        key: "email",
        column: userColumnHelper.accessor("email", {
          header: () => (
            <DataTableColumnHeader
              title="邮箱"
              direction={sort.field === "spec.email" ? sort.direction : undefined}
              onSort={(direction) => {
                setSort({ field: "spec.email", direction });
                setPage(1);
              }}
              canHide
              onHide={() => setVisibleColumns((current) => ({ ...current, email: false }))}
            />
          ),
          cell: ({ row }) => (
            <div className="w-fit text-sm text-nowrap text-muted-foreground">
              {row.original.email || "—"}
            </div>
          ),
        }),
      },
      {
        key: "phone",
        column: userColumnHelper.accessor("phone", {
          header: "手机号",
          cell: ({ row }) => (
            <div className="text-sm text-muted-foreground">{row.original.phone || "—"}</div>
          ),
        }),
      },
      {
        key: "disabled",
        column: userColumnHelper.accessor("disabled", {
          header: "状态",
          cell: ({ row }) => (
            <Badge variant={row.original.disabled ? "destructive" : "secondary"}>
              {row.original.disabled ? "已禁用" : "正常"}
            </Badge>
          ),
        }),
      },
      {
        key: "userGroup",
        column: userColumnHelper.accessor("userGroupDisplayName", {
          header: "用户组",
          cell: ({ row }) => {
            const label = row.original.userGroupDisplayName || row.original.userGroup;
            if (!label) {
              return <span className="text-sm text-muted-foreground">—</span>;
            }
            return <Badge variant="secondary">{label}</Badge>;
          },
        }),
      },
      {
        key: "actions",
        column: userColumnHelper.display({
          id: "actions",
          header: () => <span className="sr-only">操作</span>,
          cell: ({ row }) => {
            const user = row.original;
            return (
              <DropdownMenu modal={false}>
                <DropdownMenuTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    className="flex h-8 w-8 p-0 data-[state=open]:bg-muted"
                    aria-label="用户操作"
                  >
                    <MoreHorizontal className="size-4" />
                    <span className="sr-only">打开菜单</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-40">
                  {usersPath ? (
                    <DropdownMenuItem asChild>
                      <Link to={`${usersPath}/${user.name ?? ""}` as string}>
                        <Eye />
                        查看详情
                      </Link>
                    </DropdownMenuItem>
                  ) : null}
                  <DropdownMenuItem onSelect={() => setStatusTarget(user)}>
                    {user.disabled ? <CheckCircle2 /> : <Ban />}
                    {user.disabled ? "启用用户" : "禁用用户"}
                  </DropdownMenuItem>
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
          return canChangeStatus;
        }
        return visibleColumns[item.key] !== false;
      })
      .map((item) => item.column) as unknown as UserColumn[];
  }, [
    allSelected,
    canChangeStatus,
    canSelect,
    someSelected,
    selected,
    sort,
    toggleAll,
    toggleOne,
    usersPath,
    visibleColumns,
  ]);

  const table = useTable({
    features: USER_TABLE_FEATURES,
    columns,
    data: users,
  });

  return (
    <ConsolePage title="用户">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-2xl font-bold tracking-tight">用户列表</h2>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => void usersQuery.refetch()}
              disabled={usersQuery.isFetching}
            >
              {usersQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            {canExport ? (
              <Button
                type="button"
                variant="outline"
                title="导出当前筛选结果"
                onClick={() => exportMutation.mutate()}
                disabled={exportMutation.isPending}
              >
                {exportMutation.isPending ? <Loader2 className="animate-spin" /> : <Download />}
                导出
              </Button>
            ) : null}
            {canImport ? (
              <Button type="button" variant="outline" onClick={() => setImportOpen(true)}>
                <Upload />
                导入
              </Button>
            ) : null}
            {canBatchCreate ? (
              <Button type="button" variant="outline" onClick={() => setBatchCreateOpen(true)}>
                <ListPlus />
                批量创建
              </Button>
            ) : null}
            {canCreate ? (
              <Button type="button" onClick={() => setCreateOpen(true)}>
                <Plus />
                创建用户
              </Button>
            ) : null}
          </div>
        </div>

        <DataTableToolbar
          searchPlaceholder="筛选用户..."
          searchValue={searchInput}
          onSearchChange={(value) => {
            setSearchInput(value);
            setSelected(new Set());
          }}
          filters={
            <>
              <DataTableFacetedFilter
                title="状态"
                options={STATUS_OPTIONS}
                selected={statusFilters}
                onSelectedChange={(values) => {
                  setStatusFilters(values);
                  setPage(1);
                  setSelected(new Set());
                }}
              />
              <DataTableFacetedFilter
                title="用户组"
                options={userGroupOptions}
                selected={groupFilters}
                onSelectedChange={(values) => {
                  setGroupFilters(values);
                  setPage(1);
                  setSelected(new Set());
                }}
              />
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

        {usersQuery.isError ? (
          <div className="flex min-h-48 flex-col items-center justify-center gap-3 p-6 text-center">
            <Users className="size-8 text-muted-foreground" />
            <div className="space-y-1">
              <p className="text-sm font-medium">无法加载用户</p>
              <p className="text-xs text-muted-foreground">
                {resolveUserApiError(usersQuery.error)}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={() => void usersQuery.refetch()}>
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
                {usersQuery.isPending ? (
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
                      没有找到匹配的用户。
                    </TableCell>
                  </TableRow>
                ) : (
                  table.getRowModel().rows.map((row) => {
                    const selectedRow = Boolean(
                      row.original.name && selected.has(row.original.name),
                    );
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
          disabled={usersQuery.isFetching}
          className="mt-auto"
        />

        {canSelect ? (
          <DataTableBulkActions
            selectedCount={selected.size}
            entityName="用户"
            onClear={() => setSelected(new Set())}
          >
            {canExport ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={exportSelectedMutation.isPending}
                onClick={() => exportSelectedMutation.mutate([...selected])}
              >
                {exportSelectedMutation.isPending ? (
                  <Loader2 className="animate-spin" />
                ) : (
                  <Download />
                )}
                导出
              </Button>
            ) : null}
            {canBulk ? (
              <>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={bulkMutation.isPending}
                  onClick={() => bulkMutation.mutate({ action: "enable", names: [...selected] })}
                >
                  <CheckCircle2 />
                  启用
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={bulkMutation.isPending}
                  onClick={() => bulkMutation.mutate({ action: "disable", names: [...selected] })}
                >
                  <Ban />
                  禁用
                </Button>
              </>
            ) : null}
          </DataTableBulkActions>
        ) : null}
      </div>

      {createOpen ? <CreateUserDialog onClose={() => setCreateOpen(false)} /> : null}
      {batchCreateOpen ? (
        <BatchCreateUsersDialog onClose={() => setBatchCreateOpen(false)} />
      ) : null}
      {importOpen ? <ImportUsersDialog onClose={() => setImportOpen(false)} /> : null}

      <ConfirmDialog
        open={Boolean(statusTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setStatusTarget(null);
          }
        }}
        title={statusTarget?.disabled ? "启用用户" : "禁用用户"}
        desc={
          statusTarget?.disabled
            ? `确定启用「${statusTarget.displayName || statusTarget.name}」？`
            : `确定禁用「${statusTarget?.displayName || statusTarget?.name || ""}」？禁用后该用户的会话和设备会被注销。`
        }
        confirmText={statusTarget?.disabled ? "启用" : "禁用"}
        destructive={!statusTarget?.disabled}
        isLoading={statusMutation.isPending}
        handleConfirm={() => {
          if (statusTarget) {
            statusMutation.mutate(statusTarget);
          }
        }}
      />
    </ConsolePage>
  );
}
