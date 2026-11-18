import { createFileRoute } from "@tanstack/react-router";

import { SignInView } from "@/modules/auth/sign-in";

type SignInSearch = {
  redirect?: string;
  error?: string;
  detail?: string;
};

export const Route = createFileRoute("/(auth)/sign-in")({
  validateSearch: (search: Record<string, unknown>): SignInSearch => ({
    redirect: typeof search.redirect === "string" ? search.redirect : undefined,
    error: typeof search.error === "string" ? search.error : undefined,
    detail: typeof search.detail === "string" ? search.detail : undefined,
  }),
  component: SignInRoute,
});

function SignInRoute() {
  const { redirect, error, detail } = Route.useSearch();

  return <SignInView redirectTo={redirect} errorCode={error} errorDetail={detail} />;
}
