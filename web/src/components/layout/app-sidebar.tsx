import type { ReactNode } from "react";

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from "@/components/ui/sidebar";
import { useLayout } from "@/context/layout-provider";

import { NavGroup } from "./nav-group";
import { NavUser } from "./nav-user";
import type { SidebarData } from "./types";
import { WorkspaceSwitcher } from "./workspace-switcher";

type AppSidebarProps = Partial<SidebarData> & {
  onSignOut?: () => void;
  sidebarContent?: ReactNode;
};

export function AppSidebar({
  user,
  workspaces,
  activeWorkspaceId,
  navGroups,
  onSignOut,
  sidebarContent,
}: AppSidebarProps) {
  const { collapsible, variant } = useLayout();
  const content =
    sidebarContent ??
    (navGroups && navGroups.length > 0
      ? navGroups.map((group) => <NavGroup key={group.title} {...group} />)
      : null);

  return (
    <Sidebar collapsible={collapsible} variant={variant}>
      {workspaces && workspaces.length > 0 ? (
        <SidebarHeader>
          <WorkspaceSwitcher workspaces={workspaces} activeWorkspaceId={activeWorkspaceId} />
        </SidebarHeader>
      ) : null}
      {content ? <SidebarContent>{content}</SidebarContent> : null}
      {user ? (
        <SidebarFooter className="mt-auto">
          <NavUser user={user} onSignOut={onSignOut} />
        </SidebarFooter>
      ) : null}
      <SidebarRail />
    </Sidebar>
  );
}
