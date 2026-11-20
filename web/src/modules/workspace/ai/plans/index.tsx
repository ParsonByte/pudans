import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { UsagePageNoPermission } from "@/modules/workspace/ai/usage/components/usage-state";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { BillingRatiosSection } from "./components/billing-ratios-section";
import { PlanBindingsSection } from "./components/plan-bindings-section";
import { PlansSection } from "./components/plans-section";
import { VoucherGrantSection } from "./components/voucher-grant-section";

const PAGE_TITLE = "套餐与计费";

export function AiPlansView() {
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");

  if (!canView) {
    return <UsagePageNoPermission title={PAGE_TITLE} />;
  }

  return (
    <ConsolePage title={PAGE_TITLE}>
      <div className="flex flex-1 flex-col gap-5">
        <div className="space-y-1">
          <h2 className="text-2xl font-bold tracking-tight">{PAGE_TITLE}</h2>
          <p className="text-xs text-muted-foreground">
            管理 AI 套餐定义、用户组批量分配、计费权重与重置券发放；所有窗口按北京时间计算。
          </p>
        </div>

        <Tabs defaultValue="plans" className="gap-4">
          <TabsList>
            <TabsTrigger value="plans">套餐定义</TabsTrigger>
            <TabsTrigger value="bindings">批量分配</TabsTrigger>
            <TabsTrigger value="ratios">计费比例</TabsTrigger>
            <TabsTrigger value="vouchers">重置券发放</TabsTrigger>
          </TabsList>
          <TabsContent value="plans" className="space-y-5">
            <PlansSection
              canCreate={allowedOperations.has("create")}
              canUpdate={allowedOperations.has("update")}
              canDelete={allowedOperations.has("delete")}
            />
          </TabsContent>
          <TabsContent value="bindings" className="space-y-5">
            <PlanBindingsSection
              canAssign={allowedOperations.has("assign")}
              canDelete={allowedOperations.has("delete")}
            />
          </TabsContent>
          <TabsContent value="ratios" className="space-y-5">
            <BillingRatiosSection canEdit={allowedOperations.has("ratios")} />
          </TabsContent>
          <TabsContent value="vouchers" className="space-y-5">
            <VoucherGrantSection canGrant={allowedOperations.has("grant")} />
          </TabsContent>
        </Tabs>
      </div>
    </ConsolePage>
  );
}
