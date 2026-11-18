import { createFileRoute } from "@tanstack/react-router";

import { ChatNewView } from "@/modules/workspace/index";

export const Route = createFileRoute("/_authenticated/_workspace/(index)/")({
  component: ChatNewView,
});
