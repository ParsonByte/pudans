import { createFileRoute } from "@tanstack/react-router";

import { AuditLogsView } from "@/modules/workspace/console/audit/logs";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/audit/logs")({
  component: AuditLogsView,
});
