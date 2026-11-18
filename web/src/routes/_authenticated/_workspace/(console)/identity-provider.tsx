import { createFileRoute } from "@tanstack/react-router";

import { IdentityProviderView } from "@/modules/workspace/console/identity-provider";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/identity-provider")({
  component: IdentityProviderView,
});
