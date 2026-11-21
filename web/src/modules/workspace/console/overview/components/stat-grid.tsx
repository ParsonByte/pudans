import type { DashboardOverview } from "@pudans-dev/api-client";
import { Activity, ShieldAlert, ShieldCheck, Users, UsersRound, Webhook } from "lucide-react";

import { ConsoleStat } from "@/modules/workspace/console/components/console-stat";

type StatGridProps = {
  overview: DashboardOverview;
};

export function StatGrid({ overview }: StatGridProps) {
  const stats = [
    { label: "用户", value: overview.totalUsers ?? 0, icon: Users },
    { label: "角色", value: overview.totalRoles ?? 0, icon: ShieldCheck },
    { label: "用户组", value: overview.totalUserGroups ?? 0, icon: UsersRound },
    { label: "风险规则", value: overview.totalRiskRules ?? 0, icon: ShieldAlert },
    { label: "Webhook", value: overview.totalWebhooks ?? 0, icon: Webhook },
    { label: "今日操作", value: overview.todayOperations ?? 0, icon: Activity },
  ];

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {stats.map((stat) => (
        <ConsoleStat key={stat.label} {...stat} />
      ))}
    </div>
  );
}
