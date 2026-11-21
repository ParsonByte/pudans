import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";

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
    <ConsoleState
      variant="error"
      title={title}
      description={message}
      onRetry={onRetry}
      className={className}
      wrapper
    />
  );
}

type KnowledgeTableSkeletonProps = {
  columns: number;
  rows?: number;
};

export function KnowledgeTableSkeleton({ columns, rows = 4 }: KnowledgeTableSkeletonProps) {
  return (
    <ConsoleTableSkeleton
      columns={columns}
      rows={rows}
      cellClassName="px-5 py-3"
      widthClassName="h-4 w-full max-w-40"
    />
  );
}
