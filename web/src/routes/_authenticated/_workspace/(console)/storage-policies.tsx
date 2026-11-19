import { createFileRoute } from "@tanstack/react-router";

import { StoragePoliciesView } from "@/modules/workspace/console/storage-policies";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/storage-policies")({
  component: StoragePoliciesView,
});
