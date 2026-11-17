import { createFileRoute } from "@tanstack/react-router";

import { ConfigDrawer } from "@/components/config-drawer";
import { Header } from "@/components/layout/header";
import { Main } from "@/components/layout/main";
import { Search } from "@/components/search";
import { ThemeSwitch } from "@/components/theme-switch";

export const Route = createFileRoute("/_authenticated/")({
  component: DashboardPage,
});

function DashboardPage() {
  return (
    <>
      <Header>
        <h1 className="text-2xl font-bold tracking-tight">仪表盘</h1>
        <div className="ms-auto flex items-center gap-2">
          <Search />
          <ThemeSwitch />
          <ConfigDrawer />
        </div>
      </Header>
      <Main />
    </>
  );
}
