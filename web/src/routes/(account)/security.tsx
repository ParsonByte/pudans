import { createFileRoute } from "@tanstack/react-router";

import { AccountSecurityView } from "@/modules/account/security";

export const Route = createFileRoute("/(account)/security")({
  component: AccountSecurityView,
});
