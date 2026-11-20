import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

import type { SandboxState } from "../lib/api";
import { runtimeProviderLabel, runtimeStateBadgeVariant, runtimeStateLabel } from "../lib/model";

type RuntimeStateBadgeProps = {
  state?: SandboxState | null;
  className?: string;
};

export function RuntimeStateBadge({ state, className }: RuntimeStateBadgeProps) {
  return (
    <Badge variant={runtimeStateBadgeVariant(state)} className={cn(className)}>
      {runtimeStateLabel(state)}
    </Badge>
  );
}

type RuntimeProviderBadgeProps = {
  provider?: string | null;
  className?: string;
};

export function RuntimeProviderBadge({ provider, className }: RuntimeProviderBadgeProps) {
  return (
    <Badge variant="outline" className={cn(className)}>
      {runtimeProviderLabel(provider ?? undefined)}
    </Badge>
  );
}
