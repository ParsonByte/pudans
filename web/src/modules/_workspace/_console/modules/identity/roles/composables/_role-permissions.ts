import type {
    PermissionCatalog,
    PolicyRule,
    Role,
    Workspace,
    WorkspaceMenu,
    WorkspaceMenuLevel,
    WorkspaceMenuOperation,
} from "@pudans-dev/api-client";

import {
    ROLE_DEPENDENCIES_ANNOTATION,
    ROLE_DESCRIPTION_ANNOTATION,
    ROLE_DISPLAY_NAME_ANNOTATION,
    ROLE_UI_PERMISSIONS_ANNOTATION,
    roleDependencies,
    roleDirectPermissions,
    roleDisplayName,
} from "../../composables/_role";

export type RolePermissionDraft = {
    manage: Set<string>;
    operation: Set<string>;
    view: Set<string>;
};

export type RoleMenuEntry = {
    manage?: WorkspaceMenuLevel;
    menu: WorkspaceMenu;
    operations: WorkspaceMenuOperation[];
    view?: WorkspaceMenuLevel;
    workspace: Workspace;
};

export type RoleCatalogIndex = {
    menus: RoleMenuEntry[];
    operationCount: number;
    permissions: Set<string>;
    resourceLabels: Record<string, string>;
    templateOrder: Map<string, number>;
    templates: Set<string>;
};

export type RolePermissionView = {
    draft: RolePermissionDraft;
    hasWildcard: boolean;
    signature: string;
};

export type RoleRuleRow = {
    direct: boolean;
    key: string;
    rule: PolicyRule;
    sources: string[];
};

export type RoleRuleCollection = {
    rows: RoleRuleRow[];
};

export type RoleWorkspaceAccess = {
    accessible: boolean;
    entries: RoleMenuEntry[];
    grantedOperationCount: number;
    menuCount: number;
    operationCount: number;
    visibleEntries: RoleMenuEntry[];
    workspace: Workspace;
};

export const DIRECT_ROLE_RULE_SOURCE = "直接配置";

const VERB_LABELS: Record<string, string> = {
    "*": "全部动作",
    create: "创建",
    delete: "删除",
    get: "查看",
    list: "列表",
    patch: "局部更新",
    post: "提交",
    update: "更新",
    watch: "监听",
};

const VERB_THEMES: Record<string, "default" | "primary" | "warning" | "danger" | "success"> = {
    "*": "primary",
    create: "success",
    delete: "danger",
    get: "primary",
    list: "primary",
    patch: "warning",
    update: "warning",
    watch: "primary",
};

export function buildRoleCatalogIndex(catalog?: PermissionCatalog): RoleCatalogIndex {
    const menus: RoleMenuEntry[] = [];
    const templates = new Set<string>();
    const templateOrder = new Map<string, number>();
    const permissions = new Set<string>();
    let order = 0;

    for (const workspace of catalog?.workspaces ?? []) {
        for (const menu of workspace.menus ?? []) {
            const entry: RoleMenuEntry = {
                manage: menu.manage,
                menu,
                operations: menu.operations ?? [],
                view: menu.view,
                workspace,
            };
            menus.push(entry);

            for (const level of [menu.view, menu.manage]) {
                order = registerTemplate(templates, templateOrder, permissions, level, order);
            }
            for (const operation of entry.operations) {
                order = registerTemplate(templates, templateOrder, permissions, operation, order);
            }
        }
    }

    return {
        menus,
        operationCount: menus.reduce((total, entry) => total + entry.operations.length, 0),
        permissions,
        resourceLabels: catalog?.resourceLabels ?? {},
        templateOrder,
        templates,
    };
}

export function buildRolePermissionView(
    role: Role,
    rolesByName: Map<string, Role>,
    index: RoleCatalogIndex,
): RolePermissionView {
    const inherited = collectRoleUiPermissions(role.metadata.name, rolesByName);
    const effectivePermissions = expandPermissions(inherited, index);
    const hasWildcard = effectivePermissions.has("*");
    const draft = createDraft(index, effectivePermissions, hasWildcard);

    return {
        draft,
        hasWildcard,
        signature: permissionDraftSignature(draft),
    };
}

export function countRolePermissionDraft(
    index: RoleCatalogIndex,
    draft: RolePermissionDraft,
): { menuCount: number; operationCount: number } {
    let menuCount = 0;
    let operationCount = 0;

    for (const entry of index.menus) {
        const manageChecked = Boolean(entry.manage?.permission && draft.manage.has(entry.manage.permission));
        const viewChecked =
            entry.menu.publicMenu ||
            manageChecked ||
            Boolean(entry.view?.permission && draft.view.has(entry.view.permission));

        if (viewChecked || manageChecked) {
            menuCount += 1;
        }
        operationCount += manageChecked
            ? entry.operations.length
            : entry.operations.filter((operation) => operation.permission && draft.operation.has(operation.permission))
                  .length;
    }

    return { menuCount, operationCount };
}

export function buildRoleWorkspaceAccess(index: RoleCatalogIndex, draft: RolePermissionDraft): RoleWorkspaceAccess[] {
    const workspaces = new Map<string, RoleWorkspaceAccess>();

    for (const entry of index.menus) {
        const key = entry.workspace.id ?? entry.workspace.name ?? "workspace";
        const access = workspaces.get(key) ?? {
            accessible: false,
            entries: [],
            grantedOperationCount: 0,
            menuCount: 0,
            operationCount: 0,
            visibleEntries: [],
            workspace: entry.workspace,
        };

        access.entries.push(entry);
        access.menuCount += 1;
        access.operationCount += entry.operations.length;
        if (isMenuVisible(entry, draft)) {
            access.visibleEntries.push(entry);
        }
        access.grantedOperationCount += entry.operations.filter((operation) =>
            isOperationGranted(entry, operation.permission, draft),
        ).length;
        access.accessible = access.visibleEntries.length > 0;
        workspaces.set(key, access);
    }

    return Array.from(workspaces.values()).sort(
        (left, right) => (left.workspace.order ?? 0) - (right.workspace.order ?? 0),
    );
}

export function emptyRolePermissionDraft(): RolePermissionDraft {
    return {
        manage: new Set(),
        operation: new Set(),
        view: new Set(),
    };
}

export function cloneRolePermissionDraft(draft: RolePermissionDraft): RolePermissionDraft {
    return {
        manage: new Set(draft.manage),
        operation: new Set(draft.operation),
        view: new Set(draft.view),
    };
}

export function permissionDraftSignature(draft: RolePermissionDraft): string {
    return [
        Array.from(draft.view).sort().join("|"),
        Array.from(draft.manage).sort().join("|"),
        Array.from(draft.operation).sort().join("|"),
    ].join("::");
}

export function sameRolePermissionDraft(left: RolePermissionDraft, right: RolePermissionDraft): boolean {
    return permissionDraftSignature(left) === permissionDraftSignature(right);
}

export function buildRolePermissionPayload(role: Role, index: RoleCatalogIndex, draft: RolePermissionDraft): Role {
    const preservedDependencies = roleDependencies(role).filter((dependency) => !index.templates.has(dependency));
    const selectedTemplates = Array.from(collectSelectedTemplates(draft, index));
    const dependencies = Array.from(new Set([...preservedDependencies, ...selectedTemplates]));
    const directPermissions = Array.from(
        new Set(roleDirectPermissions(role).filter((permission) => !index.permissions.has(permission))),
    );

    return roleWithAnnotations(role, {
        dependencies: dependencies.length > 0 ? JSON.stringify(dependencies) : undefined,
        permissions: directPermissions.length > 0 ? JSON.stringify(directPermissions) : undefined,
    });
}

export function buildRoleProfilePayload(role: Role, values: { description?: string; displayName: string }): Role {
    return roleWithAnnotations(role, {
        description: values.description?.trim() ? values.description.trim() : undefined,
        displayName: values.displayName.trim(),
    });
}

export function createRolePayload(values: { description?: string; displayName: string; name: string }): Role {
    return {
        apiVersion: "pudans.tolim.cn/v1alpha1",
        kind: "Role",
        metadata: {
            annotations: {
                [ROLE_DESCRIPTION_ANNOTATION]: values.description?.trim() ?? "",
                [ROLE_DISPLAY_NAME_ANNOTATION]: values.displayName.trim(),
            },
            name: values.name.trim(),
        },
        rules: [],
    };
}

export function collectRoleRuleRows(role: Role, rolesByName: Map<string, Role>): RoleRuleCollection {
    const rows = new Map<string, RoleRuleRow>();
    const visited = new Set<string>([role.metadata.name]);

    for (const rule of role.rules ?? []) {
        addRuleRow(rows, rule, DIRECT_ROLE_RULE_SOURCE);
    }

    const visit = (roleName: string) => {
        if (visited.has(roleName)) {
            return;
        }
        visited.add(roleName);

        const dependency = rolesByName.get(roleName);
        if (!dependency) {
            return;
        }

        for (const rule of dependency.rules ?? []) {
            addRuleRow(rows, rule, roleDisplayName(dependency));
        }
        for (const child of roleDependencies(dependency)) {
            visit(child);
        }
    };

    for (const dependency of roleDependencies(role)) {
        visit(dependency);
    }

    const sortedRows = Array.from(rows.values()).sort((left, right) => {
        if (left.direct !== right.direct) {
            return left.direct ? -1 : 1;
        }
        return left.key.localeCompare(right.key);
    });

    return { rows: sortedRows };
}

export function operationTypeLabel(type?: string): string {
    if (type === "table") {
        return "表格";
    }
    if (type === "button") {
        return "按钮";
    }
    return type || "操作";
}

export function resourceLabel(index: RoleCatalogIndex, resource: string): string {
    if (resource === "*") {
        return "全部资源";
    }
    if (resource === "-") {
        return "本人";
    }
    return index.resourceLabels[resource] ?? "未收录资源";
}

export function scopeLabel(index: RoleCatalogIndex, value: string): string {
    if (value === "-") {
        return "本人";
    }
    return index.resourceLabels[value] ?? "未收录范围";
}

export function verbLabel(verb: string): string {
    return VERB_LABELS[verb] ?? verb;
}

export function verbTheme(verb: string): "default" | "primary" | "warning" | "danger" | "success" {
    return VERB_THEMES[verb] ?? "default";
}

export function actorCanGrant(permissions: string[] | undefined, permission: string): boolean {
    return Boolean(permissions?.includes("*") || permissions?.includes(permission));
}

export function isMenuVisible(entry: RoleMenuEntry, draft: RolePermissionDraft): boolean {
    return Boolean(
        entry.menu.publicMenu ||
        (entry.manage?.permission && draft.manage.has(entry.manage.permission)) ||
        (entry.view?.permission && draft.view.has(entry.view.permission)),
    );
}

export function isOperationGranted(
    entry: RoleMenuEntry,
    permission: string | undefined,
    draft: RolePermissionDraft,
): boolean {
    if (entry.manage?.permission && draft.manage.has(entry.manage.permission)) {
        return true;
    }
    return Boolean(permission && draft.operation.has(permission));
}

function collectSelectedTemplates(draft: RolePermissionDraft, index: RoleCatalogIndex): Set<string> {
    const templates = new Set<string>();

    for (const entry of index.menus) {
        const manageChecked = Boolean(entry.manage?.permission && draft.manage.has(entry.manage.permission));
        const viewChecked = Boolean(entry.view?.permission && draft.view.has(entry.view.permission));

        if (viewChecked && entry.view?.template) {
            templates.add(entry.view.template);
        }
        if (manageChecked) {
            if (entry.manage?.template) {
                templates.add(entry.manage.template);
            }
            if (entry.view?.template) {
                templates.add(entry.view.template);
            }
            for (const operation of entry.operations) {
                if (operation.template) {
                    templates.add(operation.template);
                }
            }
            continue;
        }
        for (const operation of entry.operations) {
            if (operation.template && operation.permission && draft.operation.has(operation.permission)) {
                templates.add(operation.template);
            }
        }
    }

    return new Set(
        Array.from(templates).sort(
            (left, right) => (index.templateOrder.get(left) ?? 0) - (index.templateOrder.get(right) ?? 0),
        ),
    );
}

function registerTemplate(
    templates: Set<string>,
    templateOrder: Map<string, number>,
    permissions: Set<string>,
    level: WorkspaceMenuLevel | WorkspaceMenuOperation | undefined,
    order: number,
): number {
    if (!level?.template) {
        if (level?.permission) {
            permissions.add(level.permission);
        }
        return order;
    }

    if (level.permission) {
        permissions.add(level.permission);
    }
    if (!templates.has(level.template)) {
        templates.add(level.template);
        templateOrder.set(level.template, order);
        return order + 1;
    }
    return order;
}

function createDraft(
    index: RoleCatalogIndex,
    effectivePermissions: Set<string>,
    hasWildcard: boolean,
): RolePermissionDraft {
    const draft = emptyRolePermissionDraft();

    for (const entry of index.menus) {
        const managePermission = entry.manage?.permission;
        const viewPermission = entry.view?.permission;
        const manageChecked = hasWildcard || Boolean(managePermission && effectivePermissions.has(managePermission));
        const viewChecked =
            entry.menu.publicMenu ||
            hasWildcard ||
            manageChecked ||
            Boolean(viewPermission && effectivePermissions.has(viewPermission));

        if (viewPermission && viewChecked) {
            draft.view.add(viewPermission);
        }
        if (managePermission && manageChecked) {
            draft.manage.add(managePermission);
            continue;
        }
        for (const operation of entry.operations) {
            if (operation.permission && (hasWildcard || effectivePermissions.has(operation.permission))) {
                draft.operation.add(operation.permission);
            }
        }
    }

    return draft;
}

function collectRoleUiPermissions(roleName: string, rolesByName: Map<string, Role>): Set<string> {
    const permissions = new Set<string>();
    const visited = new Set<string>();
    const queue = [roleName];

    while (queue.length > 0) {
        const name = queue.pop();
        if (!name || visited.has(name)) {
            continue;
        }
        visited.add(name);

        const role = rolesByName.get(name);
        if (!role) {
            continue;
        }

        for (const permission of roleDirectPermissions(role)) {
            permissions.add(permission);
        }
        queue.push(...roleDependencies(role));
    }

    return permissions;
}

function expandPermissions(permissions: Set<string>, index: RoleCatalogIndex): Set<string> {
    if (permissions.has("*")) {
        return new Set(["*"]);
    }

    const expanded = new Set(permissions);
    let changed = true;

    while (changed) {
        changed = false;
        for (const entry of index.menus) {
            const managePermission = entry.manage?.permission;
            if (!managePermission || !expanded.has(managePermission)) {
                continue;
            }
            if (entry.view?.permission && !expanded.has(entry.view.permission)) {
                expanded.add(entry.view.permission);
                changed = true;
            }
            for (const operation of entry.operations) {
                if (operation.permission && !expanded.has(operation.permission)) {
                    expanded.add(operation.permission);
                    changed = true;
                }
            }
        }
    }

    return expanded;
}

function addRuleRow(rows: Map<string, RoleRuleRow>, rule: PolicyRule, source: string) {
    const normalized = normalizePolicyRule(rule);
    const key = policyRuleSignature(normalized);
    const existing = rows.get(key);

    if (existing) {
        if (!existing.sources.includes(source)) {
            existing.sources.push(source);
        }
        existing.direct = existing.direct || source === DIRECT_ROLE_RULE_SOURCE;
        return;
    }

    rows.set(key, {
        direct: source === DIRECT_ROLE_RULE_SOURCE,
        key,
        rule: normalized,
        sources: [source],
    });
}

function normalizePolicyRule(rule: PolicyRule): PolicyRule {
    return {
        apiGroups: normalizeList(rule.apiGroups),
        nonResourceURLs: normalizeList(rule.nonResourceURLs),
        resourceNames: normalizeList(rule.resourceNames),
        resources: normalizeList(rule.resources),
        verbs: normalizeList(rule.verbs),
    };
}

function normalizeList(values?: string[]): string[] {
    return Array.from(new Set((values ?? []).map((value) => value.trim()).filter(Boolean))).sort();
}

function policyRuleSignature(rule: PolicyRule): string {
    return JSON.stringify([
        rule.apiGroups ?? [],
        rule.resources ?? [],
        rule.resourceNames ?? [],
        rule.nonResourceURLs ?? [],
        rule.verbs ?? [],
    ]);
}

function roleWithAnnotations(
    role: Role,
    updates: { dependencies?: string; description?: string; displayName?: string; permissions?: string },
): Role {
    const annotations = { ...(role.metadata.annotations ?? {}) };
    const keys = {
        dependencies: ROLE_DEPENDENCIES_ANNOTATION,
        description: ROLE_DESCRIPTION_ANNOTATION,
        displayName: ROLE_DISPLAY_NAME_ANNOTATION,
        permissions: ROLE_UI_PERMISSIONS_ANNOTATION,
    } as const;

    for (const [name, value] of Object.entries(updates) as [keyof typeof keys, string | undefined][]) {
        const key = keys[name];
        if (value === undefined || value === "") {
            delete annotations[key];
        } else {
            annotations[key] = value;
        }
    }

    return {
        ...role,
        metadata: {
            ...role.metadata,
            annotations: Object.keys(annotations).length > 0 ? annotations : undefined,
        },
        rules: role.rules ?? [],
    };
}
