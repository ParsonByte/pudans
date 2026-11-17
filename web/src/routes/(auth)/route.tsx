import { createFileRoute, useLocation } from "@tanstack/react-router";

import { AuthLayout } from "@/modules/auth/auth-layout";
import { guardSetupFlow } from "@/modules/setup/lib/setup-guard";

export const Route = createFileRoute("/(auth)")({
  beforeLoad: guardSetupFlow,
  component: AuthRouteLayout,
});

function AuthRouteLayout() {
  const variant = useLocation({
    select: (location) => (location.pathname.startsWith("/legal") ? "legal" : "auth"),
  });

  return <AuthLayout variant={variant} />;
}
