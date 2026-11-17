import type { RoleUserCount } from "@pudans-dev/api-client";

import { Badge } from "@/components/ui/badge";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

type RoleDistributionProps = {
  roles: RoleUserCount[];
};

export function RoleDistribution({ roles }: RoleDistributionProps) {
  const maxUsers = Math.max(1, ...roles.map((role) => role.users ?? 0));

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="角色分布"
        description="当前各角色的用户数量"
        action={<Badge variant="secondary">{roles.length} 个角色</Badge>}
      />
      {roles.length === 0 ? (
        <ConsoleEmpty>暂无角色分布数据。</ConsoleEmpty>
      ) : (
        <ul className="divide-y">
          {roles.map((role, index) => {
            const users = role.users ?? 0;
            const width = Math.min(100, (users / maxUsers) * 100);

            return (
              <li
                key={`${role.role ?? role.displayName ?? "role"}-${index}`}
                className="space-y-2 px-5 py-3"
              >
                <div className="flex items-center justify-between gap-3 text-sm">
                  <span className="truncate font-medium">{role.displayName || role.role}</span>
                  <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                    {users.toLocaleString("zh-CN")}
                  </span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full bg-primary" style={{ width: `${width}%` }} />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </ConsoleSection>
  );
}
