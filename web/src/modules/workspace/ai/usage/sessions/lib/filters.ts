import type { UsageSessionQuery } from "../../lib/api";
import { type UsageDateRangeValue, usageDateRange } from "../../lib/model";
import type { UsageSearchPatch, UsageUrlSearch } from "../../lib/url-state";
import { usageTextParam } from "../../lib/url-state";

export type UsageSessionFilterForm = {
  range: UsageDateRangeValue;
  agent: string;
  q: string;
  provider: string;
  model: string;
  status: string;
  routeGroup: string;
};

export function createUsageSessionFilterForm(): UsageSessionFilterForm {
  return {
    range: usageDateRange("7d"),
    agent: "",
    q: "",
    provider: "",
    model: "",
    status: "",
    routeGroup: "",
  };
}

export function sessionFiltersFromSearch(
  search: UsageUrlSearch,
): Omit<UsageSessionFilterForm, "range"> {
  return {
    agent: usageTextParam(search, "agent"),
    q: usageTextParam(search, "q"),
    provider: usageTextParam(search, "provider"),
    model: usageTextParam(search, "model"),
    status: usageTextParam(search, "status"),
    routeGroup: usageTextParam(search, "routeGroup"),
  };
}

export function sessionFilterSearch(form: Omit<UsageSessionFilterForm, "range">): UsageSearchPatch {
  return {
    agent: form.agent || undefined,
    q: form.q || undefined,
    provider: form.provider || undefined,
    model: form.model || undefined,
    status: form.status || undefined,
    routeGroup: form.routeGroup || undefined,
  };
}

function trimmed(value: string): string | undefined {
  const result = value.trim();
  return result.length > 0 ? result : undefined;
}

export function toUsageSessionQuery(form: UsageSessionFilterForm): UsageSessionQuery {
  return {
    from: form.range.from,
    to: form.range.to,
    agent: trimmed(form.agent),
    q: trimmed(form.q),
    provider: trimmed(form.provider),
    model: trimmed(form.model),
    status: trimmed(form.status),
    routeGroup: trimmed(form.routeGroup),
  };
}

export function isSessionFiltered(form: UsageSessionFilterForm): boolean {
  return (
    form.range.preset !== "7d" ||
    [form.agent, form.q, form.provider, form.model, form.status, form.routeGroup].some(
      (value) => value.trim().length > 0,
    )
  );
}
