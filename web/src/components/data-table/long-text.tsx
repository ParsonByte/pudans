import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

type LongTextProps = {
  children: ReactNode;
  className?: string;
};

export function LongText({ children, className }: LongTextProps) {
  return (
    <div
      className={cn("truncate", className)}
      title={typeof children === "string" ? children : undefined}
    >
      {children}
    </div>
  );
}
