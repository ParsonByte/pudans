import { formatBeijingDateTimeSeconds } from "@/lib/date";

import type { RiskEventItem, RiskUnbanItem } from "./api";

export const EVENT_LABELS: Record<string, string> = {
  RISK_BLOCKED: "拦截",
  RISK_ALERT: "告警",
};

type RiskEventStatusKey = "active" | "unbanned" | "expired" | "alert";

export type RiskEventStatus = {
  key: RiskEventStatusKey;
  label: string;
  variant: "destructive" | "secondary" | "outline";
  banUntil?: string;
  unbanAt?: string;
};

const STATUS_META: Record<
  RiskEventStatusKey,
  { label: string; variant: "destructive" | "secondary" | "outline" }
> = {
  active: { label: "封禁中", variant: "destructive" },
  unbanned: { label: "已解禁", variant: "secondary" },
  expired: { label: "已到期", variant: "outline" },
  alert: { label: "仅告警", variant: "outline" },
};

export function eventActorLabel(event: RiskEventItem | undefined): string {
  if (!event) {
    return "—";
  }

  return event.username || (event.uid && event.uid !== "anonymous" ? event.uid : "匿名");
}

export function resolveEventStatus(
  event: RiskEventItem,
  activeBanKeys: ReadonlySet<string>,
  unbans: readonly RiskUnbanItem[],
  latestBlockByKey: ReadonlyMap<string, string>,
): RiskEventStatus {
  if (event.operation !== "RISK_BLOCKED" || event.blockSeconds <= 0) {
    return { key: "alert", ...STATUS_META.alert };
  }

  const key = `${event.scene}:${event.subject}`;
  const isLatestBlock = latestBlockByKey.get(key) === event.auditId;
  const banUntilMs = new Date(event.timestamp).getTime() + event.blockSeconds * 1000;
  const banUntil = Number.isFinite(banUntilMs) ? new Date(banUntilMs).toISOString() : undefined;
  const unban = unbans.find(
    (item) =>
      item.scene === event.scene &&
      item.subject === event.subject &&
      item.timestamp >= event.timestamp,
  );

  if (isLatestBlock && activeBanKeys.has(key)) {
    return { key: "active", ...STATUS_META.active, banUntil };
  }

  if (unban) {
    return { key: "unbanned", ...STATUS_META.unbanned, banUntil, unbanAt: unban.timestamp };
  }

  if (isLatestBlock && Number.isFinite(banUntilMs) && Date.now() < banUntilMs) {
    return { key: "active", ...STATUS_META.active, banUntil };
  }

  return { key: "expired", ...STATUS_META.expired, banUntil };
}

export function formatExactTime(value?: string): string {
  return formatBeijingDateTimeSeconds(value);
}
