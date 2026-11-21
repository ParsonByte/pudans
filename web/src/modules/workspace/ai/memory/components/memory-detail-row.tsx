import type { ReactNode } from "react";

type MemoryDetailRowProps = {
  label: string;
  children: ReactNode;
};

export function MemoryDetailRow({ label, children }: MemoryDetailRowProps) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b pb-1.5">
      <span className="shrink-0 text-muted-foreground">{label}</span>
      <span className="min-w-0 truncate text-right">{children}</span>
    </div>
  );
}
