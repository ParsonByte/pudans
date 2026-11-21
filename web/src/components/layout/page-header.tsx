import type { ReactNode } from "react";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";

import { ConfigDrawer } from "@/components/config-drawer";
import { Search } from "@/components/search";
import { ThemeSwitch } from "@/components/theme-switch";
import { Button } from "@/components/ui/button";

import { Header } from "./header";

type PageHeaderProps = {
  title?: string;
  backHref?: string;
  children?: ReactNode;
  actions?: ReactNode;
  utilities?: ReactNode;
  hideSearch?: boolean;
};

export function PageHeader({
  title,
  backHref,
  children,
  actions,
  utilities,
  hideSearch = false,
}: PageHeaderProps) {
  return (
    <Header fixed>
      <div className="flex min-w-0 items-center gap-2">
        {backHref ? (
          <Button variant="ghost" size="icon-sm" asChild>
            <Link href={backHref} aria-label="返回">
              <ArrowLeft />
            </Link>
          </Button>
        ) : null}
        {children ?? (
          <h1 className="truncate text-lg font-semibold tracking-tight sm:text-xl">{title}</h1>
        )}
      </div>
      <div className="ms-auto flex items-center gap-2">
        {actions}
        {utilities}
        {hideSearch ? null : <Search />}
        <ThemeSwitch />
        <ConfigDrawer />
      </div>
    </Header>
  );
}
