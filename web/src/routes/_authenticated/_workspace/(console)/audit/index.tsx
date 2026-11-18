import { createFileRoute } from "@tanstack/react-router";

import { AuditView } from "@/modules/workspace/console/audit";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/audit/")({
  component: AuditView,
});
