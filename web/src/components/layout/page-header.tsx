import type { ReactNode } from "react";

import { ConfigDrawer } from "@/components/config-drawer";
import { Search } from "@/components/search";
import { ThemeSwitch } from "@/components/theme-switch";

import { Header } from "./header";

type PageHeaderProps = {
  title?: string;
  children?: ReactNode;
  actions?: ReactNode;
};

export function PageHeader({ title, children, actions }: PageHeaderProps) {
  return (
    <Header fixed>
      <div className="min-w-0">
        {children ?? (
          <h1 className="truncate text-lg font-semibold tracking-tight sm:text-xl">{title}</h1>
        )}
      </div>
      <div className="ms-auto flex items-center gap-2">
        {actions}
        <Search />
        <ThemeSwitch />
        <ConfigDrawer />
      </div>
    </Header>
  );
}
