import { createFileRoute } from "@tanstack/react-router";

import { AiProvidersView } from "@/modules/workspace/ai/providers";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/models/providers/")({
  component: AiProvidersView,
});
