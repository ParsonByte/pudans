import { Outlet } from "@tanstack/react-router";

import { SkipToMain } from "@/components/skip-to-main";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { TooltipProvider } from "@/components/ui/tooltip";
import { LayoutProvider } from "@/context/layout-provider";
import { SearchProvider } from "@/context/search-provider";
import { getCookie } from "@/lib/cookies";
import { cn } from "@/lib/utils";

import { AppSidebar } from "./app-sidebar";
import type { SidebarData } from "./types";

type AuthenticatedLayoutProps = Partial<SidebarData> & {
  onSignOut?: () => void;
};

export function AuthenticatedLayout({
  user,
  workspaces,
  navGroups,
  onSignOut,
}: AuthenticatedLayoutProps) {
  const defaultOpen = getCookie("sidebar_state") === "true";

  return (
    <SearchProvider navGroups={navGroups}>
      <TooltipProvider delayDuration={0}>
        <LayoutProvider>
          <SidebarProvider defaultOpen={defaultOpen}>
            <SkipToMain />
            <AppSidebar
              user={user}
              workspaces={workspaces}
              navGroups={navGroups}
              onSignOut={onSignOut}
            />
            <SidebarInset
              className={cn(
                "@container/content",
                "has-data-[layout=fixed]:h-svh",
                "peer-data-[variant=inset]:has-data-[layout=fixed]:h-[calc(100svh-(var(--spacing)*4))]",
              )}
            >
              <Outlet />
            </SidebarInset>
          </SidebarProvider>
        </LayoutProvider>
      </TooltipProvider>
    </SearchProvider>
  );
}
