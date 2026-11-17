import { createFileRoute } from "@tanstack/react-router";

import { InternalServerError } from "@/modules/errors";

export const Route = createFileRoute("/(errors)/500")({
  component: InternalServerError,
});
