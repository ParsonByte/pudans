import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from "@/components/ui/sidebar";
import { useLayout } from "@/context/layout-provider";

import { ChatLayout } from "./chat/chat-layout";
import { NavGroup } from "./nav-group";
import { NavUser } from "./nav-user";
import type { SidebarData } from "./types";
import { WorkspaceSwitcher } from "./workspace-switcher";

type AppSidebarProps = Partial<SidebarData> & {
  onSignOut?: () => void;
};

export function AppSidebar({
  user,
  workspaces,
  activeWorkspaceId,
  navGroups,
  onSignOut,
}: AppSidebarProps) {
  const { collapsible, variant } = useLayout();

  return (
    <Sidebar collapsible={collapsible} variant={variant}>
      {workspaces && workspaces.length > 0 ? (
        <SidebarHeader>
          <WorkspaceSwitcher workspaces={workspaces} activeWorkspaceId={activeWorkspaceId} />
        </SidebarHeader>
      ) : null}
      {activeWorkspaceId === "default" ? (
        <SidebarContent>
          <ChatLayout />
        </SidebarContent>
      ) : navGroups && navGroups.length > 0 ? (
        <SidebarContent>
          {navGroups.map((group) => (
            <NavGroup key={group.title} {...group} />
          ))}
        </SidebarContent>
      ) : null}
      {user ? (
        <SidebarFooter className="mt-auto">
          <NavUser user={user} onSignOut={onSignOut} />
        </SidebarFooter>
      ) : null}
      <SidebarRail />
    </Sidebar>
  );
}
