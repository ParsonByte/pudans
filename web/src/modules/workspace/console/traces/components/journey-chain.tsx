import { useState } from "react";

import type { AuditJourneyStep, AuditRecord } from "@pudans-dev/api-client";
import { ChevronDown, ChevronRight } from "lucide-react";

import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import { formatAuditExactTime } from "@/modules/workspace/console/audit/lib/api";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import { type JourneyItem } from "../lib/api";

type JourneyChainProps = {
  journey: JourneyItem;
  onSelectRecord: (record: AuditRecord) => void;
};

export function JourneyChain({ journey, onSelectRecord }: JourneyChainProps) {
  const steps = journey.steps ?? [];
  const orderedSteps = [...steps].reverse();
  const stepKeys = orderedSteps.map((step, index) => stepKey(step, index));
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set(stepKeys));
  const allCollapsed = stepKeys.length > 0 && stepKeys.every((key) => collapsed.has(key));
  const complete = Boolean(journey.complete);
  const operations = journey.operations ?? [];
  const resourceTypes = journey.resourceTypes ?? [];

  const toggleStep = (key: string) => {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const toggleAll = () => {
    setCollapsed(allCollapsed ? new Set() : new Set(stepKeys));
  };

  return (
    <>
      <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3 text-xs">
        <Badge variant="outline">入口 {journey.entryPath || "无页面"}</Badge>
        <ChevronRight className="size-3.5 text-muted-foreground" />
        <span className="text-muted-foreground">{journey.pageCount ?? 0} 个页面</span>
        <ChevronRight className="size-3.5 text-muted-foreground" />
        <Badge variant="outline">出口 {journey.exitPath || "无页面"}</Badge>
      </div>

      <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3">
        <span className="text-xs text-muted-foreground">操作类型</span>
        {operations.length === 0 ? (
          <span className="text-xs text-muted-foreground">—</span>
        ) : (
          operations.map((operation) => (
            <Badge key={operation} variant="secondary">
              {auditOperationLabel(operation)}
            </Badge>
          ))
        )}
        <span className="ms-3 text-xs text-muted-foreground">访问资源</span>
        {resourceTypes.length === 0 ? (
          <span className="text-xs text-muted-foreground">—</span>
        ) : (
          resourceTypes.map((resourceType) => (
            <Badge key={resourceType} variant="outline">
              {auditResourceLabel(resourceType)}
            </Badge>
          ))
        )}
      </div>

      {!complete ? (
        <div className="border-b bg-destructive/5 px-5 py-2 text-xs text-destructive">
          该用户在当前范围内没有页面访问记录，链路缺少入口和出口。
        </div>
      ) : null}

      {steps.length === 0 ? (
        <ConsoleEmpty>该用户没有可展示的活动步骤。</ConsoleEmpty>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 border-b px-5 py-2">
            <span className="text-xs text-muted-foreground">
              {orderedSteps.length} 个步骤 · 按时间倒序
            </span>
            <Button type="button" variant="outline" size="sm" onClick={toggleAll}>
              {allCollapsed ? <ChevronDown /> : <ChevronRight />}
              {allCollapsed ? "展开全部步骤" : "收起全部步骤"}
            </Button>
          </div>
          <div className="divide-y">
            {orderedSteps.map((step, index) => {
              const key = stepKey(step, index);
              return (
                <JourneyStepRow
                  key={key}
                  step={step}
                  open={!collapsed.has(key)}
                  onToggle={() => toggleStep(key)}
                  onSelectRecord={onSelectRecord}
                />
              );
            })}
          </div>
        </>
      )}
    </>
  );
}

type JourneyStepRowProps = {
  step: AuditJourneyStep;
  open: boolean;
  onToggle: () => void;
  onSelectRecord: (record: AuditRecord) => void;
};

function JourneyStepRow({ step, open, onToggle, onSelectRecord }: JourneyStepRowProps) {
  const page = step.page;
  const actions = [...(step.actions ?? [])].reverse();
  const isPage = step.kind === "PAGE";
  const path = pagePathOf(page) || step.toPath || "—";

  return (
    <div className="px-5 py-3">
      <div className="flex items-start gap-3">
        <button
          type="button"
          className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
          aria-label={open ? "收起步骤" : "展开步骤"}
          onClick={onToggle}
        >
          {open ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
        </button>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={isPage ? "secondary" : "outline"}>{isPage ? "页面" : "进入前"}</Badge>
            <span className="font-mono text-xs">{path}</span>
            {page ? <AuditResultBadge result={page.result} /> : null}
            <span className="text-[11px] text-muted-foreground tabular-nums">
              {formatAuditExactTime(step.start)} → {formatAuditExactTime(step.end)}
            </span>
            <span className="text-[11px] text-muted-foreground">{actions.length} 个操作</span>
            {page ? (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="ms-auto"
                onClick={() => onSelectRecord(page)}
              >
                页面记录
              </Button>
            ) : null}
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {isPage
              ? `${step.fromPath ? `来自 ${step.fromPath}` : "直接进入"} → ${
                  step.toPath ? `前往 ${step.toPath}` : "链路结束"
                }`
              : `进入 ${step.toPath || "第一个页面"} 之前的操作`}
          </p>
          {open ? (
            <JourneyActionList actions={actions} page={page} onSelectRecord={onSelectRecord} />
          ) : null}
        </div>
      </div>
    </div>
  );
}

type JourneyActionListProps = {
  actions: AuditRecord[];
  page?: AuditRecord;
  onSelectRecord: (record: AuditRecord) => void;
};

function JourneyActionList({ actions, page, onSelectRecord }: JourneyActionListProps) {
  if (actions.length === 0) {
    return (
      <p className="mt-3 rounded-lg border bg-muted/20 px-3 py-2 text-xs text-muted-foreground">
        该页面没有记录到操作。
      </p>
    );
  }
  const actionIds = new Set(actions.map((action) => action.metadata.name));
  return (
    <div className="mt-3 divide-y rounded-lg border bg-muted/20">
      {actions.map((action) => {
        const child = Boolean(
          action.parentAuditId &&
          action.parentAuditId !== page?.metadata.name &&
          actionIds.has(action.parentAuditId),
        );
        return (
          <button
            key={action.metadata.name}
            type="button"
            className="flex w-full min-w-0 items-start gap-3 px-3 py-2 text-left transition-colors hover:bg-muted/60"
            style={{ paddingLeft: `${12 + (child ? 16 : 0)}px` }}
            onClick={() => onSelectRecord(action)}
          >
            <span className="w-36 shrink-0 text-[11px] text-muted-foreground tabular-nums">
              {formatAuditExactTime(action.timestamp)}
            </span>
            <span className="w-24 shrink-0 text-xs font-medium">
              {auditOperationLabel(action.operation)}
            </span>
            <Badge variant="outline" className="shrink-0">
              {auditResourceLabel(action.resourceType)}
            </Badge>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-xs">{action.resourceName || "—"}</span>
              <LongText className="font-mono text-[11px] text-muted-foreground">
                {action.requestMethod || "—"} {action.requestPath || ""}
              </LongText>
            </span>
            <AuditResultBadge result={action.result} />
          </button>
        );
      })}
    </div>
  );
}

function stepKey(step: AuditJourneyStep, index: number): string {
  return `${step.kind ?? "STEP"}:${index}`;
}

function pagePathOf(page?: AuditRecord): string | undefined {
  if (page?.resourceType === "Page" && page.resourceName) {
    return page.resourceName;
  }
  return page?.requestPath;
}
