import { useMemo, useState } from "react";

import type { Role } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Boxes, Info, LayoutDashboard, Loader2, Lock, Save, Undo2 } from "lucide-react";
import { toast } from "sonner";

import { FloatingActionBar } from "@/components/floating-action-bar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import type {
  CatalogMenu,
  CatalogOperation,
  CatalogWorkspace,
  PermissionCatalog,
} from "@/modules/workspace/lib/permission-api";

import { resolveRoleApiError } from "../lib/api";
import { MY_UI_PERMISSIONS_QUERY_KEY } from "../lib/current-permissions";
import {
  type DataAccessLevel,
  type DataAccessRow,
  accessLevelLabel,
  catalogMenuEntries,
  catalogPermissionKeys,
  catalogTemplateMap,
  dataAccessRows,
  dependencyClosure,
  effectiveRules,
  expandCatalogPermissions,
  resourceNameLabels,
  roleDependencies,
  roleUiPermissions,
  sameStringSet,
  templatesFromPermissions,
  verbLabels,
  withRolePermissions,
} from "../lib/role-model";

type RolePermissionEditorProps = {
  role: Role;
  catalog: PermissionCatalog;
  readOnly: boolean;
  builtin: boolean;
  allowedPermissions: readonly string[];
};

export function RolePermissionEditor({
  role,
  catalog,
  readOnly,
  builtin,
  allowedPermissions,
}: RolePermissionEditorProps) {
  const queryClient = useQueryClient();
  const templateMap = useMemo(() => catalogTemplateMap(catalog), [catalog]);
  const catalogPermissions = useMemo(() => catalogPermissionKeys(catalog), [catalog]);
  const initialSelection = useMemo(() => {
    const directPermissions = roleUiPermissions(role);
    const fromPermissions = templatesFromPermissions(
      expandCatalogPermissions(directPermissions, catalog),
      catalog,
    );
    const dependencies = roleDependencies(role);
    if (builtin) {
      return [...new Set([...dependencies, ...fromPermissions])];
    }
    return dependencies.length > 0 ? dependencies : fromPermissions;
  }, [builtin, catalog, role]);
  const [selection, setSelection] = useState<string[]>(() => [...initialSelection]);
  const [baseline, setBaseline] = useState<string[]>(() => [...initialSelection]);
  const selectionSet = useMemo(() => new Set(selection), [selection]);
  const closure = useMemo(
    () => dependencyClosure(selection, templateMap),
    [selection, templateMap],
  );
  const wildcard = roleUiPermissions(role).includes("*");
  const locked = readOnly || builtin || wildcard;
  const dirty = !sameStringSet(selection, baseline);
  const allowedPermissionSet = useMemo(() => new Set(allowedPermissions), [allowedPermissions]);
  const allPermissionsGranted = allowedPermissionSet.has("*");
  const canGrant = (permission: string | undefined) =>
    !permission || allPermissionsGranted || allowedPermissionSet.has(permission);
  const limited = !allPermissionsGranted;

  const isInherited = (template: string) => closure.has(template) && !selectionSet.has(template);

  const toggleView = (menu: CatalogMenu, checked: boolean) => {
    const view = menu.view;
    if (!view) {
      return;
    }
    setSelection((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(view.template);
      } else {
        for (const template of menuTemplateNames(menu)) {
          next.delete(template);
        }
      }
      return [...next];
    });
  };

  const toggleManage = (menu: CatalogMenu, checked: boolean) => {
    const manage = menu.manage;
    if (!manage) {
      return;
    }
    setSelection((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(manage.template);
        if (menu.view) {
          next.delete(menu.view.template);
        }
        for (const operation of menu.operations ?? []) {
          next.delete(operation.template);
        }
      } else {
        next.delete(manage.template);
        if (menu.view) {
          next.add(menu.view.template);
        } else {
          for (const operation of menu.operations ?? []) {
            next.delete(operation.template);
          }
        }
      }
      return [...next];
    });
  };

  const toggleOperation = (operation: CatalogOperation, checked: boolean) => {
    setSelection((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(operation.template);
      } else {
        next.delete(operation.template);
      }
      return [...next];
    });
  };

  const toggleWorkspace = (workspace: CatalogWorkspace, checked: boolean) => {
    setSelection((current) => {
      const next = new Set(current);
      for (const menu of workspace.menus ?? []) {
        if (checked) {
          const access = menu.view ?? menu.manage;
          if (access) {
            next.add(access.template);
          }
        } else {
          for (const template of menuTemplateNames(menu)) {
            next.delete(template);
          }
        }
      }
      return [...next];
    });
  };

  const saveMutation = useMutation({
    mutationFn: async (templates: string[]) => {
      const directPermissions = roleUiPermissions(role).filter(
        (permission) => !catalogPermissions.has(permission),
      );
      await apiClient.role.updateRole({
        name: role.metadata.name,
        role: withRolePermissions(role, templates, directPermissions),
      });
    },
    onSuccess: async (_data, templates) => {
      setBaseline(templates);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["roles"] }),
        queryClient.invalidateQueries({ queryKey: MY_UI_PERMISSIONS_QUERY_KEY }),
      ]);
      toast.success("角色权限已保存。");
    },
    onError: (error) => toast.error(resolveRoleApiError(error)),
  });

  const workspaces = catalog.workspaces ?? [];
  const operationMenus = catalogMenuEntries(catalog).filter(
    ({ menu }) => (menu.operations ?? []).length > 0,
  );
  const dataRules = useMemo(
    () => effectiveRules(role.rules ?? [], closure, templateMap),
    [role.rules, closure, templateMap],
  );
  const dataRows = useMemo(() => dataAccessRows(dataRules), [dataRules]);

  return (
    <>
      <Tabs defaultValue="menu">
        <TabsList className="mx-4 mt-4">
          <TabsTrigger value="menu">菜单权限</TabsTrigger>
          <TabsTrigger value="operations">操作权限</TabsTrigger>
          <TabsTrigger value="data">数据权限</TabsTrigger>
          <TabsTrigger value="workspaces">工作区权限</TabsTrigger>
        </TabsList>

        <TabsContent value="menu" className="p-5">
          <div className="space-y-8">
            {locked ? (
              <EditorLockNote readOnly={readOnly} builtin={builtin} wildcard={wildcard} />
            ) : null}
            {limited && !locked ? <PermissionLimitNote /> : null}
            {workspaces.length === 0 ? (
              <ConsoleEmpty>权限目录暂无可配置的菜单。</ConsoleEmpty>
            ) : (
              workspaces.map((workspace) => (
                <section key={workspace.id} className="space-y-3">
                  <div>
                    <h3 className="text-sm font-semibold">{workspace.name}</h3>
                    <p className="text-xs text-muted-foreground">{workspace.plan}</p>
                  </div>
                  {(workspace.menus ?? []).length === 0 ? (
                    <p className="text-xs text-muted-foreground">该工作区暂无可配置的菜单。</p>
                  ) : (
                    <div className="grid gap-2 lg:grid-cols-2">
                      {(workspace.menus ?? []).map((menu) => {
                        const view = menu.view;
                        const manage = menu.manage;
                        const manageGranted = manage ? closure.has(manage.template) : false;
                        const viewGranted = view ? closure.has(view.template) : false;
                        return (
                          <MenuAccessCard
                            key={menu.key}
                            menu={menu}
                            accessible={viewGranted || manageGranted}
                            manageable={manageGranted}
                            viewDisabled={
                              locked ||
                              manageGranted ||
                              (view
                                ? isInherited(view.template) ||
                                  (!viewGranted && !canGrant(view.permission))
                                : false)
                            }
                            manageDisabled={
                              locked ||
                              (manage
                                ? isInherited(manage.template) ||
                                  (!manageGranted && !canGrant(manage.permission))
                                : false)
                            }
                            onToggleView={(checked) => toggleView(menu, checked)}
                            onToggleManage={(checked) => toggleManage(menu, checked)}
                          />
                        );
                      })}
                    </div>
                  )}
                </section>
              ))
            )}
          </div>
        </TabsContent>

        <TabsContent value="operations" className="p-5">
          <div className="space-y-8">
            {locked ? (
              <EditorLockNote readOnly={readOnly} builtin={builtin} wildcard={wildcard} />
            ) : null}
            {limited && !locked ? <PermissionLimitNote /> : null}
            {operationMenus.length === 0 ? (
              <ConsoleEmpty>权限目录暂无可配置的操作。</ConsoleEmpty>
            ) : (
              operationMenus.map(({ workspace, menu }) => {
                const accessible = Boolean(
                  menu.publicMenu ||
                  (menu.view && closure.has(menu.view.template)) ||
                  (menu.manage && closure.has(menu.manage.template)),
                );
                const manageGranted = menu.manage ? closure.has(menu.manage.template) : false;
                return (
                  <section key={menu.key} className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-sm font-semibold">{menu.label}</h3>
                      <span className="text-xs text-muted-foreground">{workspace.name}</span>
                      {manageGranted ? <Badge variant="secondary">已由管理权限授予</Badge> : null}
                    </div>
                    {!accessible ? (
                      <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
                        <Info className="mt-0.5 size-3.5 shrink-0" />
                        请先在「菜单权限」中授予该菜单的访问权限。
                      </p>
                    ) : null}
                    <div className="grid gap-2 lg:grid-cols-2">
                      {(menu.operations ?? []).map((operation) => {
                        const granted = manageGranted || closure.has(operation.template);
                        return (
                          <OperationCard
                            key={operation.template}
                            operation={operation}
                            checked={granted}
                            disabled={
                              locked ||
                              manageGranted ||
                              !accessible ||
                              isInherited(operation.template) ||
                              (!granted && !canGrant(operation.permission))
                            }
                            onCheckedChange={(checked) => toggleOperation(operation, checked)}
                          />
                        );
                      })}
                    </div>
                  </section>
                );
              })
            )}
          </div>
        </TabsContent>

        <TabsContent value="data" className="p-5">
          <DataPermissionsPanel
            rows={dataRows}
            resourceLabels={catalog.resourceLabels}
            preview={dirty && !locked}
          />
        </TabsContent>

        <TabsContent value="workspaces" className="p-5">
          <div className="space-y-3">
            {limited && !locked ? <PermissionLimitNote /> : null}
            {workspaces.length === 0 ? (
              <ConsoleEmpty>权限目录暂无工作区。</ConsoleEmpty>
            ) : (
              workspaces.map((workspace) => {
                const alwaysAccessible = (workspace.menus ?? []).some((menu) => menu.publicMenu);
                const accessible =
                  alwaysAccessible ||
                  (workspace.menus ?? []).some(
                    (menu) =>
                      (menu.view ? closure.has(menu.view.template) : false) ||
                      (menu.manage ? closure.has(menu.manage.template) : false),
                  );
                const directlySelected = (workspace.menus ?? []).some((menu) =>
                  menuTemplateNames(menu).some((template) => selectionSet.has(template)),
                );
                const canAddWorkspace = (workspace.menus ?? []).every((menu) => {
                  const access = menu.view ?? menu.manage;
                  return canGrant(access?.permission);
                });
                return (
                  <div
                    key={workspace.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4"
                  >
                    <div className="flex items-center gap-3">
                      <span className="flex size-9 items-center justify-center rounded-lg bg-muted">
                        {alwaysAccessible ? (
                          <Boxes className="size-5" />
                        ) : (
                          <LayoutDashboard className="size-5" />
                        )}
                      </span>
                      <div>
                        <p className="text-sm font-medium">{workspace.name}</p>
                        <p className="text-xs text-muted-foreground">{workspace.plan}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs text-muted-foreground">
                        {accessible ? "可访问" : "不可访问"}
                      </span>
                      <Switch
                        checked={accessible}
                        disabled={
                          locked ||
                          alwaysAccessible ||
                          (accessible && !directlySelected) ||
                          (!accessible && !canAddWorkspace)
                        }
                        onCheckedChange={(checked) => toggleWorkspace(workspace, checked)}
                        aria-label={`${workspace.name}访问权限`}
                      />
                    </div>
                  </div>
                );
              })
            )}
            <p className="flex items-start gap-2 text-xs text-muted-foreground">
              <Info className="mt-0.5 size-3.5 shrink-0" />
              默认工作区始终可访问；关闭其他工作区会移除该工作区下的全部菜单和操作权限。
            </p>
          </div>
        </TabsContent>
      </Tabs>

      <FloatingActionBar open={dirty && !locked}>
        <Button
          type="button"
          variant="outline"
          disabled={saveMutation.isPending}
          onClick={() => setSelection(baseline)}
        >
          <Undo2 />
          撤销更改
        </Button>
        <Button
          type="button"
          disabled={saveMutation.isPending}
          onClick={() => saveMutation.mutate(selection)}
        >
          {saveMutation.isPending ? <Loader2 className="animate-spin" /> : <Save />}
          保存更改
        </Button>
      </FloatingActionBar>
    </>
  );
}

function menuTemplateNames(menu: CatalogMenu): string[] {
  const names: string[] = [];
  if (menu.view) {
    names.push(menu.view.template);
  }
  if (menu.manage) {
    names.push(menu.manage.template);
  }
  for (const operation of menu.operations ?? []) {
    names.push(operation.template);
  }
  return names;
}

type EditorLockNoteProps = {
  readOnly: boolean;
  builtin: boolean;
  wildcard: boolean;
};

function EditorLockNote({ readOnly, builtin, wildcard }: EditorLockNoteProps) {
  const message = wildcard
    ? "该角色拥有全部权限，无法在此单独调整。"
    : builtin
      ? "系统内置角色不可编辑，可在下方查看当前权限范围。"
      : readOnly
        ? "当前账号没有编辑该角色的权限。"
        : "";

  return (
    <div className="flex items-start gap-2 rounded-lg border bg-muted/40 p-3 text-xs text-muted-foreground">
      <Lock className="mt-0.5 size-3.5 shrink-0" />
      {message}
    </div>
  );
}

function PermissionLimitNote() {
  return (
    <div className="flex items-start gap-2 rounded-lg border bg-muted/40 p-3 text-xs text-muted-foreground">
      <Info className="mt-0.5 size-3.5 shrink-0" />
      只能授予当前账号已有的权限，超出范围的选项不可选。
    </div>
  );
}

type MenuAccessCardProps = {
  menu: CatalogMenu;
  accessible: boolean;
  manageable: boolean;
  viewDisabled: boolean;
  manageDisabled: boolean;
  onToggleView: (checked: boolean) => void;
  onToggleManage: (checked: boolean) => void;
};

function MenuAccessCard({
  menu,
  accessible,
  manageable,
  viewDisabled,
  manageDisabled,
  onToggleView,
  onToggleManage,
}: MenuAccessCardProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-3 rounded-lg border p-3 transition-colors",
        accessible && "border-primary/40 bg-primary/5",
      )}
    >
      <div className="space-y-0.5">
        <p className="text-sm font-medium">{menu.label}</p>
        {menu.description ? (
          <p className="text-xs leading-5 text-muted-foreground">{menu.description}</p>
        ) : null}
      </div>
      {menu.publicMenu ? (
        <Badge variant="secondary" className="w-fit">
          始终可访问
        </Badge>
      ) : menu.view || menu.manage ? (
        <div className="flex flex-wrap items-center gap-4">
          {menu.view ? (
            <label className="flex cursor-pointer items-center gap-2 text-xs">
              <Checkbox
                checked={accessible}
                disabled={viewDisabled}
                onCheckedChange={(checked) => onToggleView(checked === true)}
              />
              可访问
            </label>
          ) : null}
          {menu.manage ? (
            <label className="flex cursor-pointer items-center gap-2 text-xs">
              <Checkbox
                checked={manageable}
                disabled={manageDisabled}
                onCheckedChange={(checked) => onToggleManage(checked === true)}
              />
              管理
            </label>
          ) : null}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">该菜单未配置权限模板。</p>
      )}
    </div>
  );
}

type OperationCardProps = {
  operation: CatalogOperation;
  checked: boolean;
  disabled: boolean;
  onCheckedChange: (checked: boolean) => void;
};

function OperationCard({ operation, checked, disabled, onCheckedChange }: OperationCardProps) {
  return (
    <label
      className={cn(
        "flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors",
        checked && "border-primary/40 bg-primary/5",
        disabled && "cursor-not-allowed opacity-70",
      )}
    >
      <Checkbox
        checked={checked}
        disabled={disabled}
        onCheckedChange={(next) => onCheckedChange(next === true)}
        className="mt-0.5"
      />
      <span className="min-w-0 flex-1 space-y-1">
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">{operation.label}</span>
          <Badge variant="outline">{operation.type === "table" ? "表格" : "按钮"}</Badge>
        </span>
        {operation.description ? (
          <span className="block text-xs leading-5 text-muted-foreground">
            {operation.description}
          </span>
        ) : null}
      </span>
    </label>
  );
}

type DataPermissionsPanelProps = {
  rows: readonly DataAccessRow[];
  resourceLabels?: Record<string, string>;
  preview: boolean;
};

function DataPermissionsPanel({ rows, resourceLabels, preview }: DataPermissionsPanelProps) {
  const counts: Record<DataAccessLevel, number> = { manage: 0, edit: 0, read: 0 };
  rows.forEach((row) => {
    counts[row.level] += 1;
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        <span>可管理 {counts.manage}</span>
        <span>·</span>
        <span>可编辑 {counts.edit}</span>
        <span>·</span>
        <span>仅可读 {counts.read}</span>
        <span className="w-full sm:ms-auto sm:w-auto">
          {preview ? "以下为保存后的 API 规则预览。" : "数据权限由角色的 API 规则和所选模板决定。"}
        </span>
      </div>

      {rows.length === 0 ? (
        <ConsoleEmpty>该角色暂无数据权限。</ConsoleEmpty>
      ) : (
        <div className="overflow-x-auto rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>资源</TableHead>
                <TableHead>权限级别</TableHead>
                <TableHead>范围</TableHead>
                <TableHead>允许操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.raw}>
                  <TableCell className="text-sm font-medium">
                    {resourceLabels?.[row.raw] ?? row.raw}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={
                        row.level === "manage"
                          ? "default"
                          : row.level === "edit"
                            ? "secondary"
                            : "outline"
                      }
                    >
                      {accessLevelLabel(row.level)}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {resourceNameLabels(row.resourceNames)}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {verbLabels(row.verbs).join("、")}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
