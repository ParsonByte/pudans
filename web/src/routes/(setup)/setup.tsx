import { createFileRoute } from "@tanstack/react-router";

import { SetupView } from "@/modules/setup";

export const Route = createFileRoute("/(setup)/setup")({
  component: SetupView,
});
