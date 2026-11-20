import { createFileRoute } from "@tanstack/react-router";

import { KnowledgeView } from "@/modules/workspace/ai/knowledge";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/knowledge-bases")({
  component: KnowledgeView,
});
