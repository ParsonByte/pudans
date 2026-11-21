import type { ReactNode } from "react";

import { AlertCircle, Inbox, RefreshCw, ShieldAlert } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TableCell, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

import { ConsoleSection } from "./console-page";

type ConsoleStateVariant = "error" | "empty" | "permission";

type ConsoleStateProps = {
  variant: ConsoleStateVariant;
  title: string;
  description?: string;
  onRetry?: () => void;
  action?: ReactNode;
  className?: string;
  wrapper?: boolean;
};

const STATE_ICONS = {
  error: AlertCircle,
  empty: Inbox,
  permission: ShieldAlert,
} as const;

export function ConsoleState({
  variant,
  title,
  description,
  onRetry,
  action,
  className,
  wrapper = false,
}: ConsoleStateProps) {
  const Icon = STATE_ICONS[variant];
  const content = (
    <div
      className={cn(
        "flex min-h-40 flex-col items-center justify-center gap-3 px-5 py-8 text-center",
        className,
      )}
    >
      <span
        className={cn(
          "flex size-10 items-center justify-center",
          variant === "error"
            ? "bg-destructive/10 text-destructive"
            : "bg-muted text-muted-foreground",
        )}
      >
        <Icon className="size-5" />
      </span>
      <div className="space-y-1">
        <p className="text-sm font-medium">{title}</p>
        {description ? (
          <p className="max-w-md text-xs leading-5 text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          <RefreshCw />
          重试
        </Button>
      ) : null}
      {action}
    </div>
  );
  return wrapper ? <ConsoleSection>{content}</ConsoleSection> : content;
}

type ConsoleTableSkeletonProps = {
  columns: number;
  rows?: number;
  cellClassName?: string;
  widthClassName?: string;
};

export function ConsoleTableSkeleton({
  columns,
  rows = 4,
  cellClassName = "px-4 py-3",
  widthClassName = "h-4 w-full",
}: ConsoleTableSkeletonProps) {
  return (
    <>
      {Array.from({ length: rows }, (_, rowIndex) => (
        <TableRow key={rowIndex}>
          {Array.from({ length: columns }, (_, columnIndex) => (
            <TableCell key={columnIndex} className={cellClassName}>
              <Skeleton className={widthClassName} />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
}

type ConsoleBlockSkeletonProps = {
  rows?: number;
  className?: string;
  itemClassName?: string;
};

export function ConsoleBlockSkeleton({
  rows = 3,
  className = "space-y-2 p-4",
  itemClassName = "h-9 w-full",
}: ConsoleBlockSkeletonProps) {
  return (
    <div className={className}>
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className={itemClassName} />
      ))}
    </div>
  );
}
