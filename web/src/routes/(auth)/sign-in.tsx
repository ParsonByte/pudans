import { createFileRoute } from "@tanstack/react-router";

import { SignInView } from "@/modules/auth/sign-in";

export const Route = createFileRoute("/(auth)/sign-in")({
  component: SignInView,
});
