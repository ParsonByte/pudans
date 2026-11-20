import type { ReactNode } from "react";

import { AlertCircle, Inbox } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

import type { MemoryLayer } from "../lib/api";

type MemoryErrorStateProps = {
  title?: string;
  message: string;
  onRetry?: () => void;
};

export function MemoryErrorState({
  title = "无法加载记忆数据",
  message,
  onRetry,
}: MemoryErrorStateProps) {
  return (
    <div className="flex min-h-40 flex-col items-center justify-center gap-3 px-5 py-8 text-center">
      <span className="flex size-10 items-center justify-center bg-destructive/10 text-destructive">
        <AlertCircle className="size-5" />
      </span>
      <div className="space-y-1">
        <p className="text-sm font-medium">{title}</p>
        <p className="max-w-md text-xs text-muted-foreground">{message}</p>
      </div>
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          重试
        </Button>
      ) : null}
    </div>
  );
}

type MemoryEmptyStateProps = {
  title: string;
  description: string;
  action?: ReactNode;
};

export function MemoryEmptyState({ title, description, action }: MemoryEmptyStateProps) {
  return (
    <div className="flex min-h-40 flex-col items-center justify-center gap-3 px-5 py-8 text-center">
      <span className="flex size-10 items-center justify-center bg-muted text-muted-foreground">
        <Inbox className="size-5" />
      </span>
      <div className="space-y-1">
        <p className="text-sm font-medium">{title}</p>
        <p className="max-w-md text-xs text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );
}

export function MemoryLoadingState({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-2 p-4">
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-9 w-full" />
      ))}
    </div>
  );
}

export function MemoryBlockListSkeleton() {
  return (
    <div className="space-y-2 p-3">
      {Array.from({ length: 5 }, (_, index) => (
        <div key={index} className="space-y-2 px-3.5 py-3">
          <div className="flex items-center justify-between gap-3">
            <Skeleton className="h-4 w-3/5" />
            <Skeleton className="h-4 w-8" />
          </div>
          <Skeleton className="h-3 w-2/3" />
          <div className="flex items-center justify-between gap-3">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-3 w-16" />
          </div>
          <Skeleton className="h-3 w-24" />
        </div>
      ))}
    </div>
  );
}

function ChatSkeleton() {
  return (
    <div className="space-y-4">
      {Array.from({ length: 5 }, (_, index) => {
        if (index === 2) {
          return (
            <div key={index} className="flex justify-center">
              <Skeleton className="h-6 w-48" />
            </div>
          );
        }
        const isUser = index % 2 === 0;
        return (
          <div key={index} className={isUser ? "flex justify-end" : "flex justify-start"}>
            <div
              className={`space-y-2 p-3 ${isUser ? "w-2/3 bg-primary/10" : "w-3/4 border-l-[3px] border-primary/40 bg-muted"}`}
            >
              <Skeleton className="h-3 w-16" />
              <Skeleton className="h-3 w-full" />
              <Skeleton className="h-3 w-4/5" />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function AtomicSkeleton() {
  return (
    <div className="space-y-2.5">
      {Array.from({ length: 4 }, (_, index) => (
        <div key={index} className="space-y-2 border bg-card p-3">
          <div className="flex items-center gap-2">
            <Skeleton className="h-4 w-8 rounded" />
            <Skeleton className="h-4 flex-1" />
            <Skeleton className="h-3 w-16" />
          </div>
          <Skeleton className="h-3 w-11/12" />
          <Skeleton className="h-3 w-3/4" />
        </div>
      ))}
    </div>
  );
}

function SceneSkeleton() {
  return (
    <div className="space-y-2.5">
      {Array.from({ length: 4 }, (_, index) => (
        <div key={index} className="flex items-center gap-2 border bg-card p-3">
          <Skeleton className="h-4 w-8 rounded" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-3 w-20" />
          <Skeleton className="size-4 rounded" />
        </div>
      ))}
    </div>
  );
}

function ProfileSkeleton() {
  return (
    <div className="space-y-3 border bg-card p-3">
      <div className="flex items-center gap-2 border-b pb-2">
        <Skeleton className="h-4 w-8 rounded" />
        <Skeleton className="h-4 w-40" />
        <Skeleton className="ms-auto h-3 w-24" />
      </div>
      {[96, 88, 92, 76, 84, 68, 50].map((width, index) => (
        <Skeleton key={index} className="h-3" style={{ width: `${width}%` }} />
      ))}
    </div>
  );
}

export function MemoryLayerSkeleton({ layer }: { layer: MemoryLayer }) {
  switch (layer) {
    case "L0":
      return <ChatSkeleton />;
    case "L1":
      return <AtomicSkeleton />;
    case "L2":
      return <SceneSkeleton />;
    case "L3":
      return <ProfileSkeleton />;
  }
}
