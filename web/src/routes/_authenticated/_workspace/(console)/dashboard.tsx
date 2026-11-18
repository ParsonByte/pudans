import { createFileRoute } from "@tanstack/react-router";

import { ConsoleOverviewView } from "@/modules/workspace/console/overview";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/dashboard")({
  component: ConsoleOverviewView,
});
