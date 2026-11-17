import type { ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";
import { Loader2, RefreshCw, TriangleAlert } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

import { fetchParameterGroup, resolveParameterSettingError } from "../lib/parameter-api";
import { type ParameterGroup, createParameterFormValues } from "../lib/parameter-fields";
import { ParameterGroupForm } from "./parameter-group-form";

type ParameterGroupPanelProps = {
  group: ParameterGroup;
  sectionActions?: Partial<Record<string, ReactNode>>;
  render?: (data: Record<string, unknown>) => ReactNode;
};

export function ParameterGroupPanel({ group, sectionActions, render }: ParameterGroupPanelProps) {
  const groupQuery = useQuery({
    queryKey: ["parameter-setting", group.key],
    queryFn: () => fetchParameterGroup(group.key),
    retry: false,
  });

  if (groupQuery.isPending) {
    return <ParameterGroupSkeleton group={group} />;
  }

  if (groupQuery.isError || !groupQuery.data) {
    return (
      <div className="flex min-h-48 flex-col items-center justify-center gap-4 p-6 text-center">
        <span className="flex size-10 items-center justify-center rounded-full bg-destructive/10 text-destructive">
          <TriangleAlert className="size-5" />
        </span>
        <div className="space-y-1">
          <p className="text-sm font-medium">无法加载参数</p>
          <p className="text-xs text-muted-foreground">
            {resolveParameterSettingError(groupQuery.error)}
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => void groupQuery.refetch()}
          disabled={groupQuery.isFetching}
        >
          {groupQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
          重试
        </Button>
      </div>
    );
  }

  if (render) {
    return <>{render(groupQuery.data)}</>;
  }

  return (
    <ParameterGroupForm
      group={group}
      initialValues={createParameterFormValues(group, groupQuery.data)}
      sectionActions={sectionActions}
    />
  );
}

type ParameterGroupSkeletonProps = {
  group: ParameterGroup;
};

function ParameterGroupSkeleton({ group }: ParameterGroupSkeletonProps) {
  const fields = group.fields.slice(0, 6);
  const hasSectionTitles = group.fields.some((field) => Boolean(field.section));

  return (
    <div className={cn("max-w-2xl space-y-6", hasSectionTitles && "md:pl-10")}>
      {fields.length === 0
        ? Array.from({ length: 6 }, (_, index) => (
            <div key={index} className="space-y-2">
              <Skeleton className="h-3.5 w-20" />
              <Skeleton className="h-8 w-full" />
            </div>
          ))
        : fields.map((field) => (
            <div key={field.name} className="space-y-2">
              <Skeleton className="h-3.5 w-20" />
              <Skeleton className={cn(field.kind === "textarea" ? "h-24" : "h-8", "w-full")} />
            </div>
          ))}
    </div>
  );
}
