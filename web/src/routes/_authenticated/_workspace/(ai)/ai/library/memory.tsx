import { createFileRoute } from "@tanstack/react-router";

import { MemoryView } from "@/modules/workspace/ai/memory";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/memory")({
  component: MemoryView,
});
