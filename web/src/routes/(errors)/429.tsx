import { createFileRoute } from "@tanstack/react-router";

import { TooManyRequestsError } from "@/modules/errors";

export const Route = createFileRoute("/(errors)/429")({
  component: TooManyRequestsError,
});
