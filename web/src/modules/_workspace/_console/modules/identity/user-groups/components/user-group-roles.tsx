"use client";

import { useMemo } from "react";

import type { Role, UserGroup } from "@pudans-dev/api-client";
import { AddIcon, DeleteIcon, LockOnIcon, SecuredIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Skeleton, Table, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { ROLE_TEMPLATE_LABEL, isBuiltinRole, roleDisplayName } from "../../composables/_role";
import { isBuiltinUserGroup, isSuperAdminsUserGroup, userGroupRoleNames } from "../composables/_user-group";

type UserGroupRolesProps = {
    canManage: boolean;
    group: UserGroup;
    onAdd: () => void;
    onRemove: (roleName: string) => Promise<void>;
    onRetry: () => void;
    roles: Role[];
    rolesError: unknown;
    rolesPending: boolean;
};

type BoundRoleRow = {
    __rowKey: string;
    builtin: boolean;
    name: string;
    removable: boolean;
    role?: Role;
};

export function UserGroupRoles({
    canManage,
    group,
    onAdd,
    onRemove,
    onRetry,
    roles,
    rolesError,
    rolesPending,
}: UserGroupRolesProps) {
    const builtinGroup = isBuiltinUserGroup(group);
    const superAdmins = isSuperAdminsUserGroup(group);
    const boundRoleNames = useMemo(() => userGroupRoleNames(group), [group]);
    const rolesByName = useMemo(() => new Map(roles.map((role) => [role.metadata.name, role])), [roles]);
    const eligibleRoleCount = roles.filter(
        (role) =>
            !isBuiltinRole(role) &&
            role.metadata.labels?.[ROLE_TEMPLATE_LABEL] !== "true" &&
            !boundRoleNames.includes(role.metadata.name),
    ).length;
    const rows: BoundRoleRow[] = boundRoleNames.map((name) => {
        const role = rolesByName.get(name);
        const builtin = role ? isBuiltinRole(role) : false;

        return {
            __rowKey: name,
            builtin,
            name,
            removable: canManage && !(builtinGroup && builtin),
            role,
        };
    });
    const missingRoleNames = rolesPending || rolesError ? [] : rows.filter((row) => !row.role).map((row) => row.name);
    const addDisabled = rolesPending || Boolean(rolesError) || eligibleRoleCount === 0;
    const addDisabledReason = rolesPending
        ? "正在加载角色"
        : rolesError
          ? "角色列表加载失败"
          : eligibleRoleCount === 0
            ? "没有可绑定的自定义角色"
            : undefined;

    const removeWithConfirm = (row: BoundRoleRow) => {
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "移除",
            content: `移除角色 ${row.role ? roleDisplayName(row.role) : row.name} 后，组内成员将不再获得该角色的权限。`,
            danger: true,
            onConfirm: () => {
                void onRemove(row.name);
            },
            title: "移除角色",
        });
    };
    const columns: NonNullable<TableProps<BoundRoleRow>["columns"]> = [
        {
            cell: ({ row }) => <BoundRoleCell row={row} />,
            colKey: "role",
            title: "角色",
        },
        {
            cell: ({ row }) => <BoundRoleType row={row} />,
            colKey: "type",
            title: "类型",
            width: 100,
        },
        {
            cell: ({ row }) => <span className="text-sm tabular-nums">{row.role?.rules?.length ?? 0}</span>,
            colKey: "rules",
            title: "规则",
            width: 72,
        },
        ...(canManage
            ? [
                  {
                      cell: ({ row }: { row: BoundRoleRow }) => (
                          <RemoveRoleAction onRemove={() => removeWithConfirm(row)} row={row} />
                      ),
                      colKey: "actions",
                      title: "操作",
                      width: 90,
                  },
              ]
            : []),
    ];
    const addButton = (
        <Button disabled={addDisabled} icon={<AddIcon />} onClick={onAdd} size="small" variant="outline">
            绑定角色
        </Button>
    );

    return (
        <Card
            actions={
                canManage ? (
                    addDisabledReason ? (
                        <Tooltip content={addDisabledReason}>
                            <span>{addButton}</span>
                        </Tooltip>
                    ) : (
                        addButton
                    )
                ) : null
            }
            header={<div className="text-base font-semibold">绑定角色</div>}
            headerBordered
        >
            <div className="flex flex-col gap-4">
                {superAdmins ? (
                    <Tag theme="warning" variant="light">
                        超级管理员用户组的角色绑定由平台维护
                    </Tag>
                ) : builtinGroup ? (
                    <Tag theme="warning" variant="light">
                        内置角色由平台维护，只能补充自定义角色
                    </Tag>
                ) : null}
                {missingRoleNames.length > 0 ? (
                    <Tooltip content={missingRoleNames.join("、")}>
                        <Tag theme="warning" variant="light">
                            {missingRoleNames.length} 个绑定角色未找到
                        </Tag>
                    </Tooltip>
                ) : null}
                {rolesError ? (
                    <Empty
                        action={
                            <Button onClick={onRetry} size="small" variant="outline">
                                重试
                            </Button>
                        }
                        description={consoleErrorMessage(rolesError)}
                        title="角色列表加载失败"
                        type="fail"
                    />
                ) : rolesPending ? (
                    <Skeleton animation="gradient" theme="paragraph" />
                ) : rows.length === 0 ? (
                    <Empty
                        action={
                            canManage && !addDisabled ? (
                                <Button icon={<AddIcon />} onClick={onAdd} theme="primary">
                                    绑定第一个角色
                                </Button>
                            ) : undefined
                        }
                        description="尚未绑定角色"
                    />
                ) : (
                    <Table<BoundRoleRow> columns={columns} data={rows} rowKey="__rowKey" />
                )}
            </div>
        </Card>
    );
}

function BoundRoleCell({ row }: { row: BoundRoleRow }) {
    const builtin = row.builtin;

    return (
        <div className="flex min-w-0 items-center gap-2.5">
            <span
                className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${
                    builtin
                        ? "bg-[var(--td-warning-color-light)] text-[var(--td-warning-color)]"
                        : "bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]"
                }`}
            >
                <SecuredIcon size={16} />
            </span>
            <div className="min-w-0">
                <div className="truncate text-sm font-medium">{row.role ? roleDisplayName(row.role) : row.name}</div>
                <div className="truncate font-mono text-[10px] text-[var(--td-text-color-placeholder)]">{row.name}</div>
            </div>
        </div>
    );
}

function BoundRoleType({ row }: { row: BoundRoleRow }) {
    if (!row.role) {
        return (
            <Tag theme="warning" variant="light">
                未找到
            </Tag>
        );
    }
    if (isBuiltinRole(row.role)) {
        return (
            <Tag theme="warning" variant="light">
                内置
            </Tag>
        );
    }
    if (row.role.metadata.labels?.[ROLE_TEMPLATE_LABEL] === "true") {
        return (
            <Tag theme="primary" variant="light">
                模板
            </Tag>
        );
    }

    return (
        <Tag theme="primary" variant="light">
            自定义
        </Tag>
    );
}

function RemoveRoleAction({ onRemove, row }: { onRemove: () => void; row: BoundRoleRow }) {
    if (!row.removable) {
        return (
            <Tooltip content="内置用户组必须保留内置角色">
                <Button aria-label="不可移除" disabled icon={<LockOnIcon />} size="small" variant="text" />
            </Tooltip>
        );
    }

    return (
        <Button icon={<DeleteIcon />} onClick={onRemove} size="small" theme="danger" variant="text">
            移除
        </Button>
    );
}
