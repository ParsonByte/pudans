import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { format } from "date-fns";
import {
  Activity,
  Ban,
  Loader2,
  Pencil,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Unlock,
} from "lucide-react";
import { toast } from "sonner";

import {
  DataTableFacetedFilter,
  DataTablePagination,
  DataTableToolbar,
  LongText,
} from "@/components/data-table";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { ConsoleStat } from "@/modules/workspace/console/components/console-stat";
import { formatAuditTime } from "@/modules/workspace/console/overview/lib/format";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { EditRiskRuleDialog } from "./components/edit-risk-rule-dialog";
import {
  type RiskBanItem,
  type RiskEventItem,
  type RiskRuleItem,
  type RiskUnbanItem,
  fetchRiskBans,
  fetchRiskEvents,
  fetchRiskRules,
  fetchRiskUnbans,
  resolveRiskApiError,
  riskRuleDescription,
  riskRuleDisplayName,
  unbanRiskSubject,
  updateRiskRule,
} from "./lib/api";

const ACTION_LABELS: Record<string, string> = {
  BLOCK: "拦截",
  ALERT: "仅告警",
};

const EVENT_LABELS: Record<string, string> = {
  RISK_BLOCKED: "拦截",
  RISK_ALERT: "告警",
};

const EMPTY_RULES: RiskRuleItem[] = [];

const EMPTY_EVENTS: RiskEventItem[] = [];

const EMPTY_BANS: RiskBanItem[] = [];

const EMPTY_UNBANS: RiskUnbanItem[] = [];

export function RiskView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canUpdate = allowedOperations.has("update");
  const canUnban = allowedOperations.has("unban");
  const [editTarget, setEditTarget] = useState<RiskRuleItem | null>(null);
  const [eventSearch, setEventSearch] = useState("");
  const [eventSceneFilters, setEventSceneFilters] = useState<string[]>([]);
  const [eventUserFilters, setEventUserFilters] = useState<string[]>([]);
  const [eventPage, setEventPage] = useState(1);
  const [eventSize, setEventSize] = useState(10);
  const [rulePage, setRulePage] = useState(1);
  const [ruleSize, setRuleSize] = useState(10);
  const [banPage, setBanPage] = useState(1);
  const [banSize, setBanSize] = useState(10);
  const [detailTarget, setDetailTarget] = useState<RiskEventItem | null>(null);

  const rulesQuery = useQuery({
    queryKey: ["risk-rules"],
    queryFn: fetchRiskRules,
    retry: false,
  });
  const eventsQuery = useQuery({
    queryKey: ["risk-events"],
    queryFn: () => fetchRiskEvents(7, 200),
    retry: false,
  });
  const bansQuery = useQuery({
    queryKey: ["risk-bans"],
    queryFn: fetchRiskBans,
    retry: false,
  });
  const unbansQuery = useQuery({
    queryKey: ["risk-unbans"],
    queryFn: () => fetchRiskUnbans(7, 200),
    retry: false,
  });

  const rules = rulesQuery.data ?? EMPTY_RULES;
  const events = eventsQuery.data ?? EMPTY_EVENTS;
  const bans = bansQuery.data ?? EMPTY_BANS;
  const unbans = unbansQuery.data ?? EMPTY_UNBANS;
  const enabledRules = rules.filter((rule) => rule.spec.enabled !== false);
  const sceneByKey = useMemo(() => {
    const labels = new Map<string, string>();
    for (const rule of rules) {
      labels.set(rule.metadata.name, riskRuleDisplayName(rule));
    }
    for (const event of events) {
      if (event.scene && !labels.has(event.scene)) {
        labels.set(event.scene, event.scene);
      }
    }
    return labels;
  }, [events, rules]);
  const sceneOptions = useMemo(
    () =>
      [...sceneByKey.entries()]
        .map(([value, label]) => ({ value, label }))
        .sort((left, right) => left.label.localeCompare(right.label, "zh-CN")),
    [sceneByKey],
  );
  const eventUserOptions = useMemo(() => {
    const values = new Set<string>();
    for (const event of events) {
      values.add(eventActorLabel(event));
    }
    return [...values]
      .sort((left, right) => left.localeCompare(right, "zh-CN"))
      .map((value) => ({ value, label: value }));
  }, [events]);
  const latestEventBySubject = useMemo(() => {
    const latest = new Map<string, RiskEventItem>();
    for (const event of events) {
      const key = `${event.scene}:${event.subject}`;
      if (!latest.has(key)) {
        latest.set(key, event);
      }
    }
    return latest;
  }, [events]);
  const activeBanKeys = useMemo(
    () => new Set(bans.map((ban) => `${ban.scene}:${ban.subject}`)),
    [bans],
  );
  const latestBlockByKey = useMemo(() => {
    const latest = new Map<string, string>();
    for (const event of events) {
      if (event.operation !== "RISK_BLOCKED") {
        continue;
      }
      const key = `${event.scene}:${event.subject}`;
      if (!latest.has(key)) {
        latest.set(key, event.auditId);
      }
    }
    return latest;
  }, [events]);

  const normalizedSearch = eventSearch.trim().toLowerCase();
  const filteredEvents = useMemo(() => {
    const sceneSet = new Set(eventSceneFilters);
    const userSet = new Set(eventUserFilters);
    return events.filter((event) => {
      if (sceneSet.size > 0 && !sceneSet.has(event.scene)) {
        return false;
      }
      if (userSet.size > 0 && !userSet.has(eventActorLabel(event))) {
        return false;
      }
      if (!normalizedSearch) {
        return true;
      }
      return [
        event.scene,
        event.subject,
        event.path,
        eventActorLabel(event),
        EVENT_LABELS[event.operation] ?? "",
      ]
        .filter(Boolean)
        .join("\n")
        .toLowerCase()
        .includes(normalizedSearch);
    });
  }, [eventSceneFilters, eventUserFilters, events, normalizedSearch]);
  const eventPageCount = Math.max(1, Math.ceil(filteredEvents.length / eventSize));
  const currentEventPage = Math.min(eventPage, eventPageCount);
  const pageEvents = filteredEvents.slice(
    (currentEventPage - 1) * eventSize,
    currentEventPage * eventSize,
  );
  const rulePageCount = Math.max(1, Math.ceil(rules.length / ruleSize));
  const currentRulePage = Math.min(rulePage, rulePageCount);
  const pageRules = rules.slice((currentRulePage - 1) * ruleSize, currentRulePage * ruleSize);
  const banPageCount = Math.max(1, Math.ceil(bans.length / banSize));
  const currentBanPage = Math.min(banPage, banPageCount);
  const pageBans = bans.slice((currentBanPage - 1) * banSize, currentBanPage * banSize);
  const isEventFiltered =
    eventSearch.trim().length > 0 || eventSceneFilters.length > 0 || eventUserFilters.length > 0;

  const toggleMutation = useMutation({
    mutationFn: ({ rule, enabled }: { rule: RiskRuleItem; enabled: boolean }) =>
      updateRiskRule({ ...rule, spec: { ...rule.spec, enabled } }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["risk-rules"] });
    },
    onError: (error) => toast.error(resolveRiskApiError(error)),
  });

  const unbanMutation = useMutation({
    mutationFn: ({ scene, subject }: { scene: string; subject: string }) =>
      unbanRiskSubject(scene, subject),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["risk-bans"] }),
        queryClient.invalidateQueries({ queryKey: ["risk-events"] }),
        queryClient.invalidateQueries({ queryKey: ["risk-unbans"] }),
      ]);
      toast.success("已解除风险封禁。");
    },
    onError: (error) => toast.error(resolveRiskApiError(error)),
  });

  const refreshing =
    rulesQuery.isFetching ||
    eventsQuery.isFetching ||
    bansQuery.isFetching ||
    unbansQuery.isFetching;
  const refresh = () => {
    void Promise.all([
      rulesQuery.refetch(),
      eventsQuery.refetch(),
      bansQuery.refetch(),
      unbansQuery.refetch(),
    ]);
  };
  const detailStatus = detailTarget
    ? resolveEventStatus(detailTarget, activeBanKeys, unbans, latestBlockByKey)
    : null;

  return (
    <ConsolePage title="风险">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-2xl font-bold tracking-tight">风险控制</h2>
          <Button type="button" variant="outline" onClick={refresh} disabled={refreshing}>
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <ConsoleStat label="生效规则" value={rules.length} icon={ShieldCheck} />
          <ConsoleStat label="启用规则" value={enabledRules.length} icon={ShieldAlert} />
          <ConsoleStat label="活跃封禁" value={bans.length} icon={Ban} />
          <ConsoleStat label="近 7 天事件" value={events.length} icon={Activity} />
        </div>

        <Tabs defaultValue="rules" className="gap-4">
          <TabsList>
            <TabsTrigger value="rules">风险规则</TabsTrigger>
            <TabsTrigger value="events">风险事件</TabsTrigger>
            <TabsTrigger value="bans">封禁对象</TabsTrigger>
          </TabsList>

          <TabsContent value="rules" className="flex flex-col gap-4">
            <ConsoleSection>
              {rulesQuery.isPending ? (
                <TableSkeleton rows={6} />
              ) : rulesQuery.isError ? (
                <ErrorState
                  title="无法加载风险规则"
                  message={resolveRiskApiError(rulesQuery.error)}
                  onRetry={() => void rulesQuery.refetch()}
                />
              ) : rules.length === 0 ? (
                <ConsoleEmpty>暂无可用的风险规则。</ConsoleEmpty>
              ) : (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="px-5">规则</TableHead>
                        <TableHead className="px-5">状态</TableHead>
                        <TableHead className="px-5">统计窗口</TableHead>
                        <TableHead className="px-5">阈值</TableHead>
                        <TableHead className="px-5">处置动作</TableHead>
                        <TableHead className="px-5 text-end">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {pageRules.map((rule) => {
                        const enabled = rule.spec.enabled !== false;
                        return (
                          <TableRow key={rule.metadata.name}>
                            <TableCell className="px-5 py-3">
                              <div className="min-w-0 space-y-1">
                                <LongText className="max-w-64 text-sm font-medium">
                                  {riskRuleDisplayName(rule)}
                                </LongText>
                                <LongText className="max-w-64 font-mono text-xs text-muted-foreground">
                                  {rule.metadata.name}
                                </LongText>
                                {riskRuleDescription(rule) ? (
                                  <LongText className="max-w-80 text-xs text-muted-foreground">
                                    {riskRuleDescription(rule)}
                                  </LongText>
                                ) : null}
                              </div>
                            </TableCell>
                            <TableCell className="px-5 py-3">
                              <div className="flex items-center gap-2">
                                <Switch
                                  checked={enabled}
                                  disabled={!canUpdate || toggleMutation.isPending}
                                  onCheckedChange={(checked) =>
                                    toggleMutation.mutate({ rule, enabled: checked })
                                  }
                                  aria-label={`${enabled ? "停用" : "启用"}${riskRuleDisplayName(rule)}`}
                                />
                                <span className="text-xs text-muted-foreground">
                                  {enabled ? "已启用" : "已停用"}
                                </span>
                              </div>
                            </TableCell>
                            <TableCell className="px-5 py-3 text-sm text-muted-foreground">
                              {rule.spec.windowSeconds ?? 0} 秒
                            </TableCell>
                            <TableCell className="px-5 py-3 text-sm text-muted-foreground">
                              {rule.spec.threshold ?? 0}
                            </TableCell>
                            <TableCell className="px-5 py-3">
                              <div className="flex flex-wrap items-center gap-2">
                                <Badge
                                  variant={rule.spec.action === "ALERT" ? "outline" : "secondary"}
                                >
                                  {ACTION_LABELS[rule.spec.action ?? ""] ?? rule.spec.action ?? "—"}
                                </Badge>
                                {rule.spec.action !== "ALERT" &&
                                (rule.spec.blockSeconds ?? 0) > 0 ? (
                                  <span className="text-xs text-muted-foreground">
                                    封禁 {rule.spec.blockSeconds} 秒
                                  </span>
                                ) : null}
                              </div>
                            </TableCell>
                            <TableCell className="px-5 py-3 text-end">
                              {canUpdate ? (
                                <Button
                                  type="button"
                                  variant="outline"
                                  size="sm"
                                  onClick={() => setEditTarget(rule)}
                                >
                                  <Pencil />
                                  编辑
                                </Button>
                              ) : (
                                <span className="text-sm text-muted-foreground">—</span>
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </>
              )}
            </ConsoleSection>
            <DataTablePagination
              currentPage={currentRulePage}
              pageCount={rulePageCount}
              pageSize={ruleSize}
              onPageChange={setRulePage}
              onPageSizeChange={(value) => {
                setRuleSize(value);
                setRulePage(1);
              }}
              className="mt-auto"
            />
          </TabsContent>

          <TabsContent value="events" className="flex flex-col gap-4">
            <DataTableToolbar
              searchPlaceholder="搜索场景、对象或路径..."
              searchValue={eventSearch}
              onSearchChange={(value) => {
                setEventSearch(value);
                setEventPage(1);
              }}
              filters={
                <>
                  <DataTableFacetedFilter
                    title="场景"
                    options={sceneOptions}
                    selected={eventSceneFilters}
                    onSelectedChange={(values) => {
                      setEventSceneFilters(values);
                      setEventPage(1);
                    }}
                  />
                  {eventUserOptions.length > 0 ? (
                    <DataTableFacetedFilter
                      title="触发用户"
                      options={eventUserOptions}
                      selected={eventUserFilters}
                      onSelectedChange={(values) => {
                        setEventUserFilters(values);
                        setEventPage(1);
                      }}
                    />
                  ) : null}
                </>
              }
              isFiltered={isEventFiltered}
              onReset={() => {
                setEventSearch("");
                setEventSceneFilters([]);
                setEventUserFilters([]);
                setEventPage(1);
              }}
            />
            <ConsoleSection>
              {eventsQuery.isPending ? (
                <TableSkeleton rows={6} />
              ) : eventsQuery.isError ? (
                <ErrorState
                  title="无法加载风险事件"
                  message={resolveRiskApiError(eventsQuery.error)}
                  onRetry={() => void eventsQuery.refetch()}
                />
              ) : filteredEvents.length === 0 ? (
                <ConsoleEmpty>
                  {events.length === 0 ? "最近 7 天没有风险事件。" : "没有匹配的风险事件。"}
                </ConsoleEmpty>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="px-5">时间</TableHead>
                      <TableHead className="px-5">事件</TableHead>
                      <TableHead className="px-5">状态</TableHead>
                      <TableHead className="px-5">场景</TableHead>
                      <TableHead className="px-5">触发用户</TableHead>
                      <TableHead className="px-5">对象</TableHead>
                      <TableHead className="px-5">路径</TableHead>
                      <TableHead className="px-5">次数</TableHead>
                      <TableHead className="px-5">结果</TableHead>
                      <TableHead className="px-5 text-end">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {pageEvents.map((event) => {
                      const status = resolveEventStatus(
                        event,
                        activeBanKeys,
                        unbans,
                        latestBlockByKey,
                      );

                      return (
                        <TableRow
                          key={event.auditId}
                          className="cursor-pointer"
                          onClick={() => setDetailTarget(event)}
                        >
                          <TableCell className="px-5 py-3 text-sm text-muted-foreground">
                            {formatAuditTime(event.timestamp)}
                          </TableCell>
                          <TableCell className="px-5 py-3">
                            <Badge
                              variant={
                                event.operation === "RISK_BLOCKED" ? "destructive" : "secondary"
                              }
                            >
                              {EVENT_LABELS[event.operation] ?? event.operation}
                            </Badge>
                          </TableCell>
                          <TableCell className="px-5 py-3">
                            <Badge variant={status.variant}>{status.label}</Badge>
                          </TableCell>
                          <TableCell className="px-5 py-3 text-sm">
                            {sceneByKey.get(event.scene) ?? event.scene}
                          </TableCell>
                          <TableCell className="px-5 py-3 text-sm">
                            {eventActorLabel(event)}
                          </TableCell>
                          <TableCell className="px-5 py-3 font-mono text-xs">
                            {event.subject || "—"}
                          </TableCell>
                          <TableCell className="px-5 py-3">
                            <LongText className="max-w-64 font-mono text-xs text-muted-foreground">
                              {event.path || "—"}
                            </LongText>
                          </TableCell>
                          <TableCell className="px-5 py-3 text-sm text-muted-foreground">
                            {event.count}
                          </TableCell>
                          <TableCell className="px-5 py-3">
                            <Badge variant="outline">{event.result || "—"}</Badge>
                          </TableCell>
                          <TableCell className="px-5 py-3 text-end">
                            {canUnban && status.key === "active" ? (
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                disabled={unbanMutation.isPending}
                                onClick={(clickEvent) => {
                                  clickEvent.stopPropagation();
                                  unbanMutation.mutate({
                                    scene: event.scene,
                                    subject: event.subject,
                                  });
                                }}
                              >
                                <Unlock />
                                解除封禁
                              </Button>
                            ) : (
                              <span className="text-sm text-muted-foreground">—</span>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              )}
            </ConsoleSection>
            <DataTablePagination
              currentPage={currentEventPage}
              pageCount={eventPageCount}
              pageSize={eventSize}
              onPageChange={setEventPage}
              onPageSizeChange={(value) => {
                setEventSize(value);
                setEventPage(1);
              }}
              disabled={eventsQuery.isFetching}
              className="mt-auto"
            />
          </TabsContent>

          <TabsContent value="bans" className="flex flex-col gap-4">
            <ConsoleSection>
              {bansQuery.isPending ? (
                <TableSkeleton rows={4} />
              ) : bansQuery.isError ? (
                <ErrorState
                  title="无法加载封禁对象"
                  message={resolveRiskApiError(bansQuery.error)}
                  onRetry={() => void bansQuery.refetch()}
                />
              ) : bans.length === 0 ? (
                <ConsoleEmpty>当前没有活跃的风险封禁。</ConsoleEmpty>
              ) : (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="px-5">场景</TableHead>
                        <TableHead className="px-5">对象</TableHead>
                        <TableHead className="px-5">触发用户</TableHead>
                        <TableHead className="px-5">剩余时间</TableHead>
                        <TableHead className="px-5 text-end">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {pageBans.map((ban) => (
                        <TableRow key={`${ban.scene}:${ban.subject}`}>
                          <TableCell className="px-5 py-3 text-sm">
                            {sceneByKey.get(ban.scene) ?? ban.scene}
                          </TableCell>
                          <TableCell className="px-5 py-3 font-mono text-xs">
                            {ban.subject}
                          </TableCell>
                          <TableCell className="px-5 py-3 text-sm">
                            {eventActorLabel(
                              latestEventBySubject.get(`${ban.scene}:${ban.subject}`),
                            )}
                          </TableCell>
                          <TableCell className="px-5 py-3 text-sm text-muted-foreground">
                            {formatRemaining(ban.expiresInSeconds)}
                          </TableCell>
                          <TableCell className="px-5 py-3 text-end">
                            {canUnban ? (
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                disabled={unbanMutation.isPending}
                                onClick={() =>
                                  unbanMutation.mutate({
                                    scene: ban.scene,
                                    subject: ban.subject,
                                  })
                                }
                              >
                                <Unlock />
                                解除封禁
                              </Button>
                            ) : (
                              <span className="text-sm text-muted-foreground">—</span>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </>
              )}
            </ConsoleSection>
            <DataTablePagination
              currentPage={currentBanPage}
              pageCount={banPageCount}
              pageSize={banSize}
              onPageChange={setBanPage}
              onPageSizeChange={(value) => {
                setBanSize(value);
                setBanPage(1);
              }}
              className="mt-auto"
            />
          </TabsContent>
        </Tabs>
      </div>

      {editTarget ? (
        <EditRiskRuleDialog rule={editTarget} onClose={() => setEditTarget(null)} />
      ) : null}

      <RiskEventDetailDialog
        event={detailTarget}
        status={detailStatus}
        sceneLabel={detailTarget ? (sceneByKey.get(detailTarget.scene) ?? detailTarget.scene) : ""}
        canUnban={canUnban}
        isUnbanning={unbanMutation.isPending}
        onUnban={() => {
          if (detailTarget) {
            unbanMutation.mutate({
              scene: detailTarget.scene,
              subject: detailTarget.subject,
            });
          }
        }}
        onClose={() => setDetailTarget(null)}
      />
    </ConsolePage>
  );
}

type RiskEventDetailDialogProps = {
  event: RiskEventItem | null;
  status: RiskEventStatus | null;
  sceneLabel: string;
  canUnban: boolean;
  isUnbanning: boolean;
  onUnban: () => void;
  onClose: () => void;
};

function RiskEventDetailDialog({
  event,
  status,
  sceneLabel,
  canUnban,
  isUnbanning,
  onUnban,
  onClose,
}: RiskEventDetailDialogProps) {
  return (
    <Dialog
      open={Boolean(event)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>风险事件详情</DialogTitle>
          <DialogDescription>
            {event ? `${formatExactTime(event.timestamp)} · ${event.auditId}` : ""}
          </DialogDescription>
        </DialogHeader>
        {event ? (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={event.operation === "RISK_BLOCKED" ? "destructive" : "secondary"}>
                {EVENT_LABELS[event.operation] ?? event.operation}
              </Badge>
              {status ? <Badge variant={status.variant}>{status.label}</Badge> : null}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <InfoRow label="触发用户" value={eventActorLabel(event)} />
              <InfoRow
                label="对象"
                value={<span className="font-mono text-xs">{event.subject || "—"}</span>}
              />
              <InfoRow label="场景" value={`${sceneLabel}（${event.scene}）`} />
              <InfoRow label="请求方法" value={event.requestMethod || "—"} />
              <InfoRow
                label="路径"
                value={<span className="font-mono text-xs">{event.path || "—"}</span>}
                className="sm:col-span-2"
              />
              <InfoRow label="触发次数" value={event.count} />
              <InfoRow label="结果" value={event.result || "—"} />
              {event.blockSeconds > 0 ? (
                <InfoRow label="封禁时长" value={`${event.blockSeconds} 秒`} />
              ) : null}
              {status?.banUntil ? (
                <InfoRow label="封禁到期" value={formatExactTime(status.banUntil)} />
              ) : null}
              {status?.unbanAt ? (
                <InfoRow label="解禁时间" value={formatExactTime(status.unbanAt)} />
              ) : null}
              <InfoRow
                label="审计 ID"
                value={<span className="font-mono text-xs">{event.auditId}</span>}
              />
              {event.traceId ? (
                <InfoRow
                  label="Trace ID"
                  value={<span className="font-mono text-xs">{event.traceId}</span>}
                />
              ) : null}
            </div>
          </>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            关闭
          </Button>
          {event && status?.key === "active" && canUnban ? (
            <Button type="button" variant="destructive" disabled={isUnbanning} onClick={onUnban}>
              {isUnbanning ? <Loader2 className="animate-spin" /> : <Unlock />}
              解除封禁
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function TableSkeleton({ rows }: { rows: number }) {
  return (
    <div className="space-y-3 p-5">
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-10 w-full" />
      ))}
    </div>
  );
}

function ErrorState({
  title,
  message,
  onRetry,
}: {
  title: string;
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center gap-3 p-6 text-center">
      <ShieldAlert className="size-8 text-muted-foreground" />
      <div className="space-y-1">
        <p className="text-sm font-medium">{title}</p>
        <p className="text-xs text-muted-foreground">{message}</p>
      </div>
      <Button variant="outline" size="sm" onClick={onRetry}>
        重试
      </Button>
    </div>
  );
}

function eventActorLabel(event: RiskEventItem | undefined): string {
  if (!event) {
    return "—";
  }
  return event.username || (event.uid && event.uid !== "anonymous" ? event.uid : "匿名");
}

type RiskEventStatusKey = "active" | "unbanned" | "expired" | "alert";

type RiskEventStatus = {
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

function resolveEventStatus(
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

function formatExactTime(value?: string): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : format(date, "yyyy-MM-dd HH:mm:ss");
}

function formatRemaining(seconds: number): string {
  if (seconds <= 0) {
    return "已过期";
  }
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  return `${Math.ceil(seconds / 60)} 分钟`;
}
