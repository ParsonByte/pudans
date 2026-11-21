import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  Ban,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  Unlock,
} from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import {
  DataTableFacetedFilter,
  DataTablePagination,
  DataTableToolbar,
  LongText,
} from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { ConsolePage, ConsoleSection } from "@/modules/workspace/console/components/console-page";
import { ConsoleStat } from "@/modules/workspace/console/components/console-stat";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";
import { formatAuditTime } from "@/modules/workspace/console/overview/lib/format";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { CreateRiskRuleDialog } from "./components/create-risk-rule-dialog";
import { EditRiskRuleDialog } from "./components/edit-risk-rule-dialog";
import { RiskEventDetailDialog } from "./components/risk-event-detail-dialog";
import {
  type RiskBanItem,
  type RiskEventItem,
  type RiskRuleItem,
  type RiskUnbanItem,
  deleteRiskRule,
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
import { EVENT_LABELS, eventActorLabel, resolveEventStatus } from "./lib/risk-events";

const ACTION_LABELS: Record<string, string> = {
  BLOCK: "拦截",
  ALERT: "仅告警",
};

const EMPTY_RULES: RiskRuleItem[] = [];

const EMPTY_EVENTS: RiskEventItem[] = [];

const EMPTY_BANS: RiskBanItem[] = [];

const EMPTY_UNBANS: RiskUnbanItem[] = [];

export function RiskView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canUnban = allowedOperations.has("unban");
  const [editTarget, setEditTarget] = useState<RiskRuleItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<RiskRuleItem | null>(null);
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

  const deleteMutation = useMutation({
    mutationFn: (rule: RiskRuleItem) => deleteRiskRule(rule.metadata.name),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["risk-rules"] });
      await queryClient.invalidateQueries({ queryKey: ["risk-events"] });
      setDeleteTarget(null);
      toast.success("风险规则已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveRiskApiError(error));
    },
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
        <div className="flex flex-wrap items-center justify-end gap-2">
          {canCreate ? (
            <Button type="button" onClick={() => setCreateOpen(true)}>
              <Plus />
              新建规则
            </Button>
          ) : null}
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

        <Tabs defaultValue="rules" className="flex flex-1 flex-col gap-4">
          <TabsList>
            <TabsTrigger value="rules">风险规则</TabsTrigger>
            <TabsTrigger value="events">风险事件</TabsTrigger>
            <TabsTrigger value="bans">封禁对象</TabsTrigger>
          </TabsList>

          <TabsContent value="rules" className="flex flex-1 flex-col gap-4">
            <ConsoleSection>
              {rulesQuery.isPending ? (
                <Table>
                  <TableBody>
                    <ConsoleTableSkeleton columns={6} rows={6} cellClassName="px-5 py-3" />
                  </TableBody>
                </Table>
              ) : rulesQuery.isError ? (
                <ConsoleState
                  variant="error"
                  title="无法加载风险规则"
                  description={resolveRiskApiError(rulesQuery.error)}
                  onRetry={() => void rulesQuery.refetch()}
                />
              ) : rules.length === 0 ? (
                <ConsoleState variant="empty" title="暂无可用的风险规则。" />
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
                              <div className="flex items-center justify-end gap-1">
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
                                ) : null}
                                {canDelete ? (
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon-sm"
                                    aria-label={`删除 ${riskRuleDisplayName(rule)}`}
                                    onClick={() => setDeleteTarget(rule)}
                                  >
                                    <Trash2 />
                                  </Button>
                                ) : null}
                                {!canUpdate && !canDelete ? (
                                  <span className="text-sm text-muted-foreground">—</span>
                                ) : null}
                              </div>
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

          <TabsContent value="events" className="flex flex-1 flex-col gap-4">
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
                <Table>
                  <TableBody>
                    <ConsoleTableSkeleton columns={10} rows={6} cellClassName="px-5 py-3" />
                  </TableBody>
                </Table>
              ) : eventsQuery.isError ? (
                <ConsoleState
                  variant="error"
                  title="无法加载风险事件"
                  description={resolveRiskApiError(eventsQuery.error)}
                  onRetry={() => void eventsQuery.refetch()}
                />
              ) : filteredEvents.length === 0 ? (
                <ConsoleState
                  variant="empty"
                  title={events.length === 0 ? "最近 7 天没有风险事件。" : "没有匹配的风险事件。"}
                />
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

          <TabsContent value="bans" className="flex flex-1 flex-col gap-4">
            <ConsoleSection>
              {bansQuery.isPending ? (
                <Table>
                  <TableBody>
                    <ConsoleTableSkeleton columns={5} rows={4} cellClassName="px-5 py-3" />
                  </TableBody>
                </Table>
              ) : bansQuery.isError ? (
                <ConsoleState
                  variant="error"
                  title="无法加载封禁对象"
                  description={resolveRiskApiError(bansQuery.error)}
                  onRetry={() => void bansQuery.refetch()}
                />
              ) : bans.length === 0 ? (
                <ConsoleState variant="empty" title="当前没有活跃的风险封禁。" />
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

      {createOpen ? <CreateRiskRuleDialog onClose={() => setCreateOpen(false)} /> : null}

      {editTarget ? (
        <EditRiskRuleDialog rule={editTarget} onClose={() => setEditTarget(null)} />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除风险规则"
        desc={
          deleteTarget
            ? `确定删除「${riskRuleDisplayName(deleteTarget)}」吗？删除后该场景不再参与风险判定。`
            : ""
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? "删除中…" : "确认删除"}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />

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

function formatRemaining(seconds: number): string {
  if (seconds <= 0) {
    return "已过期";
  }
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  return `${Math.ceil(seconds / 60)} 分钟`;
}
