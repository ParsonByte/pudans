import type { ReactNode } from "react";

import { DashboardTabs } from "@/modules/dashboard/dashboard-tabs";

export default function DashboardLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <>
      <DashboardTabs />
      {children}
    </>
  );
}