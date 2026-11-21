import { useMemo, useState } from "react";

import type { AuditRecord } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Loader2, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AuditLogDetailDialog } from "@/modules/workspace/console/audit/components/audit-log-detail-dialog";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import {
  formatAuditExactTime,
  localDateTimeToIso,
  todayAuditTimeRange,
} from "@/modules/workspace/console/audit/lib/api";
import {
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { JourneyChain } from "./components/journey-chain";
import { type JourneyItem, fetchJourneysPage } from "./lib/api";
import { formatDuration } from "./lib/traces";

type JourneyDetailViewProps = {
  uid: string;
};

export function JourneyDetailView({ uid }: JourneyDetailViewProps) {
  const tracesPath = useMenuPath("traces");
  const searchParams = useSearchParams();
  const search = {
    from: searchParams.get("from") ?? undefined,
    to: searchParams.get("to") ?? undefined,
  };
  const range = todayAuditTimeRange();
  const from = search.from || localDateTimeToIso(range.from);
  const to = search.to || localDateTimeToIso(range.to);
  const [detailTarget, setDetailTarget] = useState<AuditRecord | null>(null);

  const query = useQuery({
    queryKey: ["user-journey", uid, from, to],
    queryFn: () => fetchJourneysPage({ uid, from, to, page: 1, size: 1 }),
    retry: false,
  });

  const journey = useMemo(
    () => (query.data?.items ?? []).find((item): item is JourneyItem => Boolean(item.uid)) ?? null,
    [query.data],
  );

  if (query.isPending) {
    return (
      <ConsolePage title="链路详情">
        <ConsoleBlockSkeleton rows={3} className="space-y-4" itemClassName="h-32 w-full" />
      </ConsolePage>
    );
  }

  if (query.isError || !journey) {
    return (
      <ConsolePage title="链路详情">
        <div className="flex flex-wrap items-center gap-2">
          {tracesPath ? (
            <Button variant="ghost" size="sm" asChild>
              <Link href={tracesPath}>
                <ArrowLeft />
                返回链路
              </Link>
            </Button>
          ) : null}
        </div>
        <ConsoleSection>
          <ConsoleState
            variant={query.isError ? "error" : "empty"}
            title={query.isError ? "无法加载链路" : "该用户在当前时间范围内没有活动记录。"}
            description={query.isError ? "请稍后重试。" : undefined}
            onRetry={query.isError ? () => void query.refetch() : undefined}
          />
        </ConsoleSection>
      </ConsolePage>
    );
  }

  return (
    <ConsolePage title="链路详情">
      <div className="flex flex-wrap items-center gap-2">
        {tracesPath ? (
          <Button variant="ghost" size="sm" asChild>
            <Link href={tracesPath}>
              <ArrowLeft />
              返回链路
            </Link>
          </Button>
        ) : null}
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="ms-auto"
          onClick={() => void query.refetch()}
          disabled={query.isFetching}
        >
          {query.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
          刷新
        </Button>
      </div>

      <ConsoleSection>
        <ConsoleSectionHeader
          title={journey.username || journey.uid}
          description={`用户 ${journey.uid} · ${formatAuditExactTime(journey.start)} → ${formatAuditExactTime(journey.end)} · ${formatDuration(journey.durationMs)} · ${journey.pageCount ?? 0} 个页面 · ${journey.actionCount ?? 0} 个操作 · ${journey.sessionCount ?? 0} 段活动`}
          border={false}
          action={
            <div className="flex items-center gap-2">
              <Badge variant={journey.complete ? "secondary" : "destructive"}>
                {journey.complete ? "链路完整" : "缺少页面入口"}
              </Badge>
              <AuditResultBadge result={journey.status ?? "SUCCESS"} />
            </div>
          }
        />
        <JourneyChain journey={journey} onSelectRecord={setDetailTarget} />
      </ConsoleSection>

      <AuditLogDetailDialog record={detailTarget} onClose={() => setDetailTarget(null)} />
    </ConsolePage>
  );
}
