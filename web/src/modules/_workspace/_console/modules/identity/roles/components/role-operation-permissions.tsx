"use client";

import { Icon } from "@iconify/react";
import type { WorkspaceMenuOperation } from "@pudans-dev/api-client";
import { Checkbox, Table, type TableProps, Tag, Tooltip, theme } from "antd";

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
    entry: RoleMenuEntry;
    key: string;
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
            entry,
            key: `${entry.workspace.id ?? entry.workspace.name}:${entry.menu.key}:${operation.key}`,
            operation,
        })),
    );

    const columns: TableProps<OperationRow>["columns"] = [
        {
            key: "menu",
            render: (_, row) => <OperationMenuCell entry={row.entry} />,
            title: "菜单",
            width: 140,
        },
        {
            key: "operation",
            render: (_, row) => <OperationCell operation={row.operation} />,
            title: "操作",
        },
        {
            key: "permission",
            render: (_, row) => <PermissionCode value={row.operation.permission ?? row.operation.key ?? "—"} />,
            title: "权限标识",
            width: 170,
        },
        {
            align: "center",
            key: "allow",
            render: (_, row) => (
                <OperationCheckbox
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entry={row.entry}
                    onChange={onChange}
                    operation={row.operation}
                />
            ),
            title: "允许",
            width: 64,
        },
    ];

    return (
        <Table<OperationRow>
            columns={columns}
            dataSource={rows}
            pagination={false}
            rowKey={(row) => row.key}
            scroll={{ x: 520 }}
            tableLayout="fixed"
        />
    );
}

function OperationMenuCell({ entry }: { entry: RoleMenuEntry }) {
    return (
        <div className="flex min-w-0 items-center gap-2">
            <Icon icon={entry.menu.icon || "lucide:panel-top"} width={14} />
            <span className="truncate text-xs">{entry.menu.label ?? entry.menu.key}</span>
        </div>
    );
}

function OperationCell({ operation }: { operation: WorkspaceMenuOperation }) {
    const { token } = theme.useToken();

    return (
        <div className="min-w-0">
            <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                <span className="truncate text-sm">{operation.label ?? operation.key}</span>
                <Tag className="!m-0" color={operation.type === "table" ? "geekblue" : "default"}>
                    {operationTypeLabel(operation.type)}
                </Tag>
            </div>
            {operation.description ? (
                <Tooltip title={operation.description}>
                    <div className="truncate text-[11px]" style={{ color: token.colorTextTertiary }}>
                        {operation.description}
                    </div>
                </Tooltip>
            ) : null}
        </div>
    );
}

function PermissionCode({ value }: { value: string }) {
    const { token } = theme.useToken();

    return (
        <Tooltip title={value}>
            <div className="truncate font-mono text-[10px]" style={{ color: token.colorTextTertiary }}>
                {value}
            </div>
        </Tooltip>
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
        <Tooltip title={tooltip}>
            <span>
                <Checkbox
                    aria-label="允许"
                    checked={granted}
                    disabled={disabled}
                    onChange={(event) =>
                        setOperation(entry, operation.permission, event.target.checked, draft, onChange)
                    }
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
