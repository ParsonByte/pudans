import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

type RuntimeTableSkeletonProps = {
  rows?: number;
};

export function RuntimeTableSkeleton({ rows = 3 }: RuntimeTableSkeletonProps) {
  return (
    <div className="grid gap-2 p-4">
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-12 rounded-lg" />
      ))}
    </div>
  );
}

type RuntimeLoadErrorProps = {
  title: string;
  description: string;
  onRetry: () => void;
};

export function RuntimeLoadError({ title, description, onRetry }: RuntimeLoadErrorProps) {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center gap-3 p-8 text-center">
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-md text-xs text-muted-foreground">{description}</p>
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        重试
      </Button>
    </div>
  );
}
