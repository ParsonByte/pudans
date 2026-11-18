import type { SessionView } from "@pudans-dev/api-client";
import { redirect } from "@tanstack/react-router";
import { toast } from "sonner";

import { getLoginSession } from "@/modules/auth/lib/session";
import { type Navigation, fetchNavigation } from "@/modules/workspace/lib/permission-api";

export type AuthenticatedContext = {
  session: SessionView;
  navigation: Navigation;
};

export async function requireAuthenticatedContext(location: {
  href: string;
}): Promise<AuthenticatedContext> {
  const [session, navigation] = await Promise.all([
    getLoginSession().catch(() => null),
    fetchNavigation().catch(() => null),
  ]);

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

  if (!navigation) {
    toast.error("无法获取工作区导航，请稍后重试。");
    throw redirect({ to: "/500", replace: true });
  }

  return { session, navigation };
}
