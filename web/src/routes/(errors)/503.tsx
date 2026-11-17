import { createFileRoute } from "@tanstack/react-router";

import { MaintenanceError } from "@/modules/errors";

export const Route = createFileRoute("/(errors)/503")({
  component: MaintenanceError,
});
