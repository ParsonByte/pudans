import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";

type RuntimeTableSkeletonProps = {
  rows?: number;
};

export function RuntimeTableSkeleton({ rows = 3 }: RuntimeTableSkeletonProps) {
  return <ConsoleBlockSkeleton rows={rows} className="grid gap-2 p-4" itemClassName="h-12" />;
}

type RuntimeLoadErrorProps = {
  title: string;
  description: string;
  onRetry: () => void;
};

export function RuntimeLoadError({ title, description, onRetry }: RuntimeLoadErrorProps) {
  return <ConsoleState variant="error" title={title} description={description} onRetry={onRetry} />;
}
