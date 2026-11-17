import { Construction } from "lucide-react";

import { ConsolePage, ConsoleSection } from "./components/console-page";

type ConsoleFeaturePlaceholderProps = {
  title: string;
};

export function ConsoleFeaturePlaceholder({ title }: ConsoleFeaturePlaceholderProps) {
  return (
    <ConsolePage title={title}>
      <ConsoleSection className="flex min-h-64 flex-col items-center justify-center gap-4 p-8 text-center">
        <span className="flex size-10 items-center justify-center rounded-full bg-muted text-muted-foreground">
          <Construction className="size-5" />
        </span>
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">{title}模块正在建设中</h2>
          <p className="text-xs text-muted-foreground">该页面尚未接入控制台。</p>
        </div>
      </ConsoleSection>
    </ConsolePage>
  );
}
