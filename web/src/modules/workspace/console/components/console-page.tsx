import type { ComponentProps, ReactNode } from "react";

import { Main } from "@/components/layout/main";
import { PageHeader } from "@/components/layout/page-header";
import { cn } from "@/lib/utils";

type ConsolePageProps = {
  title: string;
  children: ReactNode;
};

export function ConsolePage({ title, children }: ConsolePageProps) {
  return (
    <>
      <PageHeader title={title} />
      <Main className="flex flex-1 flex-col gap-6">{children}</Main>
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
  border?: boolean;
};

export function ConsoleSectionHeader({
  title,
  description,
  action,
  border = true,
}: ConsoleSectionHeaderProps) {
  return (
    <div
      className={cn(
        "flex flex-wrap items-start justify-between gap-3 px-5 py-4",
        border && "border-b",
      )}
    >
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
