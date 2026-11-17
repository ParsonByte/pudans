import { createFileRoute } from "@tanstack/react-router";

import { AuthLayout } from "@/modules/auth/auth-layout";

export const Route = createFileRoute("/(auth)")({
  component: AuthLayout,
});
