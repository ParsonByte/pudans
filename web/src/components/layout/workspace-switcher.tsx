import { useEffect } from "react";

import { ChevronsUpDown } from "lucide-react";
import { useRouter } from "next/navigation";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuShortcut,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";

import type { Workspace } from "./types";

const WORKSPACE_LOGO_STYLES = {
  trigger: {
    box: "aspect-square size-8",
    image: "bg-muted",
    fallback: "bg-sidebar-primary text-sidebar-primary-foreground",
    imageIcon: "size-6",
    fallbackIcon: "size-4",
  },
  item: {
    box: "size-6",
    image: "bg-muted",
    fallback: "border",
    imageIcon: "size-5",
    fallbackIcon: "size-4 shrink-0",
  },
} as const;

type WorkspaceLogoVariant = keyof typeof WORKSPACE_LOGO_STYLES;

type WorkspaceSwitcherProps = {
  workspaces: Workspace[];
  activeWorkspaceId?: string;
};

export function WorkspaceSwitcher({ workspaces, activeWorkspaceId }: WorkspaceSwitcherProps) {
  const router = useRouter();
  const { isMobile } = useSidebar();
  const activeWorkspace =
    workspaces.find((workspace) => workspace.id === activeWorkspaceId) ?? workspaces[0];

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((!event.metaKey && !event.ctrlKey) || event.altKey || event.shiftKey) {
        return;
      }

      const workspace = workspaces[Number(event.key) - 1];

      if (!workspace) {
        return;
      }

      event.preventDefault();
      router.push(workspace.homeUrl);
    };

    window.addEventListener("keydown", onKeyDown);

    return () => window.removeEventListener("keydown", onKeyDown);
  }, [router, workspaces]);

  if (!activeWorkspace) {
    return null;
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <WorkspaceLogo workspace={activeWorkspace} variant="trigger" />
              <div className="grid flex-1 text-start text-sm leading-tight">
                <span className="truncate font-semibold">{activeWorkspace.name}</span>
                <span className="truncate text-xs">{activeWorkspace.plan}</span>
              </div>
              <ChevronsUpDown className="ms-auto" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--radix-dropdown-menu-trigger-width) min-w-56"
            align="start"
            side={isMobile ? "bottom" : "right"}
            sideOffset={4}
          >
            <DropdownMenuLabel className="text-xs text-muted-foreground">工作区</DropdownMenuLabel>
            {workspaces.map((workspace, index) => (
              <DropdownMenuItem
                key={workspace.id}
                onSelect={() => router.push(workspace.homeUrl)}
                className="gap-2 p-2"
              >
                <WorkspaceLogo workspace={workspace} variant="item" />
                <span className="truncate font-medium">{workspace.name}</span>
                <DropdownMenuShortcut>⌘{index + 1}</DropdownMenuShortcut>
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}

function WorkspaceLogo({
  workspace,
  variant,
}: {
  workspace: Workspace;
  variant: WorkspaceLogoVariant;
}) {
  const styles = WORKSPACE_LOGO_STYLES[variant];
  return (
    <div
      className={cn(
        "flex items-center justify-center",
        styles.box,
        workspace.logoImage ? styles.image : styles.fallback,
      )}
    >
      <workspace.logo className={workspace.logoImage ? styles.imageIcon : styles.fallbackIcon} />
    </div>
  );
}
