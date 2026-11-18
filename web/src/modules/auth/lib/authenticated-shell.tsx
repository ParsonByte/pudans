import { axiosInstance } from "@pudans-dev/api-client";
import { useLocation } from "@tanstack/react-router";
import { toast } from "sonner";

import { AuthenticatedLayout } from "@/components/layout/authenticated-layout";
import type { User } from "@/components/layout/types";
import type { AuthenticatedContext } from "@/modules/auth/lib/authenticated-context";
import {
  buildWorkspaceNavGroups,
  buildWorkspaces,
  resolveActiveWorkspace,
} from "@/modules/workspace/lib/workspaces";

export function AuthenticatedShell({ session, navigation }: AuthenticatedContext) {
  const pathname = useLocation({ select: (location) => location.pathname });
  const workspaces = buildWorkspaces(navigation);
  const activeWorkspace = resolveActiveWorkspace(workspaces, pathname);
  const navGroups = activeWorkspace ? buildWorkspaceNavGroups(navigation, activeWorkspace.id) : [];
  const user: User = {
    name: session.displayName || session.username || "用户",
    email: session.username ?? "",
    avatar: session.avatar ?? "",
  };

  const handleSignOut = async () => {
    try {
      await axiosInstance.post("/logout");
      window.location.assign("/sign-in");
    } catch {
      toast.error("退出登录失败，请稍后重试。");
    }
  };

  return (
    <AuthenticatedLayout
      user={user}
      workspaces={workspaces}
      activeWorkspaceId={activeWorkspace?.id}
      navGroups={navGroups}
      onSignOut={handleSignOut}
    />
  );
}
