"use client";

import { useMemo, useState } from "react";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";

import type { ListedUser, User } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    DownloadIcon,
    MailIcon,
    MobileIcon,
    MoreIcon,
    PlayCircleIcon,
    PoweroffIcon,
    RefreshIcon,
    SearchIcon,
    UploadIcon,
    UserAddIcon,
    UsergroupAddIcon,
} from "tdesign-icons-react";
import { Button, Card, Dropdown, Input, Select } from "tdesign-react";
import type { DropdownOption, TableProps } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useDownload } from "@/hooks/utils/use-download";
import { usePermission } from "@/hooks/utils/use-permission";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { useConsoleSession } from "../../../composables/_session";
import { useConsoleTableState } from "../../../composables/_table";
import { IdentityPageOverview } from "../components/identity-page-overview";
import { BatchCreateUsersModal } from "./components/batch-create-users-modal";
import { CreateUserModal } from "./components/create-user-modal";
import { ImportUsersModal } from "./components/import-users-modal";
import { ResetUserPasswordModal } from "./components/reset-user-password-modal";
import { UserAvatar } from "./components/user-avatar";
import { UserEditorModal } from "./components/user-editor-modal";
import { UserExpandedRow } from "./components/user-expanded-row";
import { UserGroupTag, UserStatusTag } from "./components/user-tags";
import { usersToCsv } from "./composables/_feishu-users";
import { useDeleteUser, useSetUserStatus } from "./composables/_user-actions";
import { useUserGroupOptions } from "./composables/_user-groups";
import { USER_PERMISSIONS } from "./composables/_user-permissions";
import { userDetailPath } from "./composables/_user-route";
import { useUserSummaryQuery } from "./composables/_user-summary";

const STATUS_OPTIONS = [
    { label: "正常", value: "active" },
    { label: "已禁用", value: "disabled" },
];

const SORT_OPTIONS = [
    { label: "创建时间从新到旧", value: "metadata.creationTimestamp,desc" },
    { label: "创建时间从旧到新", value: "metadata.creationTimestamp,asc" },
    { label: "用户名 A-Z", value: "metadata.name,asc" },
];

const EXPORT_PAGE_SIZE = 1000;

type UserRow = ListedUser & {
    __rowKey: string;
};

type BulkStatusResult = {
    changed: number;
    failures: { message: string; name: string }[];
    skipped: number;
};

type EditingUser = {
    detail: User;
    user: ListedUser;
};

export function UsersView() {
    const router = useRouter();
    const { workspace } = useParams<{ workspace: string }>();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const isMobile = useMobile();
    const { download } = useDownload();
    const { options: groupOptions, query: groupQuery } = useUserGroupOptions();
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        USER_PERMISSIONS.view,
    );
    const summaryQuery = useUserSummaryQuery(sessionQuery.isSuccess && canView);
    const canCreate = can(USER_PERMISSIONS.create);
    const canBatchCreate = can(USER_PERMISSIONS.batchCreate);
    const canImport = can(USER_PERMISSIONS.import);
    const canExport = can(USER_PERMISSIONS.export);
    const canBulk = can(USER_PERMISSIONS.bulk);
    const canUpdate = can(USER_PERMISSIONS.update);
    const canGroup = can(USER_PERMISSIONS.group);
    const canStatus = can(USER_PERMISSIONS.status);
    const canPassword = can(USER_PERMISSIONS.password);
    const canDelete = can(USER_PERMISSIONS.delete);
    const canSelect = canBulk || canExport;
    const canEditRow = canUpdate || canGroup;
    const [selectedUsers, setSelectedUsers] = useState<Map<string, UserRow>>(new Map());
    const [createOpen, setCreateOpen] = useState(false);
    const [batchOpen, setBatchOpen] = useState(false);
    const [importOpen, setImportOpen] = useState(false);
    const [editing, setEditing] = useState<EditingUser>();
    const [resetUser, setResetUser] = useState<ListedUser>();
    const [detailLoadingName, setDetailLoadingName] = useState<string>();
    const [exporting, setExporting] = useState(false);
    const status = table.filters.status ?? "";
    const userGroup = table.filters.userGroup ?? "";
    const activeSort = table.sorting[0];
    const sortValue = activeSort ? `${activeSort.id},${activeSort.desc ? "desc" : "asc"}` : "";
    const hasFilters = Boolean(table.keyword || status || userGroup || sortValue);
    const setStatus = useSetUserStatus();
    const deleteUser = useDeleteUser();
    const usersQuery = useQuery({
        queryKey: ["console", "users", "list", table.page, table.pageSize, table.keyword, status, userGroup, sortValue],
        queryFn: async () =>
            (
                await apiClient.user.listUsers({
                    keyword: table.keyword || undefined,
                    page: table.page,
                    size: table.pageSize,
                    sort: sortValue ? [sortValue] : undefined,
                    status: status || undefined,
                    userGroup: userGroup || undefined,
                })
            ).data,
    });
    const overviewMetrics = [
        { label: "用户总数", value: formatCountOrDash(summaryQuery.data?.total) },
        { label: "正常账号", value: formatCountOrDash(summaryQuery.data?.active) },
        { label: "已禁用", value: formatCountOrDash(summaryQuery.data?.disabled) },
        { label: "用户组", value: formatCountOrDash(groupQuery.data?.total) },
    ];
    const users = useMemo<UserRow[]>(
        () =>
            (usersQuery.data?.items ?? []).map((user, index) => ({
                ...user,
                __rowKey: user.name ?? `missing-name-${index}`,
            })),
        [usersQuery.data?.items],
    );
    const selectedRows = useMemo(() => [...selectedUsers.values()], [selectedUsers]);
    const refreshUsers = () => {
        void queryClient.invalidateQueries({ queryKey: ["console", "users"] });
    };
    const clearSelection = () => setSelectedUsers(new Map());
    const applyKeyword = (value: string) => {
        clearSelection();
        table.setKeyword(value);
    };
    const applyStatus = (value?: string) => {
        clearSelection();
        table.setFilter("status", value ?? null);
    };
    const applyGroup = (value?: string) => {
        clearSelection();
        table.setFilter("userGroup", value ?? null);
    };
    const applySort = (value?: string) => {
        clearSelection();

        if (!value) {
            table.setSort(null);
            return;
        }

        const [column, order] = value.split(",");
        table.setSort(column ?? null, order === "desc");
    };
    const resetFilters = () => {
        clearSelection();
        table.reset();
    };
    const toggleUser = (user: ListedUser) => {
        if (!user.name) {
            prompt.error("用户名称缺失。");
            return;
        }

        setStatus.mutate({ disabled: !user.disabled, name: user.name });
    };
    const openEdit = async (user: ListedUser) => {
        if (!user.name) {
            prompt.error("用户名称缺失。");
            return;
        }

        setDetailLoadingName(user.name);

        try {
            const detail = (await apiClient.user.getUser({ name: user.name })).data;
            setEditing({ detail, user });
        } catch (error) {
            prompt.error(consoleErrorMessage(error));
        } finally {
            setDetailLoadingName(undefined);
        }
    };
    const confirmDelete = (user: ListedUser) => {
        if (!user.name) {
            prompt.error("用户名称缺失。");
            return;
        }

        const name = user.name;

        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除用户 ${user.displayName ?? name} 吗？`,
            danger: true,
            onConfirm: () => {
                void deleteUser.mutateAsync(name, {
                    onSuccess: () => {
                        setSelectedUsers((previous) => {
                            const next = new Map(previous);
                            next.delete(name);
                            return next;
                        });
                    },
                });
            },
            title: "删除用户",
        });
    };
    const confirmBulkStatus = (disabled: boolean) => {
        if (selectedRows.length === 0) {
            return;
        }

        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: disabled ? "批量禁用" : "批量启用",
            content: `将会处理选中的 ${selectedRows.length} 个用户，已处于目标状态的账号会自动跳过。`,
            danger: disabled,
            onConfirm: () => {
                void bulkStatus.mutateAsync({ disabled, users: selectedRows });
            },
            title: disabled ? "批量禁用用户" : "批量启用用户",
        });
    };
    const bulkStatus = useMutation({
        mutationFn: async ({
            disabled,
            users: targets,
        }: {
            disabled: boolean;
            users: ListedUser[];
        }): Promise<BulkStatusResult> => {
            const results = await Promise.allSettled(
                targets.map(async (user) => {
                    if (!user.name) {
                        throw new Error("用户名称缺失。");
                    }

                    if (Boolean(user.disabled) === disabled) {
                        return { name: user.name, skipped: true };
                    }

                    if (disabled) {
                        await apiClient.user.disableUser({ username: user.name });
                    } else {
                        await apiClient.user.enableUser({ username: user.name });
                    }

                    return { name: user.name, skipped: false };
                }),
            );
            const failures: BulkStatusResult["failures"] = [];
            let changed = 0;
            let skipped = 0;

            results.forEach((result, index) => {
                if (result.status === "rejected") {
                    failures.push({
                        message: consoleErrorMessage(result.reason),
                        name: targets[index]?.name ?? "未知用户",
                    });
                    return;
                }

                if (result.value.skipped) {
                    skipped += 1;
                } else {
                    changed += 1;
                }
            });

            return { changed, failures, skipped };
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (result) => {
            if (result.changed > 0) {
                refreshUsers();
            }

            if (result.failures.length > 0) {
                const failedNames = new Set(result.failures.map((failure) => failure.name));
                setSelectedUsers(
                    (previous) =>
                        new Map(
                            [...previous].filter(
                                ([, user]) => Boolean(user.name) && failedNames.has(user.name as string),
                            ),
                        ),
                );
                prompt.warning({
                    content: (
                        <div className="grid gap-1">
                            {result.failures.slice(0, 8).map((failure) => (
                                <div key={failure.name}>
                                    {failure.name}：{failure.message}
                                </div>
                            ))}
                            {result.failures.length > 8 ? (
                                <div>另有 {result.failures.length - 8} 个用户处理失败。</div>
                            ) : null}
                        </div>
                    ),
                    duration: 8000,
                    title: `已更新 ${result.changed} 个用户，${result.failures.length} 个失败`,
                });
                return;
            }

            clearSelection();

            if (result.changed > 0) {
                prompt.success(
                    result.skipped > 0
                        ? `已更新 ${result.changed} 个用户，${result.skipped} 个已是目标状态。`
                        : `已更新 ${result.changed} 个用户。`,
                );
                return;
            }

            prompt.info("所选用户已是目标状态。");
        },
    });
    const listUsersForExport = async () => {
        const collected: ListedUser[] = [];
        let page = 1;

        while (true) {
            const result = (
                await apiClient.user.listUsers({
                    keyword: table.keyword || undefined,
                    page,
                    size: EXPORT_PAGE_SIZE,
                    sort: sortValue ? [sortValue] : undefined,
                    status: status || undefined,
                    userGroup: userGroup || undefined,
                })
            ).data;
            const items = result.items ?? [];

            collected.push(...items);

            if (items.length === 0 || collected.length >= (result.total ?? collected.length)) {
                return collected;
            }

            const totalPages = Math.ceil((result.total ?? collected.length) / EXPORT_PAGE_SIZE);
            if (page >= totalPages) {
                return collected;
            }

            page += 1;
        }
    };
    const exportRows = async (rows?: ListedUser[]) => {
        if (!canExport) {
            return;
        }

        setExporting(true);

        try {
            const targets = rows ?? (await listUsersForExport());

            if (targets.length === 0) {
                prompt.warning("没有可导出的用户。");
                return;
            }

            const stamp = new Date().toISOString().replace(/\D/g, "").slice(0, 14);
            download(usersToCsv(targets), `users-${stamp}.csv`, "text/csv;charset=utf-8");
            prompt.success(`已导出 ${targets.length} 个用户。`);
        } catch (error) {
            prompt.error(consoleErrorMessage(error));
        } finally {
            setExporting(false);
        }
    };
    const columns: NonNullable<TableProps<UserRow>["columns"]> = [
        {
            colKey: "row-select",
            disabled: ({ row }) => !row.name,
            type: "multiple",
            width: 48,
        },
        {
            cell: ({ row }) => {
                const name = row.displayName || row.name || "未命名用户";

                return (
                    <div className="flex min-w-0 items-center gap-2.5">
                        <UserAvatar name={name} size={36} />
                        <div className="min-w-0">
                            {row.name ? (
                                <Link className="block truncate font-medium" href={userDetailPath(workspace, row.name)}>
                                    {name}
                                </Link>
                            ) : (
                                <span className="block truncate font-medium">{name}</span>
                            )}
                        </div>
                    </div>
                );
            },
            colKey: "user",
            title: "用户",
            width: 200,
        },
        ...(isMobile
            ? []
            : [
                  {
                      cell: ({ row }: { row: UserRow }) => {
                          const loginName = row.loginName || row.name;

                          return (
                              <span className="block truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                                  {loginName ? `@${loginName}` : <EmptyValue />}
                              </span>
                          );
                      },
                      colKey: "login",
                      title: "登录名",
                      width: 140,
                  },
                  {
                      cell: ({ row }: { row: UserRow }) => (
                          <UserGroupTag displayName={row.userGroupDisplayName} name={row.userGroup} />
                      ),
                      colKey: "group",
                      title: "用户组",
                      width: 130,
                  },
                  {
                      cell: ({ row }: { row: UserRow }) => (
                          <div className="flex min-w-0 flex-col gap-1">
                              {row.email ? (
                                  <span className="flex min-w-0 items-center gap-1.5 text-xs">
                                      <MailIcon
                                          className="shrink-0 text-[var(--td-text-color-placeholder)]"
                                          size={14}
                                      />
                                      <span className="truncate">{row.email}</span>
                                  </span>
                              ) : null}
                              {row.phone ? (
                                  <span className="flex min-w-0 items-center gap-1.5 text-xs">
                                      <MobileIcon
                                          className="shrink-0 text-[var(--td-text-color-placeholder)]"
                                          size={14}
                                      />
                                      <span className="truncate">{row.phone}</span>
                                  </span>
                              ) : null}
                              {!row.email && !row.phone ? <EmptyValue /> : null}
                          </div>
                      ),
                      colKey: "contact",
                      title: "联系方式",
                      width: 180,
                  },
                  {
                      cell: ({ row }: { row: UserRow }) => (
                          <span className="whitespace-nowrap tabular-nums">
                              {row.registeredAt ? formatDateTime(row.registeredAt) : "未填写"}
                          </span>
                      ),
                      colKey: "registeredAt",
                      title: "创建时间",
                      width: 170,
                  },
                  {
                      cell: ({ row }: { row: UserRow }) => <UserStatusTag disabled={row.disabled} />,
                      colKey: "status",
                      title: "状态",
                      width: 90,
                  },
              ]),
        {
            cell: ({ row }) => {
                const statusPending = setStatus.isPending && setStatus.variables?.name === row.name;
                const options: DropdownOption[] = [
                    { content: "查看详情", disabled: !row.name, value: "detail" },
                    ...(canStatus
                        ? [
                              {
                                  content: row.disabled ? "启用用户" : "禁用用户",
                                  disabled: statusPending,
                                  prefixIcon: row.disabled ? <PlayCircleIcon /> : <PoweroffIcon />,
                                  value: "toggle",
                              },
                          ]
                        : []),
                    ...(canPassword ? [{ content: "重置密码", value: "password" }] : []),
                    ...(canDelete
                        ? [
                              { divider: true, value: "divider" },
                              { content: "删除用户", theme: "error" as const, value: "delete" },
                          ]
                        : []),
                ];

                return (
                    <div className="flex items-center gap-1">
                        {canEditRow ? (
                            <Button
                                loading={detailLoadingName === row.name}
                                onClick={() => void openEdit(row)}
                                size="small"
                                variant="text"
                            >
                                编辑
                            </Button>
                        ) : null}
                        <Dropdown
                            onClick={(item) => {
                                if (item.value === "detail" && row.name) {
                                    router.push(userDetailPath(workspace, row.name));
                                } else if (item.value === "toggle") {
                                    toggleUser(row);
                                } else if (item.value === "password") {
                                    setResetUser(row);
                                } else if (item.value === "delete") {
                                    confirmDelete(row);
                                }
                            }}
                            options={options}
                            trigger="click"
                        >
                            <Button
                                aria-label="更多用户操作"
                                icon={<MoreIcon />}
                                shape="square"
                                size="small"
                                variant="text"
                            />
                        </Dropdown>
                    </div>
                );
            },
            colKey: "actions",
            title: "操作",
            width: 120,
        },
    ];
    const handleSelectChange = (keys: Array<string | number>) => {
        const selectedKeys = new Set(keys.map(String));

        setSelectedUsers((previous) => {
            const next = new Map(previous);

            users.forEach((user) => {
                if (!selectedKeys.has(user.__rowKey)) {
                    next.delete(user.__rowKey);
                }
            });
            users.forEach((user) => {
                if (selectedKeys.has(user.__rowKey) && user.name) {
                    next.set(user.__rowKey, user);
                }
            });

            return next;
        });
    };
    const moreItems: DropdownOption[] = [];

    if (canBatchCreate) {
        moreItems.push({
            content: "批量添加用户",
            prefixIcon: <UsergroupAddIcon />,
            value: "batch-create",
        });
    }
    if (canImport) {
        moreItems.push({ content: "导入用户", prefixIcon: <UploadIcon />, value: "import" });
    }
    if (moreItems.length > 0) {
        moreItems.push({ divider: true, value: "divider" });
    }
    moreItems.push({ content: "刷新列表", prefixIcon: <RefreshIcon />, value: "refresh" });
    const bulkItems: DropdownOption[] = [];

    if (canExport) {
        bulkItems.push({ content: "导出选中", prefixIcon: <DownloadIcon />, value: "export-selected" });
    }
    if (canBulk) {
        bulkItems.push({ content: "批量启用", prefixIcon: <PlayCircleIcon />, value: "enable" });
        bulkItems.push({
            content: "批量禁用",
            prefixIcon: <PoweroffIcon />,
            theme: "error",
            value: "disable",
        });
    }
    if (bulkItems.length > 0) {
        bulkItems.push({ divider: true, value: "divider" });
    }
    bulkItems.push({ content: "取消选择", value: "clear" });
    const handleBulkAction = (key: string) => {
        if (key === "export-selected") {
            void exportRows(selectedRows);
        } else if (key === "enable") {
            confirmBulkStatus(false);
        } else if (key === "disable") {
            confirmBulkStatus(true);
        } else if (key === "clear") {
            clearSelection();
        }
    };

    return (
        <>
            <div className="flex w-full flex-col gap-4">
                <IdentityPageOverview
                    actions={
                        <>
                            {canCreate ? (
                                <Button icon={<UserAddIcon />} onClick={() => setCreateOpen(true)} theme="primary">
                                    新建用户
                                </Button>
                            ) : null}
                            <Button
                                icon={<RefreshIcon />}
                                loading={usersQuery.isFetching || summaryQuery.isFetching}
                                onClick={refreshUsers}
                                variant="outline"
                            >
                                刷新
                            </Button>
                        </>
                    }
                    icon={<UsergroupAddIcon />}
                    metrics={overviewMetrics}
                    title="用户目录"
                />
                <Card>
                    <div className="flex w-full flex-wrap items-center gap-3">
                        <Select
                            className="min-w-0 flex-1 basis-52"
                            clearable
                            onChange={(value) => applyStatus(value ? String(value) : undefined)}
                            options={STATUS_OPTIONS}
                            placeholder="账号状态"
                            value={status || undefined}
                        />
                        <Select
                            className="min-w-0 flex-1 basis-52"
                            clearable
                            filterable
                            onChange={(value) => applyGroup(value ? String(value) : undefined)}
                            options={groupOptions}
                            placeholder="用户组"
                            value={userGroup || undefined}
                        />
                        <Select
                            className="min-w-0 flex-1 basis-52"
                            clearable
                            onChange={(value) => applySort(value ? String(value) : undefined)}
                            options={SORT_OPTIONS}
                            placeholder="排序方式"
                            value={sortValue || undefined}
                        />
                        {hasFilters ? (
                            <Button onClick={resetFilters} variant="text">
                                重置筛选
                            </Button>
                        ) : null}
                    </div>
                </Card>
                <ConsoleTable<UserRow>
                    columns={columns}
                    data={users}
                    error={usersQuery.error}
                    loading={usersQuery.isFetching}
                    onRetry={usersQuery.error ? () => void usersQuery.refetch() : undefined}
                    pagination={{ ...table.pagination, total: usersQuery.data?.total ?? 0 }}
                    rowKey="__rowKey"
                    tableProps={{
                        expandedRow: ({ row }) => (
                            <UserExpandedRow
                                canEdit={canEditRow}
                                onEdit={() => void openEdit(row)}
                                user={row}
                                workspace={workspace}
                            />
                        ),
                        onSelectChange: handleSelectChange,
                        selectedRowKeys: selectedRows.map((user) => user.__rowKey),
                    }}
                    title="用户"
                    toolbar={
                        <div className="flex w-full flex-col gap-2 sm:flex-row sm:items-center">
                            <Input
                                className="w-full sm:max-w-xs"
                                clearable
                                defaultValue={table.keyword}
                                key={table.keyword}
                                onClear={() => applyKeyword("")}
                                onEnter={(value) => applyKeyword(String(value))}
                                placeholder="搜索显示名称或邮箱"
                                prefixIcon={<SearchIcon />}
                            />
                            <div className="flex items-center gap-2 sm:ml-auto">
                                {canExport ? (
                                    <Button
                                        icon={<DownloadIcon />}
                                        loading={exporting}
                                        onClick={() => void exportRows()}
                                        variant="outline"
                                    >
                                        导出
                                    </Button>
                                ) : null}
                                {canSelect && selectedRows.length > 0 ? (
                                    <Dropdown
                                        onClick={(item) => handleBulkAction(String(item.value))}
                                        options={bulkItems}
                                        trigger="click"
                                    >
                                        <Button icon={<PlayCircleIcon />} variant="outline">
                                            批量操作
                                        </Button>
                                    </Dropdown>
                                ) : null}
                                <Dropdown
                                    onClick={(item) => {
                                        if (item.value === "batch-create") {
                                            setBatchOpen(true);
                                        } else if (item.value === "import") {
                                            setImportOpen(true);
                                        } else if (item.value === "refresh") {
                                            refreshUsers();
                                        }
                                    }}
                                    options={moreItems}
                                    trigger="click"
                                >
                                    <Button
                                        aria-label="更多操作"
                                        icon={<MoreIcon />}
                                        shape="square"
                                        variant="outline"
                                    />
                                </Dropdown>
                            </div>
                        </div>
                    }
                />
            </div>
            {createOpen ? <CreateUserModal groupOptions={groupOptions} onClose={() => setCreateOpen(false)} /> : null}
            {batchOpen ? (
                <BatchCreateUsersModal groupOptions={groupOptions} onClose={() => setBatchOpen(false)} />
            ) : null}
            {importOpen ? <ImportUsersModal groupOptions={groupOptions} onClose={() => setImportOpen(false)} /> : null}
            {editing ? (
                <UserEditorModal
                    canGroup={canGroup}
                    canUpdate={canUpdate}
                    currentGroup={editing.user.userGroup}
                    detail={editing.detail}
                    groupOptions={groupOptions}
                    onClose={() => setEditing(undefined)}
                    onSaved={() => setEditing(undefined)}
                />
            ) : null}
            {resetUser ? (
                <ResetUserPasswordModal
                    displayName={resetUser.displayName}
                    name={resetUser.name ?? ""}
                    onClose={() => setResetUser(undefined)}
                    onSaved={() => setResetUser(undefined)}
                />
            ) : null}
        </>
    );
}
