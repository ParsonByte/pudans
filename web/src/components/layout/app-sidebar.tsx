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

type AppSidebarProps = Partial<SidebarData>;

export function AppSidebar({ user, workspaces, navGroups }: AppSidebarProps) {
  const { collapsible, variant } = useLayout();

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
