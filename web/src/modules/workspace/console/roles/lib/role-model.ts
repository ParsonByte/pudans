import type { PolicyRule, Role } from "@pudans-dev/api-client";

import type {
  CatalogMenu,
  CatalogWorkspace,
  PermissionCatalog,
} from "@/modules/workspace/lib/permission-api";
import { hasUiPermission } from "@/modules/workspace/lib/permissions";

export const ROLE_API_VERSION = "pudans.tolim.cn/v1alpha1";

export const ROLE_KIND = "Role";

export const ROLE_DISPLAY_NAME_ANNO = "rbac.authorization.pudans.tolim.cn/display-name";

export const ROLE_DESCRIPTION_ANNO = "rbac.authorization.pudans.tolim.cn/description";

export const ROLE_UI_PERMISSIONS_ANNO = "rbac.authorization.pudans.tolim.cn/ui-permissions";

export const ROLE_DEPENDENCIES_ANNO = "rbac.authorization.pudans.tolim.cn/dependencies";

const ROLE_TEMPLATE_LABEL = "pudans.tolim.cn/role-template";

const ROLE_HIDDEN_LABEL = "pudans.tolim.cn/hidden";

const ROLE_BUILTIN_LABEL = "pudans.tolim.cn/builtin";

const ROLE_SYSTEM_RESERVED_LABEL = "rbac.authorization.pudans.tolim.cn/system-reserved";

const ACCESS_LEVEL_RANK: Record<DataAccessLevel, number> = {
  manage: 0,
  edit: 1,
  read: 2,
};

export type DataAccessLevel = "manage" | "edit" | "read";

export type DataAccessRow = {
  level: DataAccessLevel;
  verbs: string[];
  resourceNames: string[];
  raw: string;
};

export type TemplateDefinition = {
  dependencies: string[];
  rules: PolicyRule[];
};

export type CatalogMenuEntry = {
  workspace: CatalogWorkspace;
  menu: CatalogMenu;
};

function annotationsOf(role: Role): Record<string, string> {
  return role.metadata.annotations ?? {};
}

function labelsOf(role: Role): Record<string, string> {
  return role.metadata.labels ?? {};
}

export function isRoleTemplate(role: Role): boolean {
  return labelsOf(role)[ROLE_TEMPLATE_LABEL] === "true";
}

export function isHiddenRole(role: Role): boolean {
  return labelsOf(role)[ROLE_HIDDEN_LABEL] === "true";
}

export function isBuiltinRole(role: Role): boolean {
  const labels = labelsOf(role);
  return labels[ROLE_BUILTIN_LABEL] === "true" || labels[ROLE_SYSTEM_RESERVED_LABEL] === "true";
}

export function roleDisplayName(role: Role): string {
  return annotationsOf(role)[ROLE_DISPLAY_NAME_ANNO] || role.metadata.name;
}

export function compareRoles(left: Role, right: Role): number {
  return (
    roleDisplayName(left).localeCompare(roleDisplayName(right), "zh-CN") ||
    left.metadata.name.localeCompare(right.metadata.name)
  );
}

export function visibleRoles(roles: readonly Role[]): Role[] {
  return roles.filter(
    (role) => !isRoleTemplate(role) && !isHiddenRole(role) && !role.metadata.deletionTimestamp,
  );
}

export function roleDescription(role: Role): string {
  return (
    annotationsOf(role)[ROLE_DESCRIPTION_ANNO] ||
    (isBuiltinRole(role) ? "系统内置角色。" : "自定义角色。")
  );
}

function parseStringArray(value: string | undefined): string[] {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter(
      (item): item is string => typeof item === "string" && item.trim().length > 0,
    );
  } catch {
    return [];
  }
}

export function roleUiPermissions(role: Role): string[] {
  return parseStringArray(annotationsOf(role)[ROLE_UI_PERMISSIONS_ANNO]);
}

export function roleDependencies(role: Role): string[] {
  return parseStringArray(annotationsOf(role)[ROLE_DEPENDENCIES_ANNO]);
}

export function withRolePermissions(
  role: Role,
  dependencies: readonly string[],
  directPermissions: readonly string[],
): Role {
  return {
    ...role,
    metadata: {
      ...role.metadata,
      annotations: {
        ...annotationsOf(role),
        [ROLE_DEPENDENCIES_ANNO]: JSON.stringify(dependencies),
        [ROLE_UI_PERMISSIONS_ANNO]: JSON.stringify(directPermissions),
      },
    },
  };
}

export function sameStringSet(left: readonly string[], right: readonly string[]): boolean {
  const leftSet = new Set(left);
  const rightSet = new Set(right);
  return leftSet.size === rightSet.size && [...leftSet].every((value) => rightSet.has(value));
}

export function catalogMenuEntries(catalog: PermissionCatalog): CatalogMenuEntry[] {
  return (catalog.workspaces ?? []).flatMap((workspace) =>
    (workspace.menus ?? []).map((menu) => ({ workspace, menu })),
  );
}

export function catalogPermissionKeys(catalog: PermissionCatalog): Set<string> {
  const permissions = new Set<string>();
  for (const { menu } of catalogMenuEntries(catalog)) {
    if (menu.view?.permission) {
      permissions.add(menu.view.permission);
    }
    if (menu.manage?.permission) {
      permissions.add(menu.manage.permission);
    }
    for (const operation of menu.operations ?? []) {
      if (operation.permission) {
        permissions.add(operation.permission);
      }
    }
  }
  return permissions;
}

export function catalogTemplateMap(catalog: PermissionCatalog): Map<string, TemplateDefinition> {
  const templates = new Map<string, TemplateDefinition>();
  for (const { menu } of catalogMenuEntries(catalog)) {
    if (menu.view) {
      templates.set(menu.view.template, {
        dependencies: menu.view.dependencies ?? [],
        rules: menu.view.rules ?? [],
      });
    }
    if (menu.manage) {
      templates.set(menu.manage.template, {
        dependencies: menu.manage.dependencies ?? [],
        rules: menu.manage.rules ?? [],
      });
    }
    for (const operation of menu.operations ?? []) {
      templates.set(operation.template, {
        dependencies: operation.dependencies ?? [],
        rules: operation.rules ?? [],
      });
    }
  }
  return templates;
}

export function dependencyClosure(
  selected: readonly string[],
  templates: ReadonlyMap<string, TemplateDefinition>,
): Set<string> {
  const closure = new Set(selected);
  let changed = true;
  while (changed) {
    changed = false;
    for (const template of [...closure]) {
      for (const dependency of templates.get(template)?.dependencies ?? []) {
        if (!closure.has(dependency)) {
          closure.add(dependency);
          changed = true;
        }
      }
    }
  }
  return closure;
}

export function templatesFromPermissions(
  permissions: Iterable<string>,
  catalog: PermissionCatalog,
): string[] {
  const permissionList = [...permissions];
  const templates = new Set<string>();
  for (const { menu } of catalogMenuEntries(catalog)) {
    const manageGranted = hasUiPermission(permissionList, menu.manage?.permission);
    if (manageGranted && menu.manage) {
      templates.add(menu.manage.template);
    } else if (hasUiPermission(permissionList, menu.view?.permission) && menu.view) {
      templates.add(menu.view.template);
    }
    if (manageGranted) {
      continue;
    }
    for (const operation of menu.operations ?? []) {
      if (hasUiPermission(permissionList, operation.permission)) {
        templates.add(operation.template);
      }
    }
  }
  return [...templates];
}

export function expandCatalogPermissions(
  permissions: readonly string[],
  catalog: PermissionCatalog,
): Set<string> {
  const expanded = new Set(permissions);
  if (expanded.has("*")) {
    return expanded;
  }
  for (const { menu } of catalogMenuEntries(catalog)) {
    if (!hasUiPermission(permissions, menu.manage?.permission)) {
      continue;
    }
    if (menu.view?.permission) {
      expanded.add(menu.view.permission);
    }
    for (const operation of menu.operations ?? []) {
      if (operation.permission) {
        expanded.add(operation.permission);
      }
    }
  }
  return expanded;
}

export function effectiveRules(
  roleRules: readonly PolicyRule[],
  closure: ReadonlySet<string>,
  templates: ReadonlyMap<string, TemplateDefinition>,
): PolicyRule[] {
  const seen = new Set<string>();
  const rules: PolicyRule[] = [];
  const add = (candidate: PolicyRule) => {
    const key = JSON.stringify(candidate);
    if (!seen.has(key)) {
      seen.add(key);
      rules.push(candidate);
    }
  };
  roleRules.forEach(add);
  for (const template of closure) {
    (templates.get(template)?.rules ?? []).forEach(add);
  }
  return rules;
}

export function dataAccessRows(rules: readonly PolicyRule[]): DataAccessRow[] {
  const resources = new Map<
    string,
    { verbs: Set<string>; resourceNames: Set<string>; allResourceNames: boolean }
  >();

  for (const rule of rules) {
    const keys =
      rule.resources && rule.resources.length > 0 ? rule.resources : (rule.nonResourceURLs ?? []);
    for (const key of keys) {
      if (!key) {
        continue;
      }
      const entry = resources.get(key) ?? {
        verbs: new Set<string>(),
        resourceNames: new Set<string>(),
        allResourceNames: false,
      };
      (rule.verbs ?? []).forEach((verb) => {
        if (verb) {
          entry.verbs.add(verb);
        }
      });
      const resourceNames = rule.resourceNames ?? [];
      if (resourceNames.length === 0) {
        entry.allResourceNames = true;
      } else {
        resourceNames.forEach((name) => {
          if (name) {
            entry.resourceNames.add(name);
          }
        });
      }
      resources.set(key, entry);
    }
  }

  return [...resources.entries()]
    .map(([raw, entry]) => {
      const verbs = [...entry.verbs].sort(
        (left, right) => verbSortIndex(left) - verbSortIndex(right) || left.localeCompare(right),
      );
      const level = accessLevel(verbs);
      if (!level) {
        return null;
      }
      return {
        level,
        verbs,
        resourceNames: entry.allResourceNames ? [] : [...entry.resourceNames],
        raw,
      };
    })
    .filter((row): row is DataAccessRow => row !== null)
    .sort(
      (left, right) =>
        ACCESS_LEVEL_RANK[left.level] - ACCESS_LEVEL_RANK[right.level] ||
        left.raw.localeCompare(right.raw),
    );
}

function accessLevel(verbs: readonly string[]): DataAccessLevel | null {
  if (verbs.includes("*") || verbs.includes("delete") || verbs.includes("deletecollection")) {
    return "manage";
  }
  if (verbs.some((verb) => ["create", "update", "patch"].includes(verb))) {
    return "edit";
  }
  if (verbs.some((verb) => ["get", "list", "watch"].includes(verb))) {
    return "read";
  }
  return null;
}

export function accessLevelLabel(level: DataAccessLevel): string {
  switch (level) {
    case "manage":
      return "可管理";
    case "edit":
      return "可编辑";
    default:
      return "仅可读";
  }
}

const VERB_LABELS: Record<string, string> = {
  "*": "全部操作",
  create: "创建",
  delete: "删除",
  deletecollection: "批量删除",
  get: "查看",
  list: "列表",
  patch: "修改",
  update: "编辑",
  watch: "监听",
};

export function verbLabels(verbs: readonly string[]): string[] {
  return verbs.map((verb) => VERB_LABELS[verb] ?? verb);
}

export function resourceNameLabels(resourceNames: readonly string[]): string {
  if (resourceNames.length === 0) {
    return "全部";
  }
  return resourceNames
    .map((name) => (name === "-" ? "本人" : name === "*" ? "全部" : name))
    .join("、");
}

function verbSortIndex(verb: string): number {
  const order = [
    "get",
    "list",
    "watch",
    "create",
    "update",
    "patch",
    "delete",
    "deletecollection",
    "*",
  ];
  const index = order.indexOf(verb);
  return index < 0 ? order.length : index;
}
