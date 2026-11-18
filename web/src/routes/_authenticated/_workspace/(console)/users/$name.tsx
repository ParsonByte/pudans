import { createFileRoute } from "@tanstack/react-router";

import { UserDetailView } from "@/modules/workspace/console/users/detail";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/users/$name")({
  component: UserDetailRoute,
});

function UserDetailRoute() {
  const { name } = Route.useParams();

  return <UserDetailView name={name} />;
}
