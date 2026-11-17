import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from "@/components/ui/sidebar";

import { NavGroup } from "./nav-group";
import { NavUser } from "./nav-user";
import type { SidebarData } from "./types";
import { WorkspaceSwitcher } from "./workspace-switcher";

type AppSidebarProps = Partial<SidebarData> & {
  collapsible?: "offcanvas" | "icon" | "none";
  variant?: "sidebar" | "floating" | "inset";
};

export function AppSidebar({
  collapsible = "icon",
  variant = "inset",
  user,
  workspaces,
  navGroups,
}: AppSidebarProps) {
  return (
    <Sidebar collapsible={collapsible} variant={variant}>
      {workspaces && workspaces.length > 0 ? (
        <SidebarHeader>
          <WorkspaceSwitcher workspaces={workspaces} />
        </SidebarHeader>
      ) : null}
      {navGroups && navGroups.length > 0 ? (
        <SidebarContent>
          {navGroups.map((group) => (
            <NavGroup key={group.title} {...group} />
          ))}
        </SidebarContent>
      ) : null}
      {user ? (
        <SidebarFooter>
          <NavUser user={user} />
        </SidebarFooter>
      ) : null}
      <SidebarRail />
    </Sidebar>
  );
}
