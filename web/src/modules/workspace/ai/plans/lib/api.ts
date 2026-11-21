import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { resolveApiError } from "@/lib/api-error";

import {
  AI_API_PREFIX,
  AI_API_VERSION,
  type JsonPatchOperation,
  listAll,
} from "../../providers/lib/api";
import type { PlanUsage, PlanVoucherBalance, PlanVoucherKind } from "../../usage/lib/api";

const AI_PLAN_KIND = "AiPlan";

const USER_API_PATH = "/apis/api.pudans.tolim.cn/v1alpha1/users";

const BULK_BINDING_PATH = `${AI_API_PREFIX}/aiplanbindings/-/bulk`;

const RESOLVE_BINDING_PATH = `${AI_API_PREFIX}/aiplanbindings/-/resolve`;

const PLAN_VOUCHER_GRANT_PATH = `${AI_API_PREFIX}/aiplan/-/vouchers/grant`;

export type PlanSubjectKind = "User" | "UserGroup";

export type PlanDayOfWeek =
  "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export type PlanVoucherPeriod = "WEEKLY" | "MONTHLY";

export type PlanFeature = "CHAT" | "KNOWLEDGE" | "AGENT" | "SUBAGENT";

export type PlanFallbackAction = "DENY" | "DOWNGRADE";

export type AiPlanSpec = {
  displayName: string;
  description?: string;
  enabled?: boolean;
  quota: {
    fiveHour: { limit: number };
    weekly: { limit: number; resetDayOfWeek?: PlanDayOfWeek; resetHour?: number };
  };
  toleranceUnits?: number;
  vouchers?: {
    fiveHour?: number;
    weekly?: number;
    period?: PlanVoucherPeriod;
  };
  rateLimit?: {
    maxConcurrency?: number;
    maxRpm?: number;
  };
  fallback?: {
    onFiveHourExhausted?: PlanFallbackAction;
    downgradeRouteGroup?: string;
  };
  access?: {
    routeGroups?: string[];
    models?: string[];
    features?: PlanFeature[];
  };
  [key: string]: unknown;
};

export type AiPlanItem = {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    version?: number;
    creationTimestamp?: string;
    [key: string]: unknown;
  };
  spec: AiPlanSpec;
};

export type PlanSubject = {
  kind: PlanSubjectKind;
  name: string;
};

type AiPlanBindingSpec = {
  plan: string;
  subject: PlanSubject;
  priority?: number;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  [key: string]: unknown;
};

export type AiPlanBindingItem = {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    version?: number;
    creationTimestamp?: string;
    [key: string]: unknown;
  };
  spec: AiPlanBindingSpec;
};

export type BulkBindRequest = {
  plan: string;
  subjects: PlanSubject[];
  priority: number;
  replaceExisting: boolean;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
};

type BindingSummary = {
  name?: string;
  plan?: string;
  kind?: PlanSubjectKind;
  subject?: string;
  priority?: number;
};

export type BulkBindResult = {
  plan?: string;
  bindings?: BindingSummary[];
};

export type PlanResolution = {
  planName: string | null;
  displayName: string | null;
  matchedBy: string | null;
};

type RawPlanResolution = {
  plan?:
    | {
        name?: string | null;
        planName?: string | null;
        displayName?: string | null;
        matchedBy?: string | null;
      }
    | string
    | null;
  planName?: string | null;
  displayName?: string | null;
  matchedBy?: string | null;
  binding?: {
    name?: string | null;
    plan?: string | null;
    spec?: { plan?: string | null; subject?: PlanSubject | null } | null;
  } | null;
};

export type GrantVouchersRequest = {
  kind: PlanVoucherKind;
  count: number;
  userIds?: string[];
  groupNames?: string[];
};

export type GrantVouchersResult = {
  kind?: PlanVoucherKind | null;
  count?: number;
  userIds?: string[];
};

type PlanVoucherBalanceView = {
  fiveHour: PlanVoucherBalance;
  weekly: PlanVoucherBalance;
};

export type PlanVoucherHistoryItem = {
  id: number;
  requestId: string;
  kind: PlanVoucherKind | string;
  targetWindowKey: string;
  windowKeyAfter: string;
  usedAt: string;
};

type PlanVoucherHistoryQuery = {
  limit: number;
  beforeId?: number;
};

type PlanBindingCreateValues = {
  name: string;
  plan: string;
  subject: PlanSubject;
  priority: number;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
};

export function fetchAllPlans(): Promise<AiPlanItem[]> {
  return listAll<AiPlanItem>("/aiplans");
}

export async function createPlan(plan: AiPlanItem): Promise<AiPlanItem> {
  const response = await axiosInstance.post<AiPlanItem>(`${AI_API_PREFIX}/aiplans`, plan);
  return response.data;
}

export async function fetchPlan(name: string): Promise<AiPlanItem> {
  const response = await axiosInstance.get<AiPlanItem>(
    `${AI_API_PREFIX}/aiplans/${encodeURIComponent(name)}`,
  );
  return response.data;
}

export async function patchPlan(
  name: string,
  operations: JsonPatchOperation[],
): Promise<AiPlanItem> {
  const response = await axiosInstance.patch<AiPlanItem>(
    `${AI_API_PREFIX}/aiplans/${encodeURIComponent(name)}`,
    operations,
    { headers: { "Content-Type": "application/json-patch+json" } },
  );
  return response.data;
}

export async function updatePlan(plan: AiPlanItem): Promise<AiPlanItem> {
  const response = await axiosInstance.put<AiPlanItem>(
    `${AI_API_PREFIX}/aiplans/${encodeURIComponent(plan.metadata.name)}`,
    plan,
  );
  return response.data;
}

export async function deletePlan(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/aiplans/${encodeURIComponent(name)}`);
}

export function fetchAllPlanBindings(): Promise<AiPlanBindingItem[]> {
  return listAll<AiPlanBindingItem>("/aiplanbindings");
}

export async function fetchPlanBinding(name: string): Promise<AiPlanBindingItem> {
  const response = await axiosInstance.get<AiPlanBindingItem>(
    `${AI_API_PREFIX}/aiplanbindings/${encodeURIComponent(name)}`,
  );
  return response.data;
}

export async function createPlanBinding(item: AiPlanBindingItem): Promise<AiPlanBindingItem> {
  const response = await axiosInstance.post<AiPlanBindingItem>(
    `${AI_API_PREFIX}/aiplanbindings`,
    item,
  );
  return response.data;
}

export async function updatePlanBinding(item: AiPlanBindingItem): Promise<AiPlanBindingItem> {
  const response = await axiosInstance.put<AiPlanBindingItem>(
    `${AI_API_PREFIX}/aiplanbindings/${encodeURIComponent(item.metadata.name)}`,
    item,
  );
  return response.data;
}

export async function patchPlanBinding(
  name: string,
  operations: JsonPatchOperation[],
): Promise<AiPlanBindingItem> {
  const response = await axiosInstance.patch<AiPlanBindingItem>(
    `${AI_API_PREFIX}/aiplanbindings/${encodeURIComponent(name)}`,
    operations,
    { headers: { "Content-Type": "application/json-patch+json" } },
  );
  return response.data;
}

export async function deletePlanBinding(name: string): Promise<void> {
  await axiosInstance.delete(`${AI_API_PREFIX}/aiplanbindings/${encodeURIComponent(name)}`);
}

export async function bulkBindPlan(request: BulkBindRequest): Promise<BulkBindResult> {
  const response = await axiosInstance.post<BulkBindResult>(BULK_BINDING_PATH, request);
  return response.data;
}

export async function resolvePlanBinding(uid: string): Promise<PlanResolution> {
  const response = await axiosInstance.get<RawPlanResolution>(
    `${RESOLVE_BINDING_PATH}/${encodeURIComponent(uid)}`,
  );
  return normalizeResolution(response.data);
}

export async function grantPlanVouchers(
  request: GrantVouchersRequest,
): Promise<GrantVouchersResult> {
  const response = await axiosInstance.post<GrantVouchersResult>(PLAN_VOUCHER_GRANT_PATH, request);
  return response.data;
}

export async function fetchPlanUsageByUser(uid: string): Promise<PlanUsage> {
  const response = await axiosInstance.get<PlanUsage>(
    `${AI_API_PREFIX}/aiplan/-/usage/${encodeURIComponent(uid)}`,
  );
  return response.data;
}

export async function fetchPlanVoucherBalance(): Promise<PlanVoucherBalanceView> {
  const response = await axiosInstance.get<PlanVoucherBalanceView>(
    `${AI_API_PREFIX}/aiplan/-/vouchers`,
  );
  return response.data;
}

export async function fetchPlanVoucherHistory(
  query: PlanVoucherHistoryQuery,
): Promise<PlanVoucherHistoryItem[]> {
  const response = await axiosInstance.get<unknown>(
    `${AI_API_PREFIX}/aiplan/-/vouchers/-/history`,
    { params: query },
  );
  return Array.isArray(response.data) ? (response.data as PlanVoucherHistoryItem[]) : [];
}

async function fetchUserUid(username: string): Promise<string> {
  const response = await axiosInstance.get<{ spec?: { uid?: string | null } }>(
    `${USER_API_PATH}/${encodeURIComponent(username)}`,
  );
  const uid = response.data.spec?.uid;
  if (!uid) {
    throw new Error(`用户 ${username} 缺少 uid，无法绑定套餐。`);
  }
  return uid;
}

export async function resolvePlanSubjects(
  kind: PlanSubjectKind,
  names: readonly string[],
): Promise<PlanSubject[]> {
  if (kind === "UserGroup") {
    return names.map((name) => ({ kind, name }));
  }
  return Promise.all(names.map(async (name) => ({ kind, name: await fetchUserUid(name) })));
}

function normalizeResolution(raw: RawPlanResolution): PlanResolution {
  const nested = typeof raw.plan === "object" && raw.plan !== null ? raw.plan : null;
  const flat = typeof raw.plan === "string" ? raw.plan : null;
  return {
    planName:
      nested?.name ??
      nested?.planName ??
      flat ??
      raw.planName ??
      raw.binding?.spec?.plan ??
      raw.binding?.plan ??
      null,
    displayName: nested?.displayName ?? raw.displayName ?? null,
    matchedBy: nested?.matchedBy ?? raw.matchedBy ?? null,
  };
}

export function buildPlanBindingItem(
  input: PlanBindingCreateValues,
  existing?: AiPlanBindingItem,
): AiPlanBindingItem {
  return {
    apiVersion: existing?.apiVersion ?? AI_API_VERSION,
    kind: existing?.kind ?? "AiPlanBinding",
    metadata: existing ? { ...existing.metadata, name: input.name } : { name: input.name },
    spec: {
      ...(existing?.spec ?? {}),
      plan: input.plan,
      subject: input.subject,
      priority: input.priority,
      effectiveFrom: input.effectiveFrom ?? null,
      effectiveTo: input.effectiveTo ?? null,
    },
  };
}

export function buildPlanItem(name: string, spec: AiPlanSpec, existing?: AiPlanItem): AiPlanItem {
  return {
    apiVersion: existing?.apiVersion ?? AI_API_VERSION,
    kind: AI_PLAN_KIND,
    metadata: existing ? { ...existing.metadata, name } : { name },
    spec,
  };
}

export function resolvePlanApiError(error: unknown): string {
  if (!isAxiosError(error) && error instanceof Error && error.message) {
    return error.message;
  }
  return resolveApiError(error, {
    notFound: "套餐或绑定不存在，可能已被删除。",
    conflict: "套餐名称或绑定主体已存在，请调整后重试。",
  });
}
