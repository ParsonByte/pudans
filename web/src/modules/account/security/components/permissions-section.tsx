"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { useMyUiPermissions } from "@/modules/workspace/console/roles/lib/current-permissions";

const SELF_PERMISSIONS_QUERY_KEY = ["account-self-permissions"];

function BadgeList({ values, mono = false }: { values: string[]; mono?: boolean }) {
  return (
    <div className="flex flex-wrap gap-2">
      {values.map((value) => (
        <Badge
          key={value}
          variant="secondary"
          className={mono ? "font-mono normal-case" : undefined}
        >
          {value}
        </Badge>
      ))}
    </div>
  );
}

export function PermissionsSection() {
  const rolesQuery = useQuery({
    queryKey: SELF_PERMISSIONS_QUERY_KEY,
    queryFn: async () => (await apiClient.user.getMySelfPermissions()).data,
    retry: false,
  });
  const uiPermissionsQuery = useMyUiPermissions();
  const roles = rolesQuery.data?.roles ?? [];
  const uiPermissions = uiPermissionsQuery.data ?? [];
  const loading = rolesQuery.isPending || uiPermissionsQuery.isPending;
  const bothFailed = rolesQuery.isError && uiPermissionsQuery.isError;

  const refresh = () => {
    void rolesQuery.refetch();
    void uiPermissionsQuery.refetch();
  };

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="我的权限"
        description="当前账号的角色与界面权限，仅供查看。"
        action={
          <Button variant="ghost" size="sm" onClick={refresh}>
            刷新
          </Button>
        }
      />
      {loading ? (
        <ConsoleBlockSkeleton rows={2} className="grid gap-2 p-4" itemClassName="h-10" />
      ) : bothFailed ? (
        <ConsoleState
          variant="error"
          title="无法读取权限信息。"
          description="角色与界面权限暂时不可用，请重试。"
          onRetry={refresh}
        />
      ) : (
        <div className="grid gap-5 p-5 md:grid-cols-2">
          <div className="space-y-2">
            <p className="text-xs font-medium text-muted-foreground">角色</p>
            {rolesQuery.isError ? (
              <p className="text-xs text-destructive">无法读取角色。</p>
            ) : roles.length === 0 ? (
              <p className="text-xs text-muted-foreground">暂无已分配角色。</p>
            ) : (
              <BadgeList values={roles} />
            )}
          </div>
          <div className="space-y-2">
            <p className="text-xs font-medium text-muted-foreground">界面权限</p>
            {uiPermissionsQuery.isError ? (
              <p className="text-xs text-destructive">无法读取界面权限。</p>
            ) : uiPermissions.length === 0 ? (
              <p className="text-xs text-muted-foreground">暂无界面权限。</p>
            ) : (
              <BadgeList values={uiPermissions} mono />
            )}
          </div>
        </div>
      )}
    </ConsoleSection>
  );
}
