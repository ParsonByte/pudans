import { createFileRoute } from "@tanstack/react-router";

import { UserGroupDetailView } from "@/modules/workspace/console/user-groups/detail";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/user-groups/$name")({
  component: UserGroupDetailRoute,
});

function UserGroupDetailRoute() {
  const { name } = Route.useParams();

  return <UserGroupDetailView name={name} />;
}
