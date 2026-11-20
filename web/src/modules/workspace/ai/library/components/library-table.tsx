import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

const SKELETON_ROWS = 5;

type LibraryTableProps = {
  loading: boolean;
  error: unknown;
  isEmpty: boolean;
  emptyMessage: ReactNode;
  errorTitle: string;
  resolveError: (error: unknown) => string;
  onRetry: () => void;
  children: ReactNode;
};

export function LibraryTable({
  loading,
  error,
  isEmpty,
  emptyMessage,
  errorTitle,
  resolveError,
  onRetry,
  children,
}: LibraryTableProps) {
  if (loading) {
    return (
      <ConsoleSection>
        <div className="space-y-2 p-4">
          {Array.from({ length: SKELETON_ROWS }, (_, index) => (
            <Skeleton key={index} className="h-10 rounded-lg" />
          ))}
        </div>
      </ConsoleSection>
    );
  }

  if (error) {
    return (
      <ConsoleSection className="flex min-h-56 flex-col items-center justify-center gap-3 p-8 text-center">
        <p className="text-sm font-medium">{errorTitle}</p>
        <p className="text-xs text-muted-foreground">{resolveError(error)}</p>
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          重试
        </Button>
      </ConsoleSection>
    );
  }

  if (isEmpty) {
    return (
      <ConsoleSection>
        <ConsoleEmpty>{emptyMessage}</ConsoleEmpty>
      </ConsoleSection>
    );
  }

  return <ConsoleSection className="overflow-hidden">{children}</ConsoleSection>;
}
