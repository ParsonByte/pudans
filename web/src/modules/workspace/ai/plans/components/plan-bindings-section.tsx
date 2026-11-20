import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, Trash2, UserPlus } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { beijingDateTimeInputToIso, formatBeijingDateTime } from "@/lib/date";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { useUserGroups } from "@/modules/workspace/lib/user-groups";

import { UsageErrorState } from "../../usage/components/usage-state";
import {
  type AiPlanBindingItem,
  type PlanSubjectKind,
  bulkBindPlan,
  deletePlanBinding,
  fetchAllPlanBindings,
  fetchAllPlans,
  resolvePlanApiError,
  resolvePlanBinding,
  resolvePlanSubjects,
} from "../lib/api";
import { planSubjectKindLabel } from "../lib/model";
import { PlanDeleteDialog } from "./plan-delete-dialog";
import { SubjectPicker } from "./subject-picker";

const PRIORITY_PATTERN = /^\d+$/;

type PlanBindingsSectionProps = {
  canAssign: boolean;
  canDelete: boolean;
};

export function PlanBindingsSection({ canAssign, canDelete }: PlanBindingsSectionProps) {
  const queryClient = useQueryClient();
  const plansQuery = useQuery({
    queryKey: ["ai-plans", "all"],
    queryFn: fetchAllPlans,
    retry: false,
  });
  const bindingsQuery = useQuery({
    queryKey: ["ai-plan-bindings", "all"],
    queryFn: fetchAllPlanBindings,
    retry: false,
  });
  const groupsQuery = useUserGroups();

  const [kind, setKind] = useState<PlanSubjectKind>("UserGroup");
  const [subjects, setSubjects] = useState<string[]>([]);
  const [planName, setPlanName] = useState("");
  const [priority, setPriority] = useState("0");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [effectiveTo, setEffectiveTo] = useState("");
  const [replaceExisting, setReplaceExisting] = useState(true);
  const [previewUser, setPreviewUser] = useState<string[]>([]);
  const [deleting, setDeleting] = useState<AiPlanBindingItem | null>(null);

  const plans = useMemo(() => plansQuery.data ?? [], [plansQuery.data]);
  const planValue = planName || plans[0]?.metadata.name || "";
  const planNames = useMemo(
    () => new Map(plans.map((plan) => [plan.metadata.name, plan.spec.displayName])),
    [plans],
  );
  const groupNames = useMemo(
    () =>
      new Map(
        (groupsQuery.data ?? []).map((group) => [
          group.metadata.name,
          group.displayName || group.metadata.name,
        ]),
      ),
    [groupsQuery.data],
  );

  const invalidateBindings = () => {
    void queryClient.invalidateQueries({ queryKey: ["ai-plan-bindings"] });
    void queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
  };

  const bulkMutation = useMutation({
    mutationFn: async () => {
      if (!planValue) {
        throw new Error("请选择要分配的套餐。");
      }
      if (subjects.length === 0) {
        throw new Error("请选择要分配的用户组或用户。");
      }
      if (!PRIORITY_PATTERN.test(priority.trim())) {
        throw new Error("优先级必须为非负整数。");
      }
      const resolved = await resolvePlanSubjects(kind, subjects);
      const from = effectiveFrom ? beijingDateTimeInputToIso(effectiveFrom) : undefined;
      const to = effectiveTo ? beijingDateTimeInputToIso(effectiveTo) : undefined;
      if (from && to && new Date(from) >= new Date(to)) {
        throw new Error("生效开始时间必须早于结束时间。");
      }
      return bulkBindPlan({
        plan: planValue,
        subjects: resolved,
        priority: Number(priority.trim()),
        replaceExisting,
        effectiveFrom: from ?? null,
        effectiveTo: to ?? null,
      });
    },
    onSuccess: (result) => {
      toast.success(`已提交 ${result.bindings?.length ?? subjects.length} 个套餐绑定。`);
      setSubjects([]);
      setEffectiveFrom("");
      setEffectiveTo("");
      invalidateBindings();
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  const previewMutation = useMutation({
    mutationFn: async () => {
      if (previewUser.length === 0) {
        throw new Error("请选择要预览的用户。");
      }
      const resolved = await resolvePlanSubjects("User", [previewUser[0]]);
      const uid = resolved[0]?.name;
      if (!uid) {
        throw new Error("无法解析所选用户的 uid。");
      }
      return resolvePlanBinding(uid);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (binding: AiPlanBindingItem) => deletePlanBinding(binding.metadata.name),
    onSuccess: () => {
      toast.success("套餐绑定已删除。");
      setDeleting(null);
      invalidateBindings();
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  const bindings = bindingsQuery.data ?? [];

  return (
    <div className="flex flex-col gap-5">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <ConsoleSection>
          <ConsoleSectionHeader
            title="批量分配"
            description="按用户组或用户批量绑定套餐；同一主体重复分配会更新套餐与优先级。"
          />
          <div className="space-y-4 p-5">
            <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_120px]">
              <label className="space-y-1.5 text-sm">
                <span className="text-muted-foreground">套餐</span>
                <Select
                  value={planValue}
                  onValueChange={setPlanName}
                  disabled={!canAssign || plans.length === 0}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="选择套餐" />
                  </SelectTrigger>
                  <SelectContent>
                    {plans.map((plan) => (
                      <SelectItem key={plan.metadata.name} value={plan.metadata.name}>
                        {plan.spec.displayName || plan.metadata.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="text-muted-foreground">优先级</span>
                <Input
                  value={priority}
                  onChange={(event) => setPriority(event.target.value)}
                  inputMode="numeric"
                  disabled={!canAssign}
                />
              </label>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="space-y-1.5 text-sm">
                <span className="text-muted-foreground">生效开始（北京时间，可空）</span>
                <Input
                  type="datetime-local"
                  value={effectiveFrom}
                  onChange={(event) => setEffectiveFrom(event.target.value)}
                  disabled={!canAssign}
                />
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="text-muted-foreground">生效结束（北京时间，可空）</span>
                <Input
                  type="datetime-local"
                  value={effectiveTo}
                  onChange={(event) => setEffectiveTo(event.target.value)}
                  disabled={!canAssign}
                />
              </label>
            </div>
            <div className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">分配主体</span>
              <SubjectPicker
                kind={kind}
                onKindChange={setKind}
                value={subjects}
                onChange={setSubjects}
                disabled={!canAssign}
              />
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-sm">
                <Checkbox
                  id="ai-plan-replace-existing"
                  checked={replaceExisting}
                  onCheckedChange={(checked) => setReplaceExisting(checked === true)}
                  disabled={!canAssign}
                />
                <label htmlFor="ai-plan-replace-existing">替换主体已有的有效绑定</label>
              </div>
              <Button
                type="button"
                size="sm"
                disabled={!canAssign || bulkMutation.isPending || plans.length === 0}
                onClick={() => bulkMutation.mutate()}
              >
                <UserPlus />
                批量分配
              </Button>
            </div>
            {plans.length === 0 && !plansQuery.isPending ? (
              <p className="text-xs text-muted-foreground">请先在「套餐定义」中创建套餐。</p>
            ) : null}
            {bindingsQuery.isError ? (
              <p className="text-xs text-destructive">无法加载套餐绑定，请稍后重试。</p>
            ) : null}
          </div>
        </ConsoleSection>

        <ConsoleSection>
          <ConsoleSectionHeader
            title="实际命中预览"
            description="按用户解析直接绑定、用户组优先级与默认套餐，预览运行时实际命中的套餐。"
          />
          <div className="space-y-4 p-5">
            <SubjectPicker
              kind="User"
              onKindChange={() => undefined}
              value={previewUser}
              onChange={setPreviewUser}
              multiple={false}
              lockKind
            />
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={previewMutation.isPending || previewUser.length === 0}
              onClick={() => previewMutation.mutate()}
            >
              <Search />
              预览命中套餐
            </Button>
            {previewMutation.isError ? (
              <p className="text-xs text-destructive">
                {resolvePlanApiError(previewMutation.error)}
              </p>
            ) : previewMutation.data ? (
              <div className="space-y-2 border p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium">
                    {previewMutation.data.displayName ||
                      previewMutation.data.planName ||
                      "未命中套餐"}
                  </span>
                  {previewMutation.data.planName ? (
                    <Badge variant="outline" className="font-mono text-[10px]">
                      {previewMutation.data.planName}
                    </Badge>
                  ) : null}
                </div>
                <p className="text-xs text-muted-foreground">
                  命中来源：{previewMutation.data.matchedBy || "—"}
                </p>
                {!previewMutation.data.planName ? (
                  <p className="text-xs text-muted-foreground">
                    该用户未命中任何套餐；若计费策略为拒绝，请求将被拒绝。
                  </p>
                ) : null}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">选择用户后预览其实际命中套餐。</p>
            )}
          </div>
        </ConsoleSection>
      </div>

      <ConsoleSection>
        <ConsoleSectionHeader
          title="当前套餐绑定"
          description={
            bindingsQuery.isPending
              ? "正在加载绑定…"
              : `共 ${bindings.length} 条绑定，直接用户绑定优先于用户组绑定。`
          }
          action={
            bindingsQuery.isError ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => void bindingsQuery.refetch()}
              >
                重试
              </Button>
            ) : null
          }
        />
        {bindingsQuery.isError ? (
          <UsageErrorState
            title="无法加载套餐绑定"
            message={resolvePlanApiError(bindingsQuery.error)}
            onRetry={() => void bindingsQuery.refetch()}
          />
        ) : bindingsQuery.isPending ? (
          <ConsoleEmpty>正在加载套餐绑定…</ConsoleEmpty>
        ) : bindings.length === 0 ? (
          <ConsoleEmpty>暂无套餐绑定。</ConsoleEmpty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-5">套餐</TableHead>
                <TableHead className="px-5">主体</TableHead>
                <TableHead className="px-5 text-end">优先级</TableHead>
                <TableHead className="px-5">生效时间</TableHead>
                <TableHead className="px-5 text-end">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {bindings.map((binding) => (
                <TableRow key={binding.metadata.name}>
                  <TableCell className="px-5 py-3">
                    <p className="text-sm font-medium">
                      {planNames.get(binding.spec.plan) || binding.spec.plan}
                    </p>
                    <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">
                      {binding.spec.plan}
                    </p>
                  </TableCell>
                  <TableCell className="px-5 py-3">
                    <Badge variant="secondary">
                      {planSubjectKindLabel(binding.spec.subject.kind)}
                    </Badge>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {binding.spec.subject.kind === "UserGroup"
                        ? groupNames.get(binding.spec.subject.name) || binding.spec.subject.name
                        : binding.spec.subject.name}
                    </p>
                  </TableCell>
                  <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                    {binding.spec.priority ?? 0}
                  </TableCell>
                  <TableCell className="px-5 py-3 text-xs text-muted-foreground">
                    {binding.spec.effectiveFrom || binding.spec.effectiveTo
                      ? `${binding.spec.effectiveFrom ? formatBeijingDateTime(binding.spec.effectiveFrom) : "立即"} 至 ${
                          binding.spec.effectiveTo
                            ? formatBeijingDateTime(binding.spec.effectiveTo)
                            : "长期"
                        }`
                      : "长期有效"}
                  </TableCell>
                  <TableCell className="px-5 py-3 text-end">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={!canDelete}
                      onClick={() => setDeleting(binding)}
                    >
                      <Trash2 />
                      删除
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </ConsoleSection>

      <PlanDeleteDialog
        open={deleting != null}
        title="删除套餐绑定"
        description={
          deleting
            ? `将删除主体「${deleting.spec.subject.name}」的「${deleting.spec.plan}」套餐绑定，后续请求按其他绑定或默认策略解析。`
            : ""
        }
        pending={deleteMutation.isPending}
        onOpenChange={(open) => {
          if (!open) {
            setDeleting(null);
          }
        }}
        onConfirm={() => {
          if (deleting) {
            deleteMutation.mutate(deleting);
          }
        }}
      />
    </div>
  );
}
