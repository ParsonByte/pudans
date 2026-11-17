import { createFileRoute } from "@tanstack/react-router";

import { UsersView } from "@/modules/workspace/console/users";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/users/")({
  component: UsersView,
});
