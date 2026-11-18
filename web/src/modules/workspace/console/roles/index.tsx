import { useMemo, useState } from "react";

import { type Role, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Lock, Plus, RefreshCw, Search, ShieldCheck, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { fetchPermissionCatalog } from "@/modules/workspace/lib/permission-api";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { CreateRoleDialog } from "./components/create-role-dialog";
import { RolePermissionEditor } from "./components/role-permission-editor";
import { resolveRoleApiError } from "./lib/api";
import { MY_UI_PERMISSIONS_QUERY_KEY, useMyUiPermissions } from "./lib/current-permissions";
import {
  compareRoles,
  isBuiltinRole,
  roleDescription,
  roleDisplayName,
  visibleRoles,
} from "./lib/role-model";
import { useRoles } from "./lib/use-roles";

export function RolesView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Role | null>(null);
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");

  const rolesQuery = useRoles();
  const myPermissionsQuery = useMyUiPermissions();

  const catalogQuery = useQuery({
    queryKey: ["permission-catalog"],
    queryFn: fetchPermissionCatalog,
    retry: false,
  });

  const roles = useMemo(() => visibleRoles(rolesQuery.data ?? []), [rolesQuery.data]);
  const systemRoles = useMemo(() => roles.filter(isBuiltinRole).sort(compareRoles), [roles]);
  const customRoles = useMemo(
    () => roles.filter((role) => !isBuiltinRole(role)).sort(compareRoles),
    [roles],
  );
  const selectedRole = useMemo(
    () =>
      roles.find((role) => role.metadata.name === selectedName) ?? customRoles[0] ?? systemRoles[0],
    [customRoles, roles, selectedName, systemRoles],
  );

  const normalizedSearch = search.trim().toLowerCase();
  const matchesSearch = (role: Role) => {
    if (!normalizedSearch) {
      return true;
    }
    return (
      roleDisplayName(role).toLowerCase().includes(normalizedSearch) ||
      role.metadata.name.toLowerCase().includes(normalizedSearch)
    );
  };
  const filteredSystemRoles = systemRoles.filter(matchesSearch);
  const filteredCustomRoles = customRoles.filter(matchesSearch);

  const deleteMutation = useMutation({
    mutationFn: async (role: Role) => {
      await apiClient.role.deleteRole({ name: role.metadata.name });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["roles"] }),
        queryClient.invalidateQueries({ queryKey: MY_UI_PERMISSIONS_QUERY_KEY }),
      ]);
      setDeleteTarget(null);
      setSelectedName(null);
      toast.success("角色已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveRoleApiError(error));
    },
  });

  const loading = rolesQuery.isPending || catalogQuery.isPending;
  const catalog = catalogQuery.data;
  const errorTitle = rolesQuery.isError ? "无法加载角色" : "无法加载权限目录";
  const errorMessage = rolesQuery.isError
    ? resolveRoleApiError(rolesQuery.error)
    : "无法获取权限目录，请稍后重试。";
  const retryLoad = () => {
    void rolesQuery.refetch();
    void catalogQuery.refetch();
  };

  return (
    <ConsolePage title="角色">
      {loading ? (
        <div className="grid flex-1 gap-4 xl:grid-cols-[280px_minmax(0,1fr)]">
          <Skeleton className="h-[420px] rounded-xl" />
          <Skeleton className="h-[520px] rounded-xl" />
        </div>
      ) : rolesQuery.isError || catalogQuery.isError || !catalog ? (
        <ConsoleSection className="flex min-h-64 flex-col items-center justify-center gap-4 p-8 text-center">
          <ShieldCheck className="size-8 text-muted-foreground" />
          <div className="space-y-1">
            <h2 className="text-sm font-semibold">{errorTitle}</h2>
            <p className="text-xs text-muted-foreground">{errorMessage}</p>
          </div>
          <Button variant="outline" size="sm" onClick={retryLoad}>
            <RefreshCw />
            重试
          </Button>
        </ConsoleSection>
      ) : (
        <div className="grid flex-1 items-start gap-4 xl:grid-cols-[280px_minmax(0,1fr)]">
          <ConsoleSection className="flex h-fit flex-col xl:sticky xl:top-20 xl:h-[calc(100svh-7rem)]">
            <div className="space-y-3 border-b p-4">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <h2 className="text-sm font-semibold">角色</h2>
                  <p className="text-xs text-muted-foreground">管理系统内置与自定义角色</p>
                </div>
                {canCreate ? (
                  <Button type="button" size="sm" onClick={() => setCreateOpen(true)}>
                    <Plus />
                    添加
                  </Button>
                ) : null}
              </div>
              <div className="relative">
                <Search className="pointer-events-none absolute start-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="搜索角色..."
                  className="ps-8"
                  autoComplete="off"
                  spellCheck={false}
                />
              </div>
            </div>

            <ScrollArea className="min-h-0 flex-1">
              <div className="space-y-4 p-2">
                <RoleSection
                  title="系统内置"
                  roles={filteredSystemRoles}
                  selectedName={selectedRole?.metadata.name}
                  onSelect={setSelectedName}
                  emptyText="暂无系统内置角色。"
                />
                <RoleSection
                  title="自定义"
                  roles={filteredCustomRoles}
                  selectedName={selectedRole?.metadata.name}
                  onSelect={setSelectedName}
                  emptyText={
                    roles.length === 0
                      ? "暂无角色，先添加一个角色。"
                      : normalizedSearch
                        ? "没有匹配的自定义角色。"
                        : "暂无自定义角色。"
                  }
                />
              </div>
            </ScrollArea>
          </ConsoleSection>

          {selectedRole ? (
            <ConsoleSection className="min-w-0 overflow-hidden">
              <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-lg font-semibold">{roleDisplayName(selectedRole)}</h2>
                    <Badge variant={isBuiltinRole(selectedRole) ? "secondary" : "outline"}>
                      {isBuiltinRole(selectedRole) ? "系统内置" : "自定义"}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    角色标识：<span className="font-mono">{selectedRole.metadata.name}</span>
                  </p>
                  <p className="text-sm text-muted-foreground">{roleDescription(selectedRole)}</p>
                </div>
                {isBuiltinRole(selectedRole) ? (
                  <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <Lock className="size-3.5" />
                    系统内置角色不可编辑
                  </span>
                ) : canDelete ? (
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    onClick={() => setDeleteTarget(selectedRole)}
                  >
                    <Trash2 />
                    删除角色
                  </Button>
                ) : null}
              </div>

              <RolePermissionEditor
                key={`${selectedRole.metadata.name}-${selectedRole.metadata.version ?? 0}`}
                role={selectedRole}
                catalog={catalog}
                readOnly={!canUpdate || isBuiltinRole(selectedRole)}
                builtin={isBuiltinRole(selectedRole)}
                allowedPermissions={myPermissionsQuery.data ?? []}
              />
            </ConsoleSection>
          ) : (
            <ConsoleSection>
              <ConsoleEmpty>暂无角色，先添加一个角色。</ConsoleEmpty>
            </ConsoleSection>
          )}
        </div>
      )}

      {createOpen ? (
        <CreateRoleDialog
          onClose={() => setCreateOpen(false)}
          onCreated={(name) => {
            setSearch("");
            setSelectedName(name);
          }}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除角色"
        desc={
          deleteTarget
            ? `确定删除「${roleDisplayName(deleteTarget)}」？使用该角色的用户组将失去对应权限，此操作无法恢复。`
            : ""
        }
        confirmText="删除"
        destructive
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </ConsolePage>
  );
}

type RoleSectionProps = {
  title: string;
  roles: Role[];
  selectedName?: string;
  onSelect: (name: string) => void;
  emptyText: string;
};

function RoleSection({ title, roles, selectedName, onSelect, emptyText }: RoleSectionProps) {
  return (
    <section className="space-y-1">
      <p className="px-2 text-xs font-medium text-muted-foreground">{title}</p>
      {roles.length === 0 ? (
        <p className="px-2 py-2 text-xs text-muted-foreground">{emptyText}</p>
      ) : (
        roles.map((role) => (
          <RoleListItem
            key={role.metadata.name}
            role={role}
            selected={role.metadata.name === selectedName}
            onSelect={onSelect}
          />
        ))
      )}
    </section>
  );
}

type RoleListItemProps = {
  role: Role;
  selected: boolean;
  onSelect: (name: string) => void;
};

function RoleListItem({ role, selected, onSelect }: RoleListItemProps) {
  return (
    <button
      type="button"
      onClick={() => onSelect(role.metadata.name)}
      title={roleDisplayName(role)}
      className={cn(
        "flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm transition-colors",
        selected ? "bg-primary text-primary-foreground" : "hover:bg-muted",
      )}
    >
      <ShieldCheck className="size-4 shrink-0 opacity-80" />
      <span className="min-w-0 flex-1 truncate">{roleDisplayName(role)}</span>
      {isBuiltinRole(role) ? <Lock className="size-3.5 shrink-0 opacity-60" /> : null}
    </button>
  );
}
