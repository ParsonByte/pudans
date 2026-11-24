"use client";

import type { WorkspaceMenuOperation } from "@pudans-dev/api-client";
import { Checkbox, Table, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { MenuIcon } from "@/components/ui";

import {
    type RoleCatalogIndex,
    type RoleMenuEntry,
    type RolePermissionDraft,
    actorCanGrant,
    cloneRolePermissionDraft,
    isOperationGranted,
    operationTypeLabel,
} from "../composables/_role-permissions";
import { RolePermissionBrowser } from "./role-permission-browser";

type RoleOperationPermissionsProps = {
    allowedPermissions?: string[];
    draft: RolePermissionDraft;
    editable: boolean;
    index: RoleCatalogIndex;
    onChange: (draft: RolePermissionDraft) => void;
};

type OperationRow = {
    __rowKey: string;
    entry: RoleMenuEntry;
    operation: WorkspaceMenuOperation;
};

export function RoleOperationPermissions({
    allowedPermissions,
    draft,
    editable,
    index,
    onChange,
}: RoleOperationPermissionsProps) {
    return (
        <RolePermissionBrowser
            emptyDescription="没有匹配的操作权限"
            entries={index.menus.filter((entry) => entry.operations.length > 0)}
            matchesEntry={matchesOperation}
            renderGroup={(entries) => (
                <OperationPermissionTable
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entries={entries}
                    onChange={onChange}
                />
            )}
            summary={(entries) => {
                const operationTotal = entries.reduce((total, entry) => total + entry.operations.length, 0);
                const operationGranted = entries.reduce(
                    (total, entry) =>
                        total +
                        entry.operations.filter((operation) => isOperationGranted(entry, operation.permission, draft))
                            .length,
                    0,
                );
                return `${operationGranted} / ${operationTotal} 个操作已授权`;
            }}
            toolbarPlaceholder="搜索操作或权限"
        />
    );
}

type OperationPermissionTableProps = {
    allowedPermissions?: string[];
    draft: RolePermissionDraft;
    editable: boolean;
    entries: RoleMenuEntry[];
    onChange: (draft: RolePermissionDraft) => void;
};

function OperationPermissionTable({
    allowedPermissions,
    draft,
    editable,
    entries,
    onChange,
}: OperationPermissionTableProps) {
    const rows = entries.flatMap((entry) =>
        entry.operations.map((operation) => ({
            __rowKey: `${entry.workspace.id ?? entry.workspace.name}:${entry.menu.key}:${operation.key}`,
            entry,
            operation,
        })),
    );

    const columns: NonNullable<TableProps<OperationRow>["columns"]> = [
        {
            cell: ({ row }) => <OperationMenuCell entry={row.entry} />,
            colKey: "menu",
            title: "菜单",
            width: 140,
        },
        {
            cell: ({ row }) => <OperationCell operation={row.operation} />,
            colKey: "operation",
            title: "操作",
        },
        {
            cell: ({ row }) => (
                <OperationCheckbox
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entry={row.entry}
                    onChange={onChange}
                    operation={row.operation}
                />
            ),
            colKey: "allow",
            title: "允许",
            width: 64,
        },
    ];

    return <Table<OperationRow> columns={columns} data={rows} rowKey="__rowKey" tableLayout="fixed" />;
}

function OperationMenuCell({ entry }: { entry: RoleMenuEntry }) {
    return (
        <div className="flex min-w-0 items-center gap-2">
            <MenuIcon icon={entry.menu.icon} size={14} />
            <span className="truncate text-xs">{entry.menu.label ?? entry.menu.key}</span>
        </div>
    );
}

function OperationCell({ operation }: { operation: WorkspaceMenuOperation }) {
    return (
        <div className="min-w-0">
            <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                <span className="truncate text-sm">{operation.label ?? operation.key}</span>
                <Tag theme={operation.type === "table" ? "primary" : "default"} variant="light">
                    {operationTypeLabel(operation.type)}
                </Tag>
            </div>
            {operation.description ? (
                <Tooltip content={operation.description}>
                    <div className="truncate text-[11px] text-[var(--td-text-color-placeholder)]">
                        {operation.description}
                    </div>
                </Tooltip>
            ) : null}
        </div>
    );
}

type OperationCheckboxProps = {
    allowedPermissions?: string[];
    draft: RolePermissionDraft;
    editable: boolean;
    entry: RoleMenuEntry;
    onChange: (draft: RolePermissionDraft) => void;
    operation: WorkspaceMenuOperation;
};

function OperationCheckbox({
    allowedPermissions,
    draft,
    editable,
    entry,
    onChange,
    operation,
}: OperationCheckboxProps) {
    const manageChecked = Boolean(entry.manage?.permission && draft.manage.has(entry.manage.permission));
    const granted = isOperationGranted(entry, operation.permission, draft);
    const canGrant = Boolean(operation.permission && actorCanGrant(allowedPermissions, operation.permission));
    const disabled = !editable || manageChecked || !operation.permission || !canGrant;
    const tooltip = manageChecked
        ? "管理权限已授予该操作"
        : !operation.permission
          ? "该操作没有独立权限"
          : !canGrant
            ? "当前账号无权授予该权限"
            : undefined;

    return (
        <Tooltip content={tooltip}>
            <span>
                <Checkbox
                    aria-label="允许"
                    checked={granted}
                    disabled={disabled}
                    onChange={(checked) => setOperation(entry, operation.permission, checked, draft, onChange)}
                />
            </span>
        </Tooltip>
    );
}

function matchesOperation(entry: RoleMenuEntry, query: string): boolean {
    return [
        entry.workspace.name,
        entry.menu.label,
        entry.menu.key,
        ...entry.operations.flatMap((operation) => [operation.label, operation.description, operation.permission]),
    ]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase()
        .includes(query);
}

function setOperation(
    entry: RoleMenuEntry,
    permission: string | undefined,
    checked: boolean,
    draft: RolePermissionDraft,
    onChange: (draft: RolePermissionDraft) => void,
) {
    if (!permission) {
        return;
    }

    const next = cloneRolePermissionDraft(draft);
    if (checked) {
        next.operation.add(permission);
        if (entry.view?.permission) {
            next.view.add(entry.view.permission);
        }
    } else {
        next.operation.delete(permission);
    }
    onChange(next);
}
