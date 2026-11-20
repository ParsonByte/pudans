import { createFileRoute } from "@tanstack/react-router";

import { AiSlashCommandsView } from "@/modules/workspace/ai/library/slash-commands";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/slash-commands")({
  component: AiSlashCommandsView,
});
