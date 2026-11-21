import { ConsolePage } from "@/modules/workspace/console/components/console-page";

import { UsagePlanCard } from "./components/plan-card";

const PAGE_TITLE = "套餐与重置券";

export function MyPlanView() {
  return (
    <ConsolePage title={PAGE_TITLE} description="当前账号的套餐窗口用量、限流与重置券余额。">
      <div className="flex flex-1 flex-col gap-5">
        <div className="space-y-1">
          <h2 className="text-2xl font-bold tracking-tight">{PAGE_TITLE}</h2>
          <p className="text-xs text-muted-foreground">
            套餐窗口按北京时间计算；使用重置券后新窗口以服务端计算结果为准。
          </p>
        </div>
        <UsagePlanCard />
      </div>
    </ConsolePage>
  );
}
