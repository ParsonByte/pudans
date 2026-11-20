import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TableCell, TableRow } from "@/components/ui/table";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";

type UsagePageNoPermissionProps = {
  title: string;
};

export function UsagePageNoPermission({ title }: UsagePageNoPermissionProps) {
  return (
    <ConsolePage title={title}>
      <ConsoleSection>
        <ConsoleEmpty>没有查看该页面的权限。</ConsoleEmpty>
      </ConsoleSection>
    </ConsolePage>
  );
}

type UsageErrorStateProps = {
  title: string;
  message: string;
  onRetry: () => void;
};

export function UsageErrorState({ title, message, onRetry }: UsageErrorStateProps) {
  return (
    <div className="flex min-h-44 flex-col items-center justify-center gap-3 p-6 text-center">
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-lg text-xs text-muted-foreground">{message}</p>
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        重试
      </Button>
    </div>
  );
}

type UsageTableSkeletonProps = {
  columns: number;
  rows?: number;
};

export function UsageTableSkeleton({ columns, rows = 4 }: UsageTableSkeletonProps) {
  return (
    <>
      {Array.from({ length: rows }, (_, rowIndex) => (
        <TableRow key={rowIndex}>
          {Array.from({ length: columns }, (_, columnIndex) => (
            <TableCell key={columnIndex} className="px-4 py-3">
              <Skeleton className="h-4 w-full" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
}
