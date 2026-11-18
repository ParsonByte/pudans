import { createFileRoute } from "@tanstack/react-router";

import { UserGroupsView } from "@/modules/workspace/console/user-groups";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/user-groups/")({
  component: UserGroupsView,
});
