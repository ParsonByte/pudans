import { createFileRoute } from "@tanstack/react-router";

import { AccountProfileView } from "@/modules/account/profile";

export const Route = createFileRoute("/(account)/profile")({
  component: AccountProfileView,
});
