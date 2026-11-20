import { Card, CardContent, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

import type { UsageKpiItem } from "../lib/model";

type UsageKpiCardsProps = {
  items: UsageKpiItem[];
  loading?: boolean;
  skeletonCount?: number;
};

export function UsageKpiCards({ items, loading = false, skeletonCount = 4 }: UsageKpiCardsProps) {
  if (loading) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: skeletonCount }, (_, index) => (
          <Card key={index} size="sm">
            <CardContent className="space-y-2">
              <Skeleton className="h-3 w-16" />
              <Skeleton className="h-7 w-24" />
              <Skeleton className="h-3 w-32" />
            </CardContent>
          </Card>
        ))}
      </div>
    );
  }
  if (items.length === 0) {
    return null;
  }
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {items.map((item) => (
        <Card key={item.label} size="sm">
          <CardContent className="space-y-1.5">
            <CardDescription className="text-xs">{item.label}</CardDescription>
            <p className="text-2xl font-semibold tracking-tight tabular-nums">{item.value}</p>
            {item.hint ? <p className="text-[11px] text-muted-foreground">{item.hint}</p> : null}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
