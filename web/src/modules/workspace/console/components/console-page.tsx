import type { ComponentProps, ReactNode } from "react";

import { ConfigDrawer } from "@/components/config-drawer";
import { Header } from "@/components/layout/header";
import { Main } from "@/components/layout/main";
import { Search } from "@/components/search";
import { ThemeSwitch } from "@/components/theme-switch";
import { cn } from "@/lib/utils";

type ConsolePageProps = {
  title: string;
  children: ReactNode;
};

export function ConsolePage({ title, children }: ConsolePageProps) {
  return (
    <>
      <Header fixed>
        <div className="min-w-0">
          <h1 className="truncate text-lg font-semibold tracking-tight sm:text-xl">{title}</h1>
        </div>
        <div className="ms-auto flex items-center gap-2">
          <Search />
          <ThemeSwitch />
          <ConfigDrawer />
        </div>
      </Header>
      <Main className="space-y-6">{children}</Main>
    </>
  );
}

export function ConsoleSection({ className, ...props }: ComponentProps<"section">) {
  return (
    <section
      className={cn("rounded-xl border bg-card text-card-foreground", className)}
      {...props}
    />
  );
}

type ConsoleSectionHeaderProps = {
  title: string;
  description?: string;
  action?: ReactNode;
};

export function ConsoleSectionHeader({ title, description, action }: ConsoleSectionHeaderProps) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
      <div className="space-y-1">
        <h2 className="text-sm font-semibold">{title}</h2>
        {description ? (
          <p className="text-xs leading-5 text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {action}
    </div>
  );
}

export function ConsoleEmpty({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-36 items-center justify-center px-5 py-8 text-center text-sm text-muted-foreground">
      {children}
    </div>
  );
}
