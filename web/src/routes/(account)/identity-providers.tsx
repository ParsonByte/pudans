import { createFileRoute } from "@tanstack/react-router";

import { AccountIdentitiesView } from "@/modules/account/identities";

export const Route = createFileRoute("/(account)/identity-providers")({
  component: AccountIdentitiesView,
});
