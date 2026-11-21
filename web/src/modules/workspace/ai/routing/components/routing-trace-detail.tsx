import type { ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";
import { MessageSquare } from "lucide-react";
import Link from "next/link";

import { DetailDialog } from "@/components/detail-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { resolveApiErrorWithDetail } from "@/lib/api-error";
import { formatBeijingDateTime } from "@/lib/date";

import type { AiRouteGroupMode } from "../../providers/lib/api";
import type {
  AiRouteTrace,
  AiRouteTraceAttempt,
  AiRouteTraceCandidate,
  AiRoutingHistoryDetail,
} from "../lib/api";
import { fetchRoutingHistoryDetail } from "../lib/api";
import {
  type AiRoutingLookups,
  accountDisplayName,
  agentLabel,
  complexityLabel,
  eliminationReasonLabel,
  formatDuration,
  outcomeLabel,
  providerDisplayName,
  requestStatusDotClass,
  requestStatusLabel,
  routeGroupDisplayName,
  routeModeLabel,
  shortIdentifier,
  tierLabel,
  traceAffinityLabel,
} from "../lib/model";

type RoutingTraceDetailProps = {
  requestId: string;
  lookups: AiRoutingLookups;
  onClose: () => void;
};

export function RoutingTraceDetail({ requestId, lookups, onClose }: RoutingTraceDetailProps) {
  const query = useQuery({
    queryKey: ["ai-routing-history-detail", requestId],
    queryFn: () => fetchRoutingHistoryDetail(requestId),
    retry: false,
  });

  return (
    <DetailDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title="路由详情"
      description={<span className="font-mono text-xs">{shortIdentifier(requestId, 32)}</span>}
      size="5xl"
      footer={
        <Button type="button" variant="outline" onClick={onClose}>
          关闭
        </Button>
      }
    >
      <div className="space-y-4 pb-4">
        {query.isPending ? (
          <div className="space-y-3">
            <Skeleton className="h-24" />
            <Skeleton className="h-40" />
            <Skeleton className="h-40" />
          </div>
        ) : query.isError ? (
          <div className="flex flex-col items-center gap-3 border border-dashed px-6 py-10 text-center">
            <p className="text-sm font-medium">无法加载路由详情</p>
            <p className="max-w-md text-xs text-muted-foreground">
              {resolveApiErrorWithDetail(query.error, {
                notFound: "请求不存在或已被删除。",
                conflict: "请求状态冲突，请稍后重试。",
              })}
            </p>
            <Button type="button" variant="outline" size="sm" onClick={() => void query.refetch()}>
              重试
            </Button>
          </div>
        ) : query.data ? (
          <DetailContent detail={query.data} lookups={lookups} />
        ) : null}
      </div>
    </DetailDialog>
  );
}

function DetailContent({
  detail,
  lookups,
}: {
  detail: AiRoutingHistoryDetail;
  lookups: AiRoutingLookups;
}) {
  const provider = detail.provider ? lookups.providers.get(detail.provider) : undefined;
  const account = detail.account ? lookups.accounts.get(detail.account) : undefined;
  const model = detail.model ? lookups.models.get(detail.model) : undefined;
  const group = detail.routeGroup ? lookups.routeGroups.get(detail.routeGroup) : undefined;
  const trace = detail.routeTrace;

  return (
    <>
      <section className="space-y-3 border p-4">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-sm font-semibold">概览</h3>
          <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
            <span className={`size-1.5 ${requestStatusDotClass(detail.status)}`} />
            {requestStatusLabel(detail.status)}
          </span>
          <Badge variant="outline">{agentLabel(detail.agent)}</Badge>
        </div>
        <div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <DetailItem label="路由组">
            {detail.routeGroup ? routeGroupDisplayName(group, detail.routeGroup) : "—"}
          </DetailItem>
          <DetailItem label="模型">
            {detail.model ? model?.spec.displayName || detail.model : "—"}
          </DetailItem>
          <DetailItem label="供应商 / 账号">
            {detail.provider ? providerDisplayName(provider, detail.provider) : "—"}
            {detail.account ? ` · ${accountDisplayName(account, detail.account)}` : ""}
          </DetailItem>
          <DetailItem label="档位">{tierLabel(detail.tier) ?? "—"}</DetailItem>
          <DetailItem label="尝试次数">{detail.tries > 0 ? `${detail.tries} 次` : "—"}</DetailItem>
          <DetailItem label="延迟">{formatDuration(detail.latencyMs)}</DetailItem>
          <DetailItem label="开始时间">{formatBeijingDateTime(detail.createdAt)}</DetailItem>
          <DetailItem label="完成时间">{formatBeijingDateTime(detail.completedAt)}</DetailItem>
        </div>
        {detail.conversationId ? (
          <div className="flex flex-wrap items-center gap-3 bg-muted/40 px-3 py-2">
            <p className="min-w-0 flex-1 text-xs text-muted-foreground">
              请求关联会话 #{detail.conversationId}。Pudans 不会自动重发请求，请打开原会话继续提问。
            </p>
            <Button type="button" variant="outline" size="sm" asChild>
              <Link href={`/chat/${detail.conversationId}`}>
                <MessageSquare />
                去聊天重试
              </Link>
            </Button>
          </div>
        ) : null}
      </section>

      <section className="space-y-3 border p-4">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-sm font-semibold">路由追踪</h3>
          {trace?.outcome ? <Badge variant="outline">{outcomeLabel(trace.outcome)}</Badge> : null}
        </div>
        {!trace ? (
          <p className="border border-dashed p-4 text-sm text-muted-foreground">
            本次请求没有保存路由追踪。
          </p>
        ) : (
          <TraceContent trace={trace} lookups={lookups} />
        )}
        {detail.retrievalTrace ? (
          <details className="border">
            <summary className="cursor-pointer px-3 py-2 text-sm font-medium">检索追踪</summary>
            <pre className="max-h-72 overflow-auto border-t bg-muted/40 p-3 font-mono text-[11px]">
              {JSON.stringify(detail.retrievalTrace, null, 2)}
            </pre>
          </details>
        ) : null}
      </section>
    </>
  );
}

function TraceContent({ trace, lookups }: { trace: AiRouteTrace; lookups: AiRoutingLookups }) {
  const classification = trace.classification;
  const matchedRule = trace.matchedRule;

  return (
    <div className="space-y-4">
      <div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <DetailItem label="路由模式">
          {routeModeLabel(trace.routeMode as AiRouteGroupMode | undefined)}
        </DetailItem>
        <DetailItem label="亲和">{traceAffinityLabel(trace.affinity)}</DetailItem>
        <DetailItem label="请求档位">{tierLabel(trace.requestedTier) ?? "—"}</DetailItem>
        <DetailItem label="解析档位">{tierLabel(trace.resolvedTier) ?? "—"}</DetailItem>
        <DetailItem label="选中结果">
          {trace.selected
            ? `${trace.selected.provider ? providerDisplayName(lookups.providers.get(trace.selected.provider), trace.selected.provider) : "—"} · ${
                trace.selected.model
                  ? (lookups.models.get(trace.selected.model)?.spec.displayName ??
                    trace.selected.model)
                  : "—"
              }`
            : "未选中"}
        </DetailItem>
        <DetailItem label="复杂度">
          {classification
            ? [
                complexityLabel(classification.level),
                classification.confidence == null
                  ? undefined
                  : `置信度 ${Math.round(classification.confidence * 100)}%`,
                tierLabel(classification.tier),
              ]
                .filter(Boolean)
                .join(" · ")
            : "—"}
        </DetailItem>
        <DetailItem label="调用密钥">
          {trace.callerKey ? shortIdentifier(trace.callerKey, 24) : "—"}
        </DetailItem>
      </div>

      {matchedRule ? (
        <div className="space-y-1 border bg-muted/20 p-3 text-xs">
          <p className="text-sm font-medium">
            命中规则 {matchedRule.name || "未命名"} · 优先级 {matchedRule.priority ?? 0}
          </p>
          <p className="text-muted-foreground">条件：{matchedRule.condition || "无"}</p>
          <p className="text-muted-foreground">成员：{matchedRule.member || "—"}</p>
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">没有命中自定义规则。</p>
      )}

      <div className="space-y-2">
        <h4 className="text-xs font-medium text-muted-foreground">
          候选（{(trace.candidates ?? []).length}）
        </h4>
        {(trace.candidates ?? []).length === 0 ? (
          <p className="border border-dashed p-3 text-xs text-muted-foreground">没有候选记录。</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>模型</TableHead>
                <TableHead>供应商 / 账号</TableHead>
                <TableHead>档位</TableHead>
                <TableHead>结果</TableHead>
                <TableHead className="text-end">健康度</TableHead>
                <TableHead>来源</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(trace.candidates ?? []).map((candidate, index) => (
                <CandidateRow
                  key={`${candidate.provider}-${candidate.account}-${candidate.model}-${index}`}
                  candidate={candidate}
                  lookups={lookups}
                />
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <div className="space-y-2">
        <h4 className="text-xs font-medium text-muted-foreground">
          尝试（{(trace.attempts ?? []).length}）
        </h4>
        {(trace.attempts ?? []).length === 0 ? (
          <p className="border border-dashed p-3 text-xs text-muted-foreground">没有尝试记录。</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>次数</TableHead>
                <TableHead>结果</TableHead>
                <TableHead>模型</TableHead>
                <TableHead>供应商 / 账号</TableHead>
                <TableHead>HTTP</TableHead>
                <TableHead className="text-end">延迟</TableHead>
                <TableHead>时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(trace.attempts ?? []).map((attempt, index) => (
                <AttemptRow
                  key={`${attempt.attempt}-${attempt.provider}-${attempt.model}-${index}`}
                  attempt={attempt}
                  lookups={lookups}
                />
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5 border p-3">
          <p className="text-xs font-medium text-muted-foreground">后备组</p>
          {(trace.fallbackGroups ?? []).length === 0 ? (
            <p className="text-xs text-muted-foreground">未设置。</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {(trace.fallbackGroups ?? []).map((name) => (
                <Badge key={name} variant="outline">
                  {routeGroupDisplayName(lookups.routeGroups.get(name), name)}
                </Badge>
              ))}
            </div>
          )}
        </div>
        <div className="space-y-1.5 border p-3">
          <p className="text-xs font-medium text-muted-foreground">后备供应商</p>
          {(trace.fallback ?? []).length === 0 ? (
            <p className="text-xs text-muted-foreground">未设置。</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {(trace.fallback ?? []).map((name) => (
                <Badge key={name} variant="outline">
                  {providerDisplayName(lookups.providers.get(name), name)}
                </Badge>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function CandidateRow({
  candidate,
  lookups,
}: {
  candidate: AiRouteTraceCandidate;
  lookups: AiRoutingLookups;
}) {
  return (
    <TableRow>
      <TableCell className="max-w-56">
        <span className="block truncate text-xs">
          {lookups.models.get(candidate.model)?.spec.displayName || candidate.model}
        </span>
        <span className="block truncate font-mono text-[11px] text-muted-foreground">
          {candidate.model}
        </span>
      </TableCell>
      <TableCell className="max-w-48 text-xs">
        <span className="block truncate">
          {providerDisplayName(lookups.providers.get(candidate.provider), candidate.provider)}
        </span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {accountDisplayName(lookups.accounts.get(candidate.account), candidate.account)}
        </span>
      </TableCell>
      <TableCell className="text-xs text-muted-foreground">
        {tierLabel(candidate.tier) ?? "—"}
      </TableCell>
      <TableCell>
        {candidate.eligible ? (
          <Badge variant="secondary">可用</Badge>
        ) : (
          <Badge variant="outline">{eliminationReasonLabel(candidate.reason)}</Badge>
        )}
      </TableCell>
      <TableCell className="text-end text-xs text-muted-foreground">
        {candidate.healthScore == null ? "—" : `${Math.round(candidate.healthScore)}%`}
      </TableCell>
      <TableCell className="text-xs text-muted-foreground">{candidate.source || "—"}</TableCell>
    </TableRow>
  );
}

function AttemptRow({
  attempt,
  lookups,
}: {
  attempt: AiRouteTraceAttempt;
  lookups: AiRoutingLookups;
}) {
  return (
    <TableRow>
      <TableCell className="text-xs text-muted-foreground">{attempt.attempt}</TableCell>
      <TableCell>
        <Badge variant={attempt.success ? "secondary" : "destructive"}>
          {attempt.success ? "成功" : "失败"}
        </Badge>
      </TableCell>
      <TableCell className="max-w-48">
        <span className="block truncate text-xs">
          {lookups.models.get(attempt.model)?.spec.displayName || attempt.model}
        </span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {attempt.group
            ? routeGroupDisplayName(lookups.routeGroups.get(attempt.group), attempt.group)
            : attempt.model}
        </span>
      </TableCell>
      <TableCell className="max-w-44 text-xs">
        <span className="block truncate">
          {providerDisplayName(lookups.providers.get(attempt.provider), attempt.provider)}
        </span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {accountDisplayName(lookups.accounts.get(attempt.account), attempt.account)}
        </span>
      </TableCell>
      <TableCell className="text-xs text-muted-foreground">
        {attempt.status ?? "—"}
        {attempt.errorCode ? ` · ${attempt.errorCode}` : ""}
      </TableCell>
      <TableCell className="text-end text-xs text-muted-foreground">
        {formatDuration(attempt.latencyMillis)}
      </TableCell>
      <TableCell className="text-xs text-muted-foreground">
        {formatBeijingDateTime(attempt.at)}
      </TableCell>
    </TableRow>
  );
}

function DetailItem({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-0.5">
      <span className="text-[11px] text-muted-foreground">{label}</span>
      <div className="min-w-0 truncate text-sm">{children}</div>
    </div>
  );
}
