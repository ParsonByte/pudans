import { createFileRoute } from "@tanstack/react-router";

import { TooManyRequestsError } from "@/modules/errors";

type RateLimitSearch = {
  detail?: string;
};

export const Route = createFileRoute("/(errors)/429")({
  validateSearch: (search: Record<string, unknown>): RateLimitSearch => ({
    detail: typeof search.detail === "string" ? search.detail : undefined,
  }),
  component: TooManyRequestsRoute,
});

function TooManyRequestsRoute() {
  const { detail } = Route.useSearch();

  return <TooManyRequestsError description={detail} />;
}
