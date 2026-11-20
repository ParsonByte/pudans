import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { resolveApiError } from "@/lib/api-error";

import { AI_API_PREFIX } from "../../providers/lib/api";

const PERSONAL_ACCESS_TOKEN_PATH = `${AI_API_PREFIX}/personalaccesstokens`;

const GATEWAY_STATUS_PATH = `${AI_API_PREFIX}/aigateway/-/status`;

const ACCESS_TOKEN_ANNOTATION = "security.pudans.tolim.cn/access-token";

export const GATEWAY_TOKEN_ROLE = "role-template-ai-gateway";

export const GATEWAY_TOKEN_DEFAULT_DAYS = 90;

export const GATEWAY_KEY_LIMIT_MAX = 5;

export type GatewayServiceTier = "STANDARD" | "PRIORITY" | "ULTRAFAST";

type GatewayLimitPeriod = "DAY" | "WEEK" | "MONTH" | "TOTAL";

export type GatewayKeyLimit = {
  period: GatewayLimitPeriod;
  tokens?: number;
  points?: number;
  requests?: number;
  models?: string[];
  account?: string;
};

export type GatewayKeySpec = {
  enabled?: boolean;
  models?: string[];
  maxConcurrency?: number;
  serviceTier?: GatewayServiceTier;
  limits?: GatewayKeyLimit[];
};

type GatewayTokenSpec = {
  name: string;
  description?: string;
  expiresAt?: string;
  roles?: string[];
  scopes?: string[];
  scopeMode?: string;
  username?: string;
  revoked?: boolean;
  revokesAt?: string;
  lastUsed?: string;
  tokenId?: string;
  gateway?: GatewayKeySpec;
};

export type GatewayTokenItem = {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    creationTimestamp?: string;
    annotations?: Record<string, string>;
  };
  spec: GatewayTokenSpec;
};

export type GatewayCreatedToken = {
  name: string;
  displayName: string;
  expiresAt?: string;
  token?: string;
};

export type GatewayTokenInput = {
  name: string;
  description?: string;
  expiryDays: number;
  gateway?: GatewayKeySpec;
};

export type GatewayRecentError = {
  code: string;
  at?: string;
};

export type GatewayStatus = {
  enabled?: boolean;
  entryUrl?: string;
  protocols?: unknown;
  models?: unknown;
  routeGroups?: unknown;
  defaultRouteGroup?: string;
  rateLimitEnabled?: boolean;
  recentErrors?: unknown;
  version?: string;
};

export function isHttpStatus(error: unknown, status: number): boolean {
  return isAxiosError(error) && error.response?.status === status;
}

export function isForbiddenError(error: unknown): boolean {
  return isHttpStatus(error, 403);
}

export function isNotFoundError(error: unknown): boolean {
  return isHttpStatus(error, 404);
}

export function gatewayKeySpecConfigured(gateway: GatewayKeySpec | undefined): boolean {
  if (!gateway) {
    return false;
  }
  return (
    gateway.enabled === false ||
    (gateway.models?.length ?? 0) > 0 ||
    gateway.maxConcurrency != null ||
    (gateway.serviceTier != null && gateway.serviceTier !== "STANDARD") ||
    (gateway.limits?.length ?? 0) > 0
  );
}

export function gatewaySpecObserved(tokens: readonly GatewayTokenItem[]): boolean {
  return tokens.some((token) => gatewayKeySpecConfigured(token.spec.gateway));
}

function isGatewayToken(item: GatewayTokenItem): boolean {
  return (
    (item.spec.roles ?? []).includes(GATEWAY_TOKEN_ROLE) ||
    gatewayKeySpecConfigured(item.spec.gateway)
  );
}

export async function fetchGatewayTokens(): Promise<GatewayTokenItem[]> {
  const response = await axiosInstance.get<GatewayTokenItem[]>(PERSONAL_ACCESS_TOKEN_PATH);
  return response.data.filter(isGatewayToken);
}

type NormalizedGatewaySpec = {
  enabled: boolean;
  models: string[];
  maxConcurrency: number | null;
  serviceTier: GatewayServiceTier;
  limits: {
    period: GatewayLimitPeriod;
    tokens: number | null;
    points: number | null;
    requests: number | null;
    models: string[];
    account: string | null;
  }[];
};

function normalizeGatewaySpec(gateway: GatewayKeySpec | undefined): NormalizedGatewaySpec {
  return {
    enabled: gateway?.enabled ?? true,
    models: [...(gateway?.models ?? [])].sort(),
    maxConcurrency: gateway?.maxConcurrency ?? null,
    serviceTier: gateway?.serviceTier ?? "STANDARD",
    limits: (gateway?.limits ?? []).map((limit) => ({
      period: limit.period,
      tokens: limit.tokens ?? null,
      points: limit.points ?? null,
      requests: limit.requests ?? null,
      models: [...(limit.models ?? [])].sort(),
      account: limit.account ?? null,
    })),
  };
}

export type GatewayTokenCreation = {
  item: GatewayTokenItem;
  created: GatewayCreatedToken;
  gatewayApplied: boolean;
};

export async function createGatewayToken(input: GatewayTokenInput): Promise<GatewayTokenCreation> {
  const expiresAt = new Date(Date.now() + input.expiryDays * 24 * 60 * 60 * 1000).toISOString();
  const response = await axiosInstance.post<GatewayTokenItem>(PERSONAL_ACCESS_TOKEN_PATH, {
    apiVersion: "security.pudans.tolim.cn/v1alpha1",
    kind: "PersonalAccessToken",
    metadata: {},
    spec: {
      name: input.name,
      ...(input.description ? { description: input.description } : {}),
      roles: [GATEWAY_TOKEN_ROLE],
      scopeMode: "RolesOnly",
      expiresAt,
      ...(input.gateway ? { gateway: input.gateway } : {}),
    },
  });
  const item = response.data;
  const token = item.metadata.annotations?.[ACCESS_TOKEN_ANNOTATION] || undefined;
  return {
    item,
    created: {
      name: item.metadata.name,
      displayName: input.name,
      token,
      expiresAt: item.spec.expiresAt ?? expiresAt,
    },
    gatewayApplied:
      input.gateway === undefined ||
      JSON.stringify(normalizeGatewaySpec(input.gateway)) ===
        JSON.stringify(normalizeGatewaySpec(item.spec.gateway)),
  };
}

export async function revokeGatewayToken(name: string): Promise<GatewayTokenItem> {
  const response = await axiosInstance.put<GatewayTokenItem>(
    `${PERSONAL_ACCESS_TOKEN_PATH}/${encodeURIComponent(name)}/actions/revocation`,
  );
  return response.data;
}

export async function restoreGatewayToken(name: string): Promise<GatewayTokenItem> {
  const response = await axiosInstance.put<GatewayTokenItem>(
    `${PERSONAL_ACCESS_TOKEN_PATH}/${encodeURIComponent(name)}/actions/restoration`,
  );
  return response.data;
}

export async function deleteGatewayToken(name: string): Promise<void> {
  await axiosInstance.delete(`${PERSONAL_ACCESS_TOKEN_PATH}/${encodeURIComponent(name)}`);
}

export async function fetchGatewayStatus(): Promise<GatewayStatus> {
  const response = await axiosInstance.get<GatewayStatus>(GATEWAY_STATUS_PATH);
  return response.data;
}

export function resolveGatewayApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "网关密钥不存在或已被删除。",
    conflict: "网关密钥状态已变化，请刷新后重试。",
  });
}
