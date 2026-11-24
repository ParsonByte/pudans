import type { Workspace } from "@pudans-dev/api-client";

import { type RoleMenuEntry, operationTypeLabel } from "../../roles/composables/_role-permissions";

const CJK_CHARACTER = /[\u3400-\u9fff]/;

const DISPLAY_TEXT_REPLACEMENTS: ReadonlyArray<readonly [RegExp, string]> = [[/\s*Webhook\s*/gi, "回调"]];

const WORKSPACE_LABELS: Record<string, string> = {
    console: "管理控制台",
    uc: "个人中心",
};

export type PermissionWorkspaceGroup = {
    entries: RoleMenuEntry[];
    key: string;
    menuCount: number;
    name: string;
    operationCount: number;
    publicMenuCount: number;
    workspace: Workspace;
};

export function buildPermissionWorkspaceGroups(entries: RoleMenuEntry[]): PermissionWorkspaceGroup[] {
    const groups = new Map<string, PermissionWorkspaceGroup>();

    for (const entry of entries) {
        const key = workspaceKey(entry.workspace);
        const group = groups.get(key) ?? {
            entries: [],
            key,
            menuCount: 0,
            name: workspaceDisplayName(entry.workspace),
            operationCount: 0,
            publicMenuCount: 0,
            workspace: entry.workspace,
        };

        group.entries.push(entry);
        group.menuCount += 1;
        group.operationCount += entry.operations.length;
        group.publicMenuCount += Number(Boolean(entry.menu.publicMenu));
        groups.set(key, group);
    }

    return [...groups.values()].sort(
        (left, right) =>
            (left.workspace.order ?? 0) - (right.workspace.order ?? 0) ||
            left.name.localeCompare(right.name, "zh-Hans-CN", { sensitivity: "base" }),
    );
}

export function matchesDirectoryEntry(entry: RoleMenuEntry, query: string): boolean {
    return [
        entry.menu.label,
        entry.menu.description,
        ...entry.operations.flatMap((operation) => [
            operation.label,
            operation.description,
            operationTypeLabel(operation.type),
        ]),
    ]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase()
        .includes(query);
}

export function matchesWorkspace(group: PermissionWorkspaceGroup, query: string): boolean {
    return [group.name, group.workspace.plan].filter(Boolean).join(" ").toLocaleLowerCase().includes(query);
}

export function localizeDisplayText(text?: string): string {
    if (!text) {
        return "";
    }

    return DISPLAY_TEXT_REPLACEMENTS.reduce(
        (localized, [pattern, replacement]) => localized.replace(pattern, replacement),
        text,
    );
}

export function menuDisplayLabel(menu: RoleMenuEntry["menu"], resourceLabels: Record<string, string>): string {
    if (menu.label && CJK_CHARACTER.test(menu.label)) {
        return menu.label;
    }

    const resourceLabel = menu.component ? resourceLabels[menu.component] : undefined;
    if (resourceLabel && CJK_CHARACTER.test(resourceLabel)) {
        return resourceLabel;
    }

    return localizeDisplayText(menu.label) || "未命名菜单";
}

export function workspaceDescription(workspace: Workspace): string {
    const description = localizeDisplayText(workspace.plan);
    return description === "工作区" || description === workspaceDisplayName(workspace) ? "" : description;
}

function workspaceDisplayName(workspace: Workspace): string {
    const override = WORKSPACE_LABELS[workspace.id ?? ""];
    if (override) {
        return override;
    }
    if (workspace.name && CJK_CHARACTER.test(workspace.name)) {
        return workspace.name;
    }
    if (workspace.plan && CJK_CHARACTER.test(workspace.plan)) {
        return workspace.plan;
    }
    return workspace.name || workspace.slug || "未命名工作区";
}

function workspaceKey(workspace: Workspace): string {
    return workspace.id || workspace.name || workspace.slug || "workspace";
}
