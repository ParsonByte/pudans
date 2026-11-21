import type { ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";

import { cn } from "@/lib/utils";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";

import { fetchParameterGroup, resolveParameterSettingError } from "../lib/parameter-api";
import { type ParameterFieldSet, createParameterFormValues } from "../lib/parameter-fields";
import { ParameterGroupForm } from "./parameter-group-form";

type ParameterPanelGroup = ParameterFieldSet & {
  key: string;
};

type ParameterGroupPanelProps = {
  group: ParameterPanelGroup;
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
      <ConsoleState
        variant="error"
        title="无法加载参数"
        description={resolveParameterSettingError(groupQuery.error)}
        onRetry={() => void groupQuery.refetch()}
      />
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
  group: ParameterPanelGroup;
};

function ParameterGroupSkeleton({ group }: ParameterGroupSkeletonProps) {
  const fieldCount = group.fields.length > 0 ? Math.min(group.fields.length, 6) : 6;
  const hasSectionTitles = group.fields.some((field) => Boolean(field.section));

  return (
    <ConsoleBlockSkeleton
      rows={fieldCount}
      className={cn("max-w-2xl space-y-3", hasSectionTitles && "md:pl-10")}
      itemClassName="h-8 w-full"
    />
  );
}
