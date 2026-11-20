import type { UsageRequestQuery } from "../../lib/api";
import { type UsageDateRangeValue, usageDateRange } from "../../lib/model";
import type { UsageSearchPatch, UsageUrlSearch } from "../../lib/url-state";
import { usageTextParam } from "../../lib/url-state";

export type UsageRequestFilterForm = {
  range: UsageDateRangeValue;
  status: string;
  provider: string;
  model: string;
  account: string;
  agent: string;
  mode: string;
  routeGroup: string;
  q: string;
  purpose: string;
  via: string;
  callerKey: string;
  retried: string;
};

export type UsageRequestFilters = Omit<UsageRequestFilterForm, "range">;

export function createUsageRequestFilterForm(): UsageRequestFilterForm {
  return {
    range: usageDateRange("7d"),
    status: "",
    provider: "",
    model: "",
    account: "",
    agent: "",
    mode: "",
    routeGroup: "",
    q: "",
    purpose: "",
    via: "",
    callerKey: "",
    retried: "",
  };
}

export function requestFiltersFromSearch(search: UsageUrlSearch): UsageRequestFilters {
  return {
    status: usageTextParam(search, "status"),
    provider: usageTextParam(search, "provider"),
    model: usageTextParam(search, "model"),
    account: usageTextParam(search, "account"),
    agent: usageTextParam(search, "agent"),
    mode: usageTextParam(search, "mode"),
    routeGroup: usageTextParam(search, "routeGroup"),
    q: usageTextParam(search, "q"),
    purpose: usageTextParam(search, "purpose"),
    via: usageTextParam(search, "via"),
    callerKey: usageTextParam(search, "callerKey"),
    retried: usageTextParam(search, "retried"),
  };
}

export function requestFilterSearch(filters: UsageRequestFilters): UsageSearchPatch {
  return {
    status: filters.status || undefined,
    provider: filters.provider || undefined,
    model: filters.model || undefined,
    account: filters.account || undefined,
    agent: filters.agent || undefined,
    mode: filters.mode || undefined,
    routeGroup: filters.routeGroup || undefined,
    q: filters.q || undefined,
    purpose: filters.purpose || undefined,
    via: filters.via || undefined,
    callerKey: filters.callerKey || undefined,
    retried: filters.retried || undefined,
  };
}

function trimmed(value: string): string | undefined {
  const result = value.trim();
  return result.length > 0 ? result : undefined;
}

export function toUsageRequestQuery(form: UsageRequestFilterForm): UsageRequestQuery {
  return {
    from: form.range.from,
    to: form.range.to,
    status: trimmed(form.status),
    provider: trimmed(form.provider),
    model: trimmed(form.model),
    account: trimmed(form.account),
    agent: trimmed(form.agent),
    mode: trimmed(form.mode),
    routeGroup: trimmed(form.routeGroup),
    q: trimmed(form.q),
    purpose: trimmed(form.purpose),
    via: trimmed(form.via),
    callerKey: trimmed(form.callerKey),
    retried: trimmed(form.retried),
  };
}

export function hasAdvancedRequestFilters(form: UsageRequestFilterForm): boolean {
  return [
    form.provider,
    form.model,
    form.account,
    form.agent,
    form.routeGroup,
    form.purpose,
    form.via,
    form.callerKey,
  ].some((value) => value.trim().length > 0);
}

export function isRequestFiltered(form: UsageRequestFilterForm): boolean {
  return (
    form.range.preset !== "7d" ||
    form.status !== "" ||
    form.mode !== "" ||
    form.q.trim() !== "" ||
    form.retried !== "" ||
    hasAdvancedRequestFilters(form)
  );
}
