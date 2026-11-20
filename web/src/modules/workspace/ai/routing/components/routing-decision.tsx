import type { ReactNode } from "react";

import { ArrowRightLeft, CheckCircle2, CircleDot, GitBranch } from "lucide-react";

import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { modelDisplayName } from "../../providers/lib/model";
import {
  effectiveParentMode,
  parentModeLabel,
  routeGroupRules,
} from "../../route-groups/lib/model";
import type { AiRoutingLive, AiRoutingRequest } from "../lib/api";
import {
  type AiRoutingLookups,
  accountDisplayName,
  agentLabel,
  formatDuration,
  formatTokenCount,
  providerDisplayName,
  requestStatusLabel,
  routeGroupDisplayName,
  routeModeLabel,
  tierLabel,
  userDisplayName,
} from "../lib/model";

type RoutingDecisionProps = {
  live: AiRoutingLive | undefined;
  lookups: AiRoutingLookups;
  defaultGroupName: string | undefined;
  loading: boolean;
  error: boolean;
};

export function RoutingDecision({
  live,
  lookups,
  defaultGroupName,
  loading,
  error,
}: RoutingDecisionProps) {
  const latest = live?.requests[0];
  const defaultGroup = defaultGroupName ? lookups.routeGroups.get(defaultGroupName) : undefined;
  const latestGroup = latest?.routeGroup ? lookups.routeGroups.get(latest.routeGroup) : undefined;
  const routeGroup = latestGroup ?? defaultGroup;

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
        <h2 className="text-sm font-semibold">最近一个请求是怎么路由的</h2>
        {latest ? (
          <span className="flex items-center gap-2 text-[11px] text-muted-foreground">
            <CircleDot className="size-3.5" />
            {requestStatusLabel(latest.status)}
            <span>·</span>
            <span className="font-mono">{latest.requestId.slice(0, 12)}</span>
          </span>
        ) : null}
      </div>

      {loading ? (
        <div className="space-y-3 px-5 py-5">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-4 w-3/5" />
        </div>
      ) : error ? (
        <p className="px-5 py-5 text-xs text-muted-foreground">无法加载路由记录。</p>
      ) : latest ? (
        <ol className="space-y-3 px-5 py-5">
          <Step icon={<GitBranch className="size-3.5" />}>
            {userDisplayName(latest.userId, lookups)} 通过 {agentLabel(latest.agentSource)} 请求{" "}
            <Strong>{modelLabel(latest, lookups)}</Strong>，由{" "}
            <Strong>
              {providerDisplayName(
                lookups.providers.get(latest.provider ?? ""),
                latest.provider ?? "未知供应商",
              )}
            </Strong>{" "}
            提供。
          </Step>
          <Step icon={<ArrowRightLeft className="size-3.5" />}>
            路由组{" "}
            <Strong>
              {latest.routeGroup
                ? routeGroupDisplayName(
                    lookups.routeGroups.get(latest.routeGroup),
                    latest.routeGroup,
                  )
                : "未指定"}
            </Strong>{" "}
            以 <Strong>{routeModeLabel(routeGroup?.spec.routeMode)}</Strong> 方式选择，账号{" "}
            <Strong>
              {accountDisplayName(
                lookups.accounts.get(latest.account ?? ""),
                latest.account ?? "未知账号",
              )}
            </Strong>
            {latest.tier ? `，等级 ${tierLabel(latest.tier)}` : ""}
            {routeGroup
              ? `；所属模式${parentModeLabel(effectiveParentMode(routeGroup))}，配置 ${
                  routeGroupRules(routeGroup).length
                } 条规则${routeGroup.spec.sink ? "，Sink 开启" : ""}`
              : ""}
            。
          </Step>
          <Step icon={<CheckCircle2 className="size-3.5" />}>
            {providerDisplayName(
              lookups.providers.get(latest.provider ?? ""),
              latest.provider ?? "供应商",
            )}
            （{modelLabel(latest, lookups)}）{requestStatusLabel(latest.status)}
            {latest.ttftMs != null ? `，首字 ${formatDuration(latest.ttftMs)}` : ""}
            {latest.latencyMs != null ? `，总耗时 ${formatDuration(latest.latencyMs)}` : ""}
            {latest.totalTokens != null ? `，${formatTokenCount(latest.totalTokens)} token` : ""}
            {latest.errorCode ? `（${latest.errorCode}）` : ""}。
          </Step>
        </ol>
      ) : (
        <ol className="space-y-3 px-5 py-5">
          <Step icon={<CircleDot className="size-3.5" />}>
            还没有经过 PUDANS 网关的请求，所以暂时没有可展示的路由决定。
          </Step>
          <Step icon={<GitBranch className="size-3.5" />}>
            {defaultGroup ? (
              <>
                默认路由组{" "}
                <Strong>{routeGroupDisplayName(defaultGroup, defaultGroup.metadata.name)}</Strong>{" "}
                使用 <Strong>{routeModeLabel(defaultGroup.spec.routeMode)}</Strong> 方式，所属模式
                <Strong>{parentModeLabel(effectiveParentMode(defaultGroup))}</Strong>，配置{" "}
                <Strong>{routeGroupRules(defaultGroup).length}</Strong> 条规则，包含{" "}
                <Strong>
                  {
                    (defaultGroup.spec.members ?? []).filter((member) => member.enabled !== false)
                      .length
                  }
                </Strong>{" "}
                个模型成员{defaultGroup.spec.sink ? "，Sink 开启" : ""}。
              </>
            ) : (
              "还没有默认路由组。网关需要路由组才能选择模型。"
            )}
          </Step>
          <Step icon={<CheckCircle2 className="size-3.5" />}>
            接入 Agent 并发出第一个请求后，这里会记录真实的选择过程。
          </Step>
        </ol>
      )}

      {latest ? (
        <p className="px-5 pb-4 text-[11px] text-muted-foreground">
          路由记录只包含最终选择结果，不包含命中的规则明细；上方展示的是当前路由组的规则配置。
        </p>
      ) : null}
    </ConsoleSection>
  );
}

function Step({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <li className="flex items-start gap-3 text-sm leading-6">
      <span className="mt-1 flex size-5 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
        {icon}
      </span>
      <span className="min-w-0 flex-1 text-muted-foreground [&_strong]:font-medium [&_strong]:text-foreground">
        {children}
      </span>
    </li>
  );
}

function Strong({ children }: { children: ReactNode }) {
  return <strong>{children}</strong>;
}

function modelLabel(request: AiRoutingRequest, lookups: AiRoutingLookups): string {
  const model = request.model ? lookups.models.get(request.model) : undefined;
  return model ? modelDisplayName(model) : request.model || "未知模型";
}
