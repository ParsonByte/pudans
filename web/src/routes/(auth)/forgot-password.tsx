import { createFileRoute } from "@tanstack/react-router";

import { ForgotPasswordView } from "@/modules/auth/forgot-password";

export const Route = createFileRoute("/(auth)/forgot-password")({
  component: ForgotPasswordView,
});
