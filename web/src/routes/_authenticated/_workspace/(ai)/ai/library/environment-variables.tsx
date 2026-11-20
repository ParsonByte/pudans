import { createFileRoute } from "@tanstack/react-router";

import { AiEnvironmentVariablesView } from "@/modules/workspace/ai/library/environment-variables";

export const Route = createFileRoute(
  "/_authenticated/_workspace/(ai)/ai/library/environment-variables",
)({
  component: AiEnvironmentVariablesView,
});
