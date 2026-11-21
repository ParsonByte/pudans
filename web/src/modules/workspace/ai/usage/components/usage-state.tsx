import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";

type UsagePageNoPermissionProps = {
  title: string;
};

export function UsagePageNoPermission({ title }: UsagePageNoPermissionProps) {
  return (
    <ConsolePage title={title}>
      <ConsoleState variant="permission" title="没有查看该页面的权限。" wrapper />
    </ConsolePage>
  );
}

type UsageErrorStateProps = {
  title: string;
  message: string;
  onRetry: () => void;
};

export function UsageErrorState({ title, message, onRetry }: UsageErrorStateProps) {
  return <ConsoleState variant="error" title={title} description={message} onRetry={onRetry} />;
}

type UsageTableSkeletonProps = {
  columns: number;
  rows?: number;
};

export function UsageTableSkeleton({ columns, rows = 4 }: UsageTableSkeletonProps) {
  return <ConsoleTableSkeleton columns={columns} rows={rows} />;
}
