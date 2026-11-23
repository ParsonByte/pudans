"use client";

import { Icon } from "@iconify/react";
import { Switch, Table, type TableProps, Tag, Tooltip, theme } from "antd";

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
    const columns: TableProps<RoleMenuEntry>["columns"] = [
        {
            key: "menu",
            render: (_, entry) => <MenuIdentity entry={entry} />,
            title: "菜单",
        },
        {
            key: "permission",
            render: (_, entry) => <MenuPermissionIdentifiers entry={entry} />,
            title: "权限标识",
            width: 190,
        },
        {
            align: "center",
            key: "view",
            render: (_, entry) => (
                <MenuViewSwitch
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entry={entry}
                    onChange={onChange}
                />
            ),
            title: "访问",
            width: 76,
        },
        {
            align: "center",
            key: "manage",
            render: (_, entry) => (
                <MenuManageSwitch
                    allowedPermissions={allowedPermissions}
                    draft={draft}
                    editable={editable}
                    entry={entry}
                    onChange={onChange}
                />
            ),
            title: "管理",
            width: 76,
        },
    ];

    return (
        <Table<RoleMenuEntry>
            columns={columns}
            dataSource={entries}
            pagination={false}
            rowKey={(entry) => `${entry.workspace.id ?? entry.workspace.name}:${entry.menu.key}`}
            scroll={{ x: 520 }}
            tableLayout="fixed"
        />
    );
}

function MenuIdentity({ entry }: { entry: RoleMenuEntry }) {
    const { token } = theme.useToken();
    const description = entry.menu.description || entry.menu.parent || entry.menu.group || entry.menu.key;

    return (
        <div className="flex min-w-0 items-center gap-2.5">
            <span
                className="flex size-7 shrink-0 items-center justify-center rounded-md"
                style={{ background: token.colorFillTertiary, color: token.colorTextSecondary }}
            >
                <Icon icon={entry.menu.icon || "lucide:panel-top"} width={15} />
            </span>
            <div className="min-w-0">
                <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{entry.menu.label ?? entry.menu.key}</span>
                    {entry.menu.publicMenu ? (
                        <Tag className="!m-0" color="cyan">
                            公开
                        </Tag>
                    ) : null}
                </div>
                <Tooltip title={description}>
                    <div className="truncate text-[11px]" style={{ color: token.colorTextTertiary }}>
                        {description}
                    </div>
                </Tooltip>
            </div>
        </div>
    );
}

function MenuPermissionIdentifiers({ entry }: { entry: RoleMenuEntry }) {
    const { token } = theme.useToken();
    const permissions = [
        entry.view?.permission ? { label: "访问", value: entry.view.permission } : undefined,
        entry.manage?.permission ? { label: "管理", value: entry.manage.permission } : undefined,
    ].filter((item): item is { label: string; value: string } => Boolean(item));

    if (permissions.length === 0) {
        return (
            <span className="font-mono text-[10px]" style={{ color: token.colorTextTertiary }}>
                —
            </span>
        );
    }

    return (
        <div className="flex min-w-0 flex-col gap-0.5">
            {permissions.map((permission) => (
                <Tooltip key={permission.value} title={permission.value}>
                    <div className="flex min-w-0 gap-1.5 font-mono text-[10px]">
                        <span className="shrink-0" style={{ color: token.colorTextTertiary }}>
                            {permission.label}
                        </span>
                        <span className="truncate" style={{ color: token.colorTextSecondary }}>
                            {permission.value}
                        </span>
                    </div>
                </Tooltip>
            ))}
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
        <Tooltip title={tooltip}>
            <span>
                <Switch
                    aria-label="访问"
                    checked={checked}
                    disabled={disabled}
                    onChange={(nextChecked) => setView(entry, nextChecked, checked, draft, onChange)}
                    size="small"
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
        <Tooltip title={tooltip}>
            <span>
                <Switch
                    aria-label="管理"
                    checked={checked}
                    disabled={disabled}
                    onChange={(nextChecked) => setManage(entry, nextChecked, draft, onChange)}
                    size="small"
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
