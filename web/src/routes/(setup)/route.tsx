import { createFileRoute } from "@tanstack/react-router";

import { AuthLayout } from "@/modules/auth/auth-layout";
import { guardSetupFlow } from "@/modules/setup/lib/setup-guard";

export const Route = createFileRoute("/(setup)")({
  beforeLoad: guardSetupFlow,
  component: SetupLayout,
});

function SetupLayout() {
  return <AuthLayout variant="setup" />;
}
