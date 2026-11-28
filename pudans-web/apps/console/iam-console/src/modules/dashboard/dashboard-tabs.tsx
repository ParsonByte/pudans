"use client";

import { usePathname, useRouter } from "next/navigation";

import { Segmented } from "antd";

import "./dashboard-tabs.modules.less";

const tabs = [
  { label: "概览", value: "overview" },
  { label: "治理监测", value: "governance-monitoring" },
  { label: "行为审计", value: "behavior-audit" },
];

export function DashboardTabs() {
  const pathname = usePathname();
  const router = useRouter();
  const active =
    tabs.find((tab) => pathname?.startsWith(`/dashboard/${tab.value}`))?.value ?? "overview";

  return (
    <div className="dashboard-tabs">
      <Segmented
        onChange={(value) => router.push(`/dashboard/${value}`)}
        options={tabs}
        value={active}
      />
    </div>
  );
}