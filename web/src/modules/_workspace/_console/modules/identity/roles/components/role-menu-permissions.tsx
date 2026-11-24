"use client";

import { Switch, Table, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { MenuIcon } from "@/components/ui";

import {
    type RoleCatalogIndex,
    type RoleMenuEntry,
    type RolePermissionDraft,
    actorCanGrant,
    cloneRolePermissionDraft,
    isMenuVisible,
} from "../composables/_role-permissions";
import { RolePermissionBrowser } from "./role-permission-browser";

type RoleMenuPermissionsProps = {
    allowedPermissions?: string[];
    draft: RolePermissionDraft;
    editable: boolean;
    index: RoleCatalogIndex;
    onChange: (draft: RolePermissionDraft) => void;
};

type MenuRow = RoleMenuEntry & {
    __rowKey: string;
};

export function RoleMenuPermissions({
    allowedPermissions,
    draft,
    editable,
    index,
    onChange,
}: RoleMenuPermissionsProps) {
    return (
        <RolePermissionBrowser
            emptyDescription="没有匹配的菜单权限"
            entries={index.menus}
            matchesEntry={matchesMenu}
            renderGroup={(entries) => (
                <MenuPermissionTable
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entries={entries}
                    onChange={onChange}
                />
            )}
            summary={(entries) =>
                `${entries.filter((entry) => isMenuVisible(entry, draft)).length} / ${entries.length} 个菜单已授权`
            }
            toolbarPlaceholder="搜索菜单或权限"
        />
    );
}

type MenuPermissionTableProps = {
    allowedPermissions?: string[];
    draft: RolePermissionDraft;
    editable: boolean;
    entries: RoleMenuEntry[];
    onChange: (draft: RolePermissionDraft) => void;
};

function MenuPermissionTable({ allowedPermissions, draft, editable, entries, onChange }: MenuPermissionTableProps) {
    const rows: MenuRow[] = entries.map((entry) => ({
        ...entry,
        __rowKey: `${entry.workspace.id ?? entry.workspace.name}:${entry.menu.key}`,
    }));
    const columns: NonNullable<TableProps<MenuRow>["columns"]> = [
        {
            cell: ({ row }) => <MenuIdentity entry={row} />,
            colKey: "menu",
            title: "菜单",
        },
        {
            cell: ({ row }) => (
                <MenuViewSwitch
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entry={row}
                    onChange={onChange}
                />
            ),
            colKey: "view",
            title: "访问",
            width: 76,
        },
        {
            cell: ({ row }) => (
                <MenuManageSwitch
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entry={row}
                    onChange={onChange}
                />
            ),
            colKey: "manage",
            title: "管理",
            width: 76,
        },
    ];

    return <Table<MenuRow> columns={columns} data={rows} rowKey="__rowKey" tableLayout="fixed" />;
}

function MenuIdentity({ entry }: { entry: RoleMenuEntry }) {
    const description = entry.menu.description || entry.menu.parent || entry.menu.group || entry.menu.key;

    return (
        <div className="flex min-w-0 items-center gap-2.5">
            <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                <MenuIcon icon={entry.menu.icon} size={15} />
            </span>
            <div className="min-w-0">
                <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{entry.menu.label ?? entry.menu.key}</span>
                    {entry.menu.publicMenu ? (
                        <Tag theme="primary" variant="light">
                            公开
                        </Tag>
                    ) : null}
                </div>
                {description ? (
                    <Tooltip content={description}>
                        <div className="truncate text-[11px] text-[var(--td-text-color-placeholder)]">
                            {description}
                        </div>
                    </Tooltip>
                ) : null}
            </div>
        </div>
    );
}

type MenuSwitchProps = {
    allowedPermissions?: string[];
    draft: RolePermissionDraft;
    editable: boolean;
    entry: RoleMenuEntry;
    onChange: (draft: RolePermissionDraft) => void;
};

function MenuViewSwitch({ allowedPermissions, draft, editable, entry, onChange }: MenuSwitchProps) {
    const viewPermission = entry.view?.permission;
    const publicMenu = Boolean(entry.menu.publicMenu);
    const checked = publicMenu || Boolean(viewPermission && draft.view.has(viewPermission));
    const canGrant = Boolean(viewPermission && actorCanGrant(allowedPermissions, viewPermission));
    const disabled = !editable || publicMenu || !viewPermission || !canGrant;
    const tooltip = publicMenu
        ? "公开菜单无需授权"
        : !viewPermission
          ? "该菜单没有独立的访问权限"
          : !canGrant
            ? "当前账号无权授予该权限"
            : undefined;

    return (
        <Tooltip content={tooltip}>
            <span>
                <Switch
                    aria-label="访问"
                    disabled={disabled}
                    onChange={(value) => setView(entry, value === true, checked, draft, onChange)}
                    size="small"
                    value={checked}
                />
            </span>
        </Tooltip>
    );
}

function MenuManageSwitch({ allowedPermissions, draft, editable, entry, onChange }: MenuSwitchProps) {
    const managePermission = entry.manage?.permission;
    const checked = Boolean(managePermission && draft.manage.has(managePermission));
    const canGrant = Boolean(managePermission && actorCanGrant(allowedPermissions, managePermission));
    const disabled = !editable || !managePermission || !canGrant;
    const tooltip = !managePermission ? "该菜单没有独立的管理权限" : !canGrant ? "当前账号无权授予该权限" : undefined;

    return (
        <Tooltip content={tooltip}>
            <span>
                <Switch
                    aria-label="管理"
                    disabled={disabled}
                    onChange={(value) => setManage(entry, value === true, draft, onChange)}
                    size="small"
                    value={checked}
                />
            </span>
        </Tooltip>
    );
}

function matchesMenu(entry: RoleMenuEntry, query: string): boolean {
    return [
        entry.workspace.name,
        entry.menu.label,
        entry.menu.description,
        entry.menu.key,
        entry.menu.group,
        entry.menu.parent,
        entry.view?.permission,
        entry.manage?.permission,
        ...entry.operations.map((operation) => operation.permission),
    ]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase()
        .includes(query);
}

function setView(
    entry: RoleMenuEntry,
    checked: boolean,
    viewChecked: boolean,
    draft: RolePermissionDraft,
    onChange: (draft: RolePermissionDraft) => void,
) {
    if (!viewChecked && !checked) {
        return;
    }

    const viewPermission = entry.view?.permission;
    const managePermission = entry.manage?.permission;
    const next = cloneRolePermissionDraft(draft);

    if (checked) {
        if (viewPermission) {
            next.view.add(viewPermission);
        }
        return onChange(next);
    }

    if (viewPermission) {
        next.view.delete(viewPermission);
    }
    if (managePermission) {
        next.manage.delete(managePermission);
    }
    for (const operation of entry.operations) {
        if (operation.permission) {
            next.operation.delete(operation.permission);
        }
    }
    onChange(next);
}

function setManage(
    entry: RoleMenuEntry,
    checked: boolean,
    draft: RolePermissionDraft,
    onChange: (draft: RolePermissionDraft) => void,
) {
    const managePermission = entry.manage?.permission;
    if (!managePermission) {
        return;
    }

    const next = cloneRolePermissionDraft(draft);
    if (checked) {
        next.manage.add(managePermission);
        if (entry.view?.permission) {
            next.view.add(entry.view.permission);
        }
    } else {
        next.manage.delete(managePermission);
    }
    onChange(next);
}
