import { useMemo, useState } from "react";

import { Icon } from "@iconify/react";
import { Link } from "@tanstack/react-router";
import { ArrowRight } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import {
  MODEL_TIER_OPTIONS,
  formatNumber,
  modelDisplayName,
  optionLabel,
} from "../../providers/lib/model";
import type { AiRoutingLive, AiRoutingRequest } from "../lib/api";
import {
  type AiRoutingLookups,
  accountDisplayName,
  agentIcon,
  agentLabel,
  cacheHitRate,
  dayKeyLabel,
  formatClock,
  formatDuration,
  formatPoints,
  formatRelativeTime,
  formatTokenCount,
  providerDisplayName,
  requestStatusDotClass,
  requestStatusLabel,
  routeGroupDisplayName,
  shortIdentifier,
  userDisplayName,
  utcDayKey,
} from "../lib/model";

type RoutingRequestsProps = {
  live: AiRoutingLive | undefined;
  lookups: AiRoutingLookups;
  loading: boolean;
  error: boolean;
  usagePath: string | undefined;
};

type GroupMode = "request" | "session";

export function RoutingRequests({
  live,
  lookups,
  loading,
  error,
  usagePath,
}: RoutingRequestsProps) {
  const [selectedDay, setSelectedDay] = useState("live");
  const [groupMode, setGroupMode] = useState<GroupMode>("request");
  const requests = useMemo(() => live?.requests ?? [], [live?.requests]);
  const days = live?.daily ?? [];
  const activeDay =
    selectedDay === "live" || days.some((day) => day.day === selectedDay) ? selectedDay : "live";

  const filtered = useMemo(
    () =>
      activeDay === "live"
        ? requests
        : requests.filter((request) => utcDayKey(request.createdAt) === activeDay),
    [activeDay, requests],
  );

  const sessions = useMemo(() => groupSessions(filtered), [filtered]);

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold">请求</h2>
          <Badge variant="secondary">{filtered.length}</Badge>
        </div>
        <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
          <span>网关返回的最近 {requests.length} 个</span>
          {usagePath ? (
            <Button variant="ghost" size="sm" asChild>
              <Link to={usagePath}>查看用量</Link>
            </Button>
          ) : null}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3">
        <DayPill
          active={activeDay === "live"}
          label="实时"
          onClick={() => setSelectedDay("live")}
        />
        {days.map((day) => (
          <DayPill
            key={day.day}
            active={activeDay === day.day}
            label={dayKeyLabel(day.day)}
            count={day.requests}
            onClick={() => setSelectedDay(day.day)}
          />
        ))}
        <div className="ms-auto inline-flex overflow-hidden rounded-lg border bg-muted/30 p-0.5">
          <ModeButton
            active={groupMode === "request"}
            onClick={() => setGroupMode("request")}
            label="按请求"
          />
          <ModeButton
            active={groupMode === "session"}
            onClick={() => setGroupMode("session")}
            label="按会话"
          />
        </div>
      </div>

      {loading ? (
        <div className="space-y-3 px-5 py-4">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : error ? (
        <p className="px-5 py-5 text-xs text-muted-foreground">无法加载请求记录。</p>
      ) : filtered.length === 0 ? (
        <ConsoleEmpty>
          {requests.length === 0
            ? "还没有经过 PUDANS 网关的请求。接入 Agent 后，这里会实时显示每次路由。"
            : "这个时间范围内没有请求。"}
        </ConsoleEmpty>
      ) : groupMode === "session" ? (
        <div className="divide-y">
          {sessions.map((session) => (
            <div key={session.key}>
              <div className="flex flex-wrap items-center gap-2 bg-muted/20 px-5 py-2.5">
                <Icon
                  icon={agentIcon(session.agentSource)}
                  className="size-3.5 text-muted-foreground"
                  aria-hidden="true"
                />
                <span className="text-sm font-medium">{agentLabel(session.agentSource)}</span>
                <span className="font-mono text-[11px] text-muted-foreground">
                  {shortIdentifier(session.key, 28)}
                </span>
                <Badge variant="outline">{session.requests.length} 个请求</Badge>
                <span className="ms-auto text-[11px] text-muted-foreground">
                  {formatTokenCount(session.tokens)} token · {formatRelativeTime(session.latestAt)}
                </span>
              </div>
              <div className="divide-y">
                {session.requests.map((request) => (
                  <RequestRow key={request.requestId} request={request} lookups={lookups} />
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="divide-y">
          {filtered.map((request) => (
            <RequestRow key={request.requestId} request={request} lookups={lookups} />
          ))}
        </div>
      )}
    </ConsoleSection>
  );
}

function RequestRow({
  request,
  lookups,
}: {
  request: AiRoutingRequest;
  lookups: AiRoutingLookups;
}) {
  const provider = request.provider ? lookups.providers.get(request.provider) : undefined;
  const model = request.model ? lookups.models.get(request.model) : undefined;
  const cacheRate = cacheHitRate(request.inputTokens, request.cacheTokens);
  const metrics: string[] = [];
  if (request.latencyMs != null) {
    metrics.push(formatDuration(request.latencyMs));
  }
  if (request.ttftMs != null) {
    metrics.push(`首字 ${formatDuration(request.ttftMs)}`);
  }
  if (request.totalTokens != null) {
    metrics.push(`${formatTokenCount(request.totalTokens)} token`);
  }
  if (cacheRate != null) {
    metrics.push(`缓存 ${cacheRate.toFixed(1)}%`);
  }
  if (request.tps != null) {
    metrics.push(`${formatNumber(request.tps, 1)} token/秒`);
  }
  const points = formatPoints(request.points);
  if (points) {
    metrics.push(points);
  }

  return (
    <div
      className={`px-5 py-3 text-sm transition-colors hover:bg-muted/20 ${
        request.status === "RUNNING" ? "bg-amber-500/5" : ""
      }`}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-[11px] text-muted-foreground">
          {formatClock(request.createdAt)}
        </span>
        <span className={`size-1.5 rounded-full ${requestStatusDotClass(request.status)}`} />
        <span className="inline-flex items-center gap-1.5">
          <Icon
            icon={agentIcon(request.agentSource)}
            className="size-3.5 text-muted-foreground"
            aria-hidden="true"
          />
          {agentLabel(request.agentSource)}
        </span>
        <ArrowRight className="size-3 text-muted-foreground" aria-hidden="true" />
        <span className="inline-flex min-w-0 items-center gap-1.5">
          <ProviderBrandIcon provider={provider} className="size-3.5" />
          <span className="truncate">
            {providerDisplayName(provider, request.provider ?? "未知供应商")}
          </span>
        </span>
        <span className="text-muted-foreground">·</span>
        <span className="truncate font-medium">
          {model ? modelDisplayName(model) : request.model || "未知模型"}
        </span>
        {request.tier ? (
          <Badge variant="outline">{optionLabel(MODEL_TIER_OPTIONS, request.tier)}</Badge>
        ) : null}
        <span className="ms-auto flex items-center gap-2">
          <Badge
            variant={
              request.status === "FAILED"
                ? "destructive"
                : request.status === "SUCCESS" || request.status === "SUCCEEDED"
                  ? "secondary"
                  : "outline"
            }
          >
            {requestStatusLabel(request.status)}
          </Badge>
          <span className="text-[11px] text-muted-foreground">
            {formatRelativeTime(request.completedAt ?? request.createdAt)}
          </span>
        </span>
      </div>

      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
        {metrics.length > 0 ? <span>{metrics.join(" · ")}</span> : <span>等待用量上报</span>}
        <span>用户 {userDisplayName(request.userId, lookups)}</span>
        {request.routeGroup ? (
          <span>
            路由组{" "}
            {routeGroupDisplayName(lookups.routeGroups.get(request.routeGroup), request.routeGroup)}
          </span>
        ) : null}
        {request.account ? (
          <span>
            账号 {accountDisplayName(lookups.accounts.get(request.account), request.account)}
          </span>
        ) : null}
        {request.errorCode ? <span className="text-destructive">{request.errorCode}</span> : null}
      </div>
    </div>
  );
}

function DayPill({
  active,
  label,
  count,
  onClick,
}: {
  active: boolean;
  label: string;
  count?: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex h-7 items-center gap-1.5 rounded-full border px-3 text-xs transition-colors ${
        active
          ? "border-primary/40 bg-primary/10 font-medium text-primary"
          : "border-border/60 bg-muted/30 text-muted-foreground hover:border-border hover:text-foreground"
      }`}
    >
      {label}
      {count != null ? <span className="tabular-nums opacity-70">{count}</span> : null}
    </button>
  );
}

function ModeButton({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-md px-2.5 py-1 text-xs transition-colors ${
        active
          ? "bg-background font-medium text-foreground shadow-sm"
          : "text-muted-foreground hover:text-foreground"
      }`}
    >
      {label}
    </button>
  );
}

type SessionGroup = {
  key: string;
  agentSource: string;
  requests: AiRoutingRequest[];
  latestAt: string;
  tokens: number;
};

function groupSessions(requests: AiRoutingRequest[]): SessionGroup[] {
  const groups = new Map<string, SessionGroup>();
  for (const request of requests) {
    const key =
      request.sessionKey ||
      (request.conversationId != null
        ? `conversation-${request.conversationId}`
        : request.requestId);
    const existing = groups.get(key);
    if (existing) {
      existing.requests.push(request);
      existing.tokens += request.totalTokens ?? 0;
      if (request.createdAt > existing.latestAt) {
        existing.latestAt = request.createdAt;
      }
      continue;
    }
    groups.set(key, {
      key,
      agentSource: request.agentSource,
      requests: [request],
      latestAt: request.createdAt,
      tokens: request.totalTokens ?? 0,
    });
  }
  return [...groups.values()].sort((left, right) => right.latestAt.localeCompare(left.latestAt));
}
