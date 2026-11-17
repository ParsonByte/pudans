import { createFileRoute } from "@tanstack/react-router";

import { UnauthorizedError } from "@/modules/errors";

export const Route = createFileRoute("/(errors)/401")({
  component: UnauthorizedError,
});
