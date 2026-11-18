import type { ElementType } from "react";

import { ConsoleSection } from "./console-page";

type ConsoleStatProps = {
  label: string;
  value: number;
  icon: ElementType;
};

export function ConsoleStat({ label, value, icon: Icon }: ConsoleStatProps) {
  return (
    <ConsoleSection className="flex items-start justify-between gap-4 p-5">
      <div className="space-y-2">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        <p className="text-2xl font-semibold tracking-tight tabular-nums">
          {value.toLocaleString("zh-CN")}
        </p>
      </div>
      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
        <Icon className="size-4" />
      </span>
    </ConsoleSection>
  );
}
