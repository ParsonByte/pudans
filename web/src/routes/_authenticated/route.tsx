import { createFileRoute } from "@tanstack/react-router";

import { requireAuthenticatedContext } from "@/modules/auth/lib/authenticated-context";
import { AuthenticatedShell } from "@/modules/auth/lib/authenticated-shell";

export const Route = createFileRoute("/_authenticated")({
  beforeLoad: ({ location }) => requireAuthenticatedContext(location),
  component: AuthenticatedRoute,
});

function AuthenticatedRoute() {
  const { session, navigation } = Route.useRouteContext();

  return <AuthenticatedShell session={session} navigation={navigation} />;
}
