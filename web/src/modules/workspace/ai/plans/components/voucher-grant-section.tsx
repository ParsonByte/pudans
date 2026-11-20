import { useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Ticket } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  PLAN_VOUCHER_KIND_OPTIONS,
  planVoucherKindLabel,
} from "@/modules/workspace/ai/usage/lib/model";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import type { PlanVoucherKind } from "../../usage/lib/api";
import {
  type PlanSubjectKind,
  grantPlanVouchers,
  resolvePlanApiError,
  resolvePlanSubjects,
} from "../lib/api";
import { SubjectPicker } from "./subject-picker";

const COUNT_PATTERN = /^[1-9]\d*$/;

type VoucherGrantSectionProps = {
  canGrant: boolean;
};

export function VoucherGrantSection({ canGrant }: VoucherGrantSectionProps) {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<PlanSubjectKind>("UserGroup");
  const [subjects, setSubjects] = useState<string[]>([]);
  const [voucherKind, setVoucherKind] = useState<PlanVoucherKind>("FIVE_HOUR_RESET");
  const [count, setCount] = useState("1");

  const mutation = useMutation({
    mutationFn: async () => {
      if (subjects.length === 0) {
        throw new Error("请选择要发放的用户组或用户。");
      }
      if (!COUNT_PATTERN.test(count.trim())) {
        throw new Error("发放数量必须为正整数。");
      }
      const resolved = await resolvePlanSubjects(kind, subjects);
      return grantPlanVouchers({
        kind: voucherKind,
        count: Number(count.trim()),
        userIds: kind === "User" ? resolved.map((subject) => subject.name) : undefined,
        groupNames: kind === "UserGroup" ? resolved.map((subject) => subject.name) : undefined,
      });
    },
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
      const grantedKind = result.kind ?? voucherKind;
      toast.success(
        `已为 ${result.userIds?.length ?? 0} 个用户发放 ${result.count ?? count} 张${planVoucherKindLabel(grantedKind)}。`,
      );
      setSubjects([]);
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="重置券发放"
        description="为选定用户或用户组成员追加当前周期的 5 小时 / 周重置券；用户组成员在发放时解析。"
      />
      <div className="grid gap-4 p-5 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <div className="space-y-4">
          <div className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">发放主体</span>
            <SubjectPicker
              kind={kind}
              onKindChange={setKind}
              value={subjects}
              onChange={setSubjects}
              disabled={!canGrant}
            />
          </div>
          <div className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">重置券类型</span>
            <ToggleGroup
              type="single"
              variant="outline"
              size="sm"
              value={voucherKind}
              onValueChange={(value) => {
                if (value === "FIVE_HOUR_RESET" || value === "WEEKLY_RESET") {
                  setVoucherKind(value);
                }
              }}
              disabled={!canGrant}
            >
              {PLAN_VOUCHER_KIND_OPTIONS.map((option) => (
                <ToggleGroupItem key={option.value} value={option.value}>
                  {option.label}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
            <p className="text-xs text-muted-foreground">
              {PLAN_VOUCHER_KIND_OPTIONS.find((option) => option.value === voucherKind)?.hint}
            </p>
          </div>
        </div>
        <div className="space-y-4">
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">每主体发放数量</span>
            <Input
              value={count}
              onChange={(event) => setCount(event.target.value)}
              inputMode="numeric"
              disabled={!canGrant}
            />
          </label>
          <p className="text-xs text-muted-foreground">
            用户组会在发放时展开为组内成员，每人各获得指定张数。
          </p>
          <div className="flex justify-end">
            <Button
              type="button"
              size="sm"
              disabled={!canGrant || mutation.isPending}
              onClick={() => mutation.mutate()}
            >
              <Ticket />
              发放重置券
            </Button>
          </div>
        </div>
      </div>
    </ConsoleSection>
  );
}
