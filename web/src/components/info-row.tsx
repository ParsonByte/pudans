import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

type InfoRowProps = {
  label: string;
  value: ReactNode;
  className?: string;
};

export function InfoRow({ label, value, className }: InfoRowProps) {
  return (
    <div className={cn("space-y-1", className)}>
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <div className="text-sm">{value}</div>
    </div>
  );
}
