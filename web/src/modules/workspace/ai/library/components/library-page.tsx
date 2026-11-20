import type { ReactNode } from "react";

import { Loader2, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";

type LibraryPageProps = {
  title: string;
  heading: string;
  description: string;
  count: number;
  countUnit: string;
  refreshing: boolean;
  onRefresh: () => void;
  actions?: ReactNode;
  children: ReactNode;
};

export function LibraryPage({
  title,
  heading,
  description,
  count,
  countUnit,
  refreshing,
  onRefresh,
  actions,
  children,
}: LibraryPageProps) {
  return (
    <ConsolePage title={title}>
      <div className="flex flex-1 flex-col gap-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-base font-semibold">{heading}</h2>
            <p className="max-w-2xl text-xs text-muted-foreground">
              {count} {countUnit} · {description}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onRefresh}
              disabled={refreshing}
            >
              {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            {actions}
          </div>
        </div>
        {children}
      </div>
    </ConsolePage>
  );
}
