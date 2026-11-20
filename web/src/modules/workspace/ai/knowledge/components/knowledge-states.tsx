import { RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TableCell, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";

type KnowledgeErrorStateProps = {
  title: string;
  message: string;
  onRetry?: () => void;
  className?: string;
};

export function KnowledgeErrorState({
  title,
  message,
  onRetry,
  className,
}: KnowledgeErrorStateProps) {
  return (
    <ConsoleSection
      className={cn(
        "flex min-h-40 flex-col items-center justify-center gap-3 p-8 text-center",
        className,
      )}
    >
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-md text-xs text-muted-foreground">{message}</p>
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          <RefreshCw />
          重试
        </Button>
      ) : null}
    </ConsoleSection>
  );
}

type KnowledgeTableSkeletonProps = {
  columns: number;
  rows?: number;
};

export function KnowledgeTableSkeleton({ columns, rows = 4 }: KnowledgeTableSkeletonProps) {
  return (
    <>
      {Array.from({ length: rows }, (_, rowIndex) => (
        <TableRow key={rowIndex}>
          {Array.from({ length: columns }, (_, columnIndex) => (
            <TableCell key={columnIndex} className="px-5 py-3">
              <Skeleton className="h-4 w-full max-w-40" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
}
