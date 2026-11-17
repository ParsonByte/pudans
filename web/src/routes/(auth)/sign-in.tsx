import { createFileRoute } from "@tanstack/react-router";

import { SignInView } from "@/modules/auth/sign-in";

type SignInSearch = {
  redirect?: string;
};

export const Route = createFileRoute("/(auth)/sign-in")({
  validateSearch: (search: Record<string, unknown>): SignInSearch => ({
    redirect: typeof search.redirect === "string" ? search.redirect : undefined,
  }),
  component: SignInRoute,
});

function SignInRoute() {
  const { redirect } = Route.useSearch();

  return <SignInView redirectTo={redirect} />;
}
