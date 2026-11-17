import { axiosInstance } from "@pudans-dev/api-client";
import { createFileRoute, redirect, useLocation } from "@tanstack/react-router";
import { toast } from "sonner";

import { AuthenticatedLayout } from "@/components/layout/authenticated-layout";
import type { User } from "@/components/layout/types";
import { getLoginSession } from "@/modules/auth/lib/session";
import {
  buildWorkspaceNavGroups,
  buildWorkspaces,
  resolveActiveWorkspace,
} from "@/modules/workspace/lib/workspaces";

export const Route = createFileRoute("/_authenticated")({
  beforeLoad: async ({ location }) => {
    const session = await getLoginSession().catch(() => null);

    if (!session) {
      toast.error("无法获取登录状态，请稍后重试。");
      throw redirect({ to: "/sign-in", replace: true });
    }

    if (!session.authenticated) {
      throw redirect({
        to: "/sign-in",
        search: { redirect: location.href },
        replace: true,
      });
    }

    if (session.consoleAccessDisallowed) {
      throw redirect({ to: "/403", replace: true });
    }

    return { session };
  },
  component: AuthenticatedRoute,
});

function AuthenticatedRoute() {
  const { session } = Route.useRouteContext();
  const pathname = useLocation({ select: (location) => location.pathname });
  const workspaces = buildWorkspaces(session.uiPermissions);
  const activeWorkspace = resolveActiveWorkspace(workspaces, pathname);
  const navGroups = activeWorkspace
    ? buildWorkspaceNavGroups(activeWorkspace.id, session.uiPermissions)
    : [];
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
