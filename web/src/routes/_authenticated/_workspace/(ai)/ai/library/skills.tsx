import { createFileRoute } from "@tanstack/react-router";

import { AiSkillsView } from "@/modules/workspace/ai/library/skills";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/skills")({
  component: AiSkillsView,
});
