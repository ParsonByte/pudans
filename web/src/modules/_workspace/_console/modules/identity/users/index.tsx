"use client";

import { useMemo, useState } from "react";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";

import { type ProColumns, ProTable } from "@ant-design/pro-components";
import type { ListedUser, User } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Dropdown, Flex, Input, type MenuProps, Select, Space } from "antd";
import {
    ChevronDown,
    Download,
    ListChecks,
    Mail,
    MoreHorizontal,
    Power,
    PowerOff,
    RefreshCw,
    Search,
    Smartphone,
    Upload,
    UserPlus,
    UsersRound,
} from "lucide-react";

import { SectionCard } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useDownload } from "@/hooks/utils/use-download";
import { usePermission } from "@/hooks/utils/use-permission";

import { EmptyValue } from "../../../components/empty-value";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatDateTime } from "../../../composables/_format";
import { useConsoleSession } from "../../../composables/_session";
import { useConsoleTableState } from "../../../composables/_table";
import { BatchCreateUsersModal } from "./components/batch-create-users-modal";
import { CreateUserModal } from "./components/create-user-modal";
import { ImportUsersModal } from "./components/import-users-modal";
import { ResetUserPasswordModal } from "./components/reset-user-password-modal";
import { UserAvatar } from "./components/user-avatar";
import { UserEditorModal } from "./components/user-editor-modal";
import { UserExpandIcon } from "./components/user-expand-icon";
import { UserExpandedRow } from "./components/user-expanded-row";
import { UserGroupTag, UserStatusTag } from "./components/user-tags";
import { usersToCsv } from "./composables/_feishu-users";
import { useDeleteUser, useSetUserStatus } from "./composables/_user-actions";
import { useUserGroupOptions } from "./composables/_user-groups";
import { USER_PERMISSIONS } from "./composables/_user-permissions";
import { userDetailPath } from "./composables/_user-route";

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
    const { modal } = App.useApp();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const { download } = useDownload();
    const { options: groupOptions } = useUserGroupOptions();
    const sessionQuery = useConsoleSession();
    const { can } = usePermission({ permissions: sessionQuery.data?.uiPermissions }, USER_PERMISSIONS.view);
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
    const [selectedUsers, setSelectedUsers] = useState<Map<string, ListedUser>>(new Map());
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
    const users = useMemo(() => usersQuery.data?.items ?? [], [usersQuery.data?.items]);
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

        modal.confirm({
            cancelText: "取消",
            content: `确定删除用户 ${user.displayName ?? name} 吗？`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () =>
                deleteUser.mutateAsync(name, {
                    onSuccess: () => {
                        setSelectedUsers((previous) => {
                            const next = new Map(previous);
                            next.delete(name);
                            return next;
                        });
                    },
                }),
            title: "删除用户",
        });
    };
    const confirmBulkStatus = (disabled: boolean) => {
        if (selectedRows.length === 0) {
            return;
        }

        modal.confirm({
            cancelText: "取消",
            content: `将处理选中的 ${selectedRows.length} 个用户，已处于目标状态的账号会自动跳过。`,
            okButtonProps: disabled ? { danger: true } : undefined,
            okText: disabled ? "批量禁用" : "批量启用",
            onOk: () => bulkStatus.mutateAsync({ disabled, users: selectedRows }),
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
                setSelectedUsers((previous) => {
                    const next = new Map<string, ListedUser>();
                    result.failures.forEach((failure) => {
                        const user = previous.get(failure.name);
                        if (user) {
                            next.set(failure.name, user);
                        }
                    });
                    return next;
                });
                modal.warning({
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
                    okText: "知道了",
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
    const columns: ProColumns<ListedUser>[] = [
        {
            key: "userName",
            render: (_, user) => {
                const name = user.displayName || user.name || "未命名用户";

                return (
                    <Flex align="center" className="min-w-0" gap={10}>
                        <UserAvatar name={name} size={36} />
                        <div className="min-w-0">
                            {user.name ? (
                                <Link
                                    className="block truncate font-medium"
                                    href={userDetailPath(workspace, user.name)}
                                >
                                    {name}
                                </Link>
                            ) : (
                                <span className="block truncate font-medium">{name}</span>
                            )}
                        </div>
                    </Flex>
                );
            },
            title: "用户",
            width: 176,
        },
        {
            key: "login",
            render: (_, user) => {
                const loginName = user.loginName || user.name;

                return (
                    <span className="block truncate font-mono text-xs whitespace-nowrap text-neutral-600 dark:text-neutral-300">
                        {loginName ? `@${loginName}` : <EmptyValue />}
                    </span>
                );
            },
            title: "登录名",
            responsive: ["md"],
            width: 108,
        },
        {
            key: "group",
            render: (_, user) => <UserGroupTag displayName={user.userGroupDisplayName} name={user.userGroup} />,
            title: "用户组",
            responsive: ["lg"],
            width: 106,
        },
        {
            key: "contact",
            render: (_, user) => (
                <Flex gap={2} vertical>
                    {user.email ? (
                        <span className="flex min-w-0 items-center gap-1.5 text-xs">
                            <Mail className="shrink-0 text-neutral-400" size={13} />
                            <span className="truncate">{user.email}</span>
                        </span>
                    ) : null}
                    {user.phone ? (
                        <span className="flex min-w-0 items-center gap-1.5 text-xs">
                            <Smartphone className="shrink-0 text-neutral-400" size={13} />
                            <span className="truncate">{user.phone}</span>
                        </span>
                    ) : null}
                    {!user.email && !user.phone ? <EmptyValue /> : null}
                </Flex>
            ),
            title: "联系方式",
            responsive: ["xl"],
            width: 144,
        },
        {
            key: "registeredAt",
            render: (_, user) => (
                <span className="whitespace-nowrap tabular-nums">{formatDateTime(user.registeredAt)}</span>
            ),
            title: "创建时间",
            responsive: ["xl"],
            width: 146,
        },
        {
            key: "status",
            render: (_, user) => <UserStatusTag disabled={user.disabled} />,
            title: "状态",
            responsive: ["lg"],
            width: 74,
        },
        {
            key: "actions",
            render: (_, user) => {
                const menuItems: NonNullable<MenuProps["items"]> = [
                    { disabled: !user.name, key: "detail", label: "查看详情" },
                ];
                const statusPending = setStatus.isPending && setStatus.variables?.name === user.name;

                if (canStatus) {
                    menuItems.push({
                        disabled: statusPending,
                        key: "toggle",
                        label: user.disabled ? "启用用户" : "禁用用户",
                    });
                }
                if (canPassword) {
                    menuItems.push({ key: "password", label: "重置密码" });
                }
                if (canDelete) {
                    menuItems.push({ type: "divider" });
                    menuItems.push({ danger: true, key: "delete", label: "删除用户" });
                }

                return (
                    <Space size={8} wrap>
                        {canEditRow ? (
                            <Button
                                loading={detailLoadingName === user.name}
                                onClick={() => void openEdit(user)}
                                size="small"
                                type="link"
                            >
                                编辑
                            </Button>
                        ) : null}
                        <Dropdown
                            menu={{
                                items: menuItems,
                                onClick: ({ key }) => {
                                    if (key === "detail" && user.name) {
                                        router.push(userDetailPath(workspace, user.name));
                                    } else if (key === "toggle") {
                                        toggleUser(user);
                                    } else if (key === "password") {
                                        setResetUser(user);
                                    } else if (key === "delete") {
                                        confirmDelete(user);
                                    }
                                },
                            }}
                        >
                            <Button
                                aria-label="更多用户操作"
                                icon={<MoreHorizontal size={16} />}
                                size="small"
                                type="text"
                            />
                        </Dropdown>
                    </Space>
                );
            },
            title: "操作",
            width: 92,
        },
    ];
    const rowSelection = canSelect
        ? {
              getCheckboxProps: (user: ListedUser) => ({ disabled: !user.name }),
              onChange: (keys: React.Key[], rows: ListedUser[]) => {
                  const selectedKeys = new Set(keys.map(String));
                  setSelectedUsers((previous) => {
                      const next = new Map(previous);

                      users.forEach((user) => {
                          if (user.name && !selectedKeys.has(user.name)) {
                              next.delete(user.name);
                          }
                      });
                      rows.forEach((row) => {
                          if (row.name && selectedKeys.has(row.name)) {
                              next.set(row.name, row);
                          }
                      });

                      return next;
                  });
              },
              preserveSelectedRowKeys: true,
              selectedRowKeys: selectedRows.map((user) => user.name).filter((name): name is string => Boolean(name)),
          }
        : undefined;
    const moreItems: NonNullable<MenuProps["items"]> = [];

    if (canBatchCreate) {
        moreItems.push({ icon: <UsersRound size={14} />, key: "batch-create", label: "批量添加用户" });
    }
    if (canImport) {
        moreItems.push({ icon: <Upload size={14} />, key: "import", label: "导入用户" });
    }
    if (moreItems.length > 0) {
        moreItems.push({ type: "divider" });
    }
    moreItems.push({ icon: <RefreshCw size={14} />, key: "refresh", label: "刷新列表" });
    const bulkItems: NonNullable<MenuProps["items"]> = [];

    if (canExport) {
        bulkItems.push({ icon: <Download size={14} />, key: "export-selected", label: "导出选中" });
    }
    if (canBulk) {
        bulkItems.push({ icon: <Power size={14} />, key: "enable", label: "批量启用" });
        bulkItems.push({ danger: true, icon: <PowerOff size={14} />, key: "disable", label: "批量禁用" });
    }
    if (bulkItems.length > 0) {
        bulkItems.push({ type: "divider" });
    }
    bulkItems.push({ key: "clear", label: "取消选择" });
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
            <Flex className="w-full" gap={16} vertical>
                <SectionCard title="筛选条件">
                    <div className="flex w-full flex-wrap items-center gap-3">
                        <Select
                            allowClear
                            aria-label="筛选账号状态"
                            className="min-w-0 flex-1 basis-52"
                            onChange={applyStatus}
                            options={STATUS_OPTIONS}
                            placeholder="账号状态"
                            value={status || undefined}
                        />
                        <Select
                            allowClear
                            aria-label="筛选用户组"
                            className="min-w-0 flex-1 basis-52"
                            onChange={applyGroup}
                            options={groupOptions}
                            optionFilterProp="label"
                            placeholder="用户组"
                            showSearch
                            value={userGroup || undefined}
                        />
                        <Select
                            allowClear
                            aria-label="选择排序方式"
                            className="min-w-0 flex-1 basis-52"
                            onChange={applySort}
                            options={SORT_OPTIONS}
                            placeholder="排序方式"
                            value={sortValue || undefined}
                        />
                        {hasFilters ? (
                            <Button className="shrink-0" onClick={resetFilters} type="link">
                                重置筛选
                            </Button>
                        ) : null}
                    </div>
                </SectionCard>
                {usersQuery.error ? (
                    <Alert
                        action={
                            <Button onClick={() => void usersQuery.refetch()} size="small">
                                重试
                            </Button>
                        }
                        description={consoleErrorMessage(usersQuery.error)}
                        message="用户列表加载失败"
                        showIcon
                        type="error"
                    />
                ) : null}
                <ProTable<ListedUser>
                    cardProps={{ className: "!rounded-[20px] sm:!rounded-[28px]" }}
                    columns={columns}
                    columnsState={{
                        persistenceKey: "console.users.table-columns",
                        persistenceType: "localStorage",
                    }}
                    components={{ ExpandIcon: UserExpandIcon }}
                    dataSource={users}
                    expandable={{
                        expandedRowRender: (user) => (
                            <UserExpandedRow
                                canEdit={canEditRow}
                                onEdit={() => void openEdit(user)}
                                user={user}
                                workspace={workspace}
                            />
                        ),
                    }}
                    headerTitle={
                        canExport || (canSelect && selectedRows.length > 0) ? (
                            <Space size={8} wrap>
                                {canExport ? (
                                    <Button
                                        icon={<Download size={15} />}
                                        loading={exporting}
                                        onClick={() => void exportRows()}
                                    >
                                        导出
                                    </Button>
                                ) : null}
                                {canSelect && selectedRows.length > 0 ? (
                                    <Dropdown
                                        menu={{ items: bulkItems, onClick: ({ key }) => handleBulkAction(key) }}
                                        trigger={["click"]}
                                    >
                                        <Button icon={<ListChecks size={15} />}>
                                            批量操作
                                            <ChevronDown size={14} />
                                        </Button>
                                    </Dropdown>
                                ) : null}
                            </Space>
                        ) : undefined
                    }
                    loading={usersQuery.isFetching}
                    options={{ density: false, fullScreen: false, reload: false, setting: true }}
                    pagination={{
                        ...table.pagination,
                        showTotal: (total) => `共 ${total} 项`,
                        total: usersQuery.data?.total ?? 0,
                    }}
                    rowKey={(user) => user.name ?? user.displayName ?? ""}
                    rowSelection={rowSelection}
                    scroll={{ x: "max-content" }}
                    search={false}
                    tableAlertRender={false}
                    toolBarRender={() => [
                        <Input
                            allowClear
                            aria-label="搜索用户"
                            className="w-full sm:w-72"
                            defaultValue={table.keyword}
                            key={table.keyword}
                            onClear={() => applyKeyword("")}
                            onPressEnter={(event) => applyKeyword(event.currentTarget.value)}
                            placeholder="搜索显示名称 / 邮箱"
                            prefix={<Search className="text-neutral-400" size={15} />}
                        />,
                        canCreate ? (
                            <Button
                                icon={<UserPlus size={15} />}
                                key="create"
                                onClick={() => setCreateOpen(true)}
                                type="primary"
                            >
                                新建用户
                            </Button>
                        ) : null,
                        <Dropdown
                            key="more"
                            menu={{
                                items: moreItems,
                                onClick: ({ key }) => {
                                    if (key === "batch-create") {
                                        setBatchOpen(true);
                                    } else if (key === "import") {
                                        setImportOpen(true);
                                    } else if (key === "refresh") {
                                        refreshUsers();
                                    }
                                },
                            }}
                            trigger={["click"]}
                        >
                            <Button aria-label="更多操作" icon={<MoreHorizontal size={18} />} type="text" />
                        </Dropdown>,
                    ]}
                />
            </Flex>
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
