import type { DashboardOverview } from "@pudans-dev/api-client";
import { Activity, ShieldAlert, ShieldCheck, Users, UsersRound, Webhook } from "lucide-react";

import { ConsoleSection } from "@/modules/workspace/console/components/console-page";

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
        <ConsoleSection key={stat.label} className="flex items-start justify-between gap-4 p-5">
          <div className="space-y-2">
            <p className="text-xs font-medium text-muted-foreground">{stat.label}</p>
            <p className="text-2xl font-semibold tracking-tight tabular-nums">
              {stat.value.toLocaleString("zh-CN")}
            </p>
          </div>
          <span className="flex size-9 shrink-0 items-center justify-center bg-primary/10 text-primary">
            <stat.icon className="size-4" />
          </span>
        </ConsoleSection>
      ))}
    </div>
  );
}
