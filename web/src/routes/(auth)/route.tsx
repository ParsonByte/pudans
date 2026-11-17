import { createFileRoute, redirect, useLocation } from "@tanstack/react-router";

import { AuthLayout } from "@/modules/auth/auth-layout";
import { getLoginSession } from "@/modules/auth/lib/session";
import { guardSetupFlow } from "@/modules/setup/lib/setup-guard";

export const Route = createFileRoute("/(auth)")({
  beforeLoad: async (context) => {
    await guardSetupFlow(context);

    if (context.location.pathname !== "/sign-in") {
      return;
    }

    const session = await getLoginSession().catch(() => null);

    if (!session) {
      return;
    }

    if (session.authenticated && session.consoleAccessDisallowed) {
      throw redirect({ to: "/403", replace: true });
    }

    if (session.authenticated) {
      throw redirect({ to: "/", replace: true });
    }
  },
  component: AuthRouteLayout,
});

function AuthRouteLayout() {
  const variant = useLocation({
    select: (location) => (location.pathname.startsWith("/legal") ? "legal" : "auth"),
  });

  return <AuthLayout variant={variant} />;
}
