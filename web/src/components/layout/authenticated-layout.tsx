import type { ReactNode } from "react";

import { PageTransition } from "@/components/motion/page-transition";
import { SkipToMain } from "@/components/skip-to-main";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { TooltipProvider } from "@/components/ui/tooltip";
import { LayoutProvider } from "@/context/layout-provider";
import { SearchProvider } from "@/context/search-provider";
import { getCookie } from "@/lib/cookies";
import { cn } from "@/lib/utils";

import { AppSidebar } from "./app-sidebar";
import { ChatLayout } from "./chat/chat-layout";
import type { SidebarData } from "./types";

type AuthenticatedLayoutProps = Partial<SidebarData> & {
  onSignOut?: () => void;
  children: ReactNode;
};

const CHAT_CONVERSATION_PATH = "/chat";

export function AuthenticatedLayout({
  user,
  workspaces,
  activeWorkspaceId,
  navGroups,
  onSignOut,
  children,
}: AuthenticatedLayoutProps) {
  const defaultOpen = getCookie("sidebar_state") !== "false";
  const activeWorkspace =
    workspaces?.find((workspace) => workspace.id === activeWorkspaceId) ?? workspaces?.[0];
  const sidebarContent = activeWorkspace?.paths.includes(CHAT_CONVERSATION_PATH) ? (
    <ChatLayout navGroups={navGroups ?? []} />
  ) : undefined;

  return (
    <SearchProvider navGroups={navGroups}>
      <TooltipProvider delayDuration={0}>
        <LayoutProvider>
          <SidebarProvider defaultOpen={defaultOpen} className="h-svh overflow-hidden">
            <SkipToMain />
            <AppSidebar
              user={user}
              workspaces={workspaces}
              activeWorkspaceId={activeWorkspaceId}
              navGroups={navGroups}
              onSignOut={onSignOut}
              sidebarContent={sidebarContent}
            />
            <SidebarInset
              className={cn(
                "@container/content",
                "h-svh overflow-y-auto",
                "has-data-[layout=fixed]:h-svh",
                "peer-data-[variant=inset]:has-data-[layout=fixed]:h-[calc(100svh-(var(--spacing)*4))]",
              )}
            >
              <PageTransition>{children}</PageTransition>
            </SidebarInset>
          </SidebarProvider>
        </LayoutProvider>
      </TooltipProvider>
    </SearchProvider>
  );
}
