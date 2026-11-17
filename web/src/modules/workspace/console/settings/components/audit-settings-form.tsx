import { type FormEvent, useState } from "react";

import { useMutation } from "@tanstack/react-query";
import { ChevronDown, Loader2, Save, Undo2 } from "lucide-react";
import { toast } from "sonner";

import { FloatingActionBar } from "@/components/floating-action-bar";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import {
  AUDIT_MODE_OPTIONS,
  AUDIT_OPERATION_GROUPS,
  AUDIT_OPERATION_VALUES,
  AUDIT_RESOURCE_GROUPS,
  type AuditEventGroup,
  type AuditMode,
} from "../lib/audit-events";
import { resolveParameterSettingError, updateParameterGroup } from "../lib/parameter-api";
import { AuditEventGroupSection } from "./audit-event-group";

type AuditDraft = {
  mode: AuditMode;
  operations: string[];
  resourceTypes: string[];
};

type AuditSettingsFormProps = {
  data: Record<string, unknown>;
};

export function AuditSettingsForm({ data }: AuditSettingsFormProps) {
  const [initialDraft] = useState(() => readAuditDraft(data));
  const [draft, setDraft] = useState(initialDraft);
  const [savedDraft, setSavedDraft] = useState(initialDraft);

  const mutation = useMutation({
    mutationFn: (values: AuditDraft) =>
      updateParameterGroup("audit", {
        auditRecordRules: {
          mode: values.mode,
          operations:
            values.mode === "WHITELIST" &&
            values.resourceTypes.length === 0 &&
            sameValues(values.operations, AUDIT_OPERATION_VALUES)
              ? []
              : values.operations,
          resourceTypes: values.resourceTypes,
        },
      }),
    onSuccess: (_, values) => {
      setSavedDraft(values);
      toast.success("审计配置已保存。");
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const dirty =
    draft.mode !== savedDraft.mode ||
    !sameValues(draft.operations, savedDraft.operations) ||
    !sameValues(draft.resourceTypes, savedDraft.resourceTypes);

  const extraOperations = draft.operations.filter(
    (value) => !AUDIT_OPERATION_VALUES.includes(value),
  );
  const extraResourceTypes = draft.resourceTypes.filter(
    (value) =>
      !AUDIT_RESOURCE_GROUPS.some((group) => group.items.some((item) => item.value === value)),
  );
  const operationGroups = withCustomGroup(AUDIT_OPERATION_GROUPS, extraOperations);
  const resourceGroups = withCustomGroup(AUDIT_RESOURCE_GROUPS, extraResourceTypes);

  const toggleItem = (key: "operations" | "resourceTypes", value: string, checked: boolean) => {
    setDraft((current) => ({
      ...current,
      [key]: toggleValues(current[key], [value], checked),
    }));
  };

  const toggleGroup = (
    key: "operations" | "resourceTypes",
    values: readonly string[],
    checked: boolean,
  ) => {
    setDraft((current) => ({
      ...current,
      [key]: toggleValues(current[key], values, checked),
    }));
  };

  const handleModeChange = (value: string) => {
    const mode: AuditMode = value === "BLACKLIST" ? "BLACKLIST" : "WHITELIST";

    setDraft({
      mode,
      operations: mode === "WHITELIST" ? [...AUDIT_OPERATION_VALUES] : [],
      resourceTypes: [],
    });
  };

  const handleReset = () => {
    setDraft(savedDraft);
  };

  const formId = "audit-parameter-form";

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (
      draft.mode === "WHITELIST" &&
      draft.operations.length === 0 &&
      draft.resourceTypes.length === 0
    ) {
      toast.error("白名单模式至少需要选择一个事件。");
      return;
    }

    mutation.mutate(draft);
  };

  return (
    <form
      id={formId}
      onSubmit={handleSubmit}
      className="max-w-5xl space-y-10"
      aria-busy={mutation.isPending}
      noValidate
    >
      <section className="space-y-4 border-b pb-6">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_18rem] md:items-start">
          <div className="space-y-1">
            <h3 className="text-base font-semibold">筛选模式</h3>
            <p className="text-sm text-muted-foreground">
              {draft.mode === "WHITELIST"
                ? "仅勾选的操作事件和资源类型会写入审计日志。"
                : "勾选的操作事件和资源类型不会写入审计日志。"}
            </p>
            <p className="text-xs text-muted-foreground">切换筛选模式会重置事件选择。</p>
          </div>
          <Select value={draft.mode} onValueChange={handleModeChange}>
            <SelectTrigger id="audit-mode" className="w-full" aria-label="筛选模式">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {AUDIT_MODE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </section>

      <section className="space-y-6">
        <div className="space-y-1">
          <h3 className="text-base font-semibold">操作事件</h3>
          <p className="text-sm text-muted-foreground">
            请求级审计操作，用于决定哪些接口调用会写入审计日志。
          </p>
        </div>
        <div className="space-y-6">
          {operationGroups.map((group) => (
            <AuditEventGroupSection
              key={group.key}
              group={group}
              selected={draft.operations}
              mode={draft.mode}
              onToggleItem={(value, checked) => toggleItem("operations", value, checked)}
              onToggleGroup={(values, checked) => toggleGroup("operations", values, checked)}
            />
          ))}
        </div>
      </section>

      <Collapsible defaultOpen={initialDraft.resourceTypes.length > 0} className="border-t pt-6">
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="group flex w-full items-start justify-between gap-4 text-left"
          >
            <div className="space-y-1">
              <h3 className="text-base font-semibold">资源类型</h3>
              <p className="text-sm text-muted-foreground">
                可选：按资源类型额外匹配；与操作事件同时配置时，任一匹配即生效。
              </p>
            </div>
            <ChevronDown className="mt-1 size-4 shrink-0 text-muted-foreground transition-transform group-data-[state=open]:rotate-180" />
          </button>
        </CollapsibleTrigger>
        <CollapsibleContent className="space-y-6 pt-6">
          {resourceGroups.map((group) => (
            <AuditEventGroupSection
              key={group.key}
              group={group}
              selected={draft.resourceTypes}
              mode={draft.mode}
              onToggleItem={(value, checked) => toggleItem("resourceTypes", value, checked)}
              onToggleGroup={(values, checked) => toggleGroup("resourceTypes", values, checked)}
            />
          ))}
        </CollapsibleContent>
      </Collapsible>

      <FloatingActionBar open={dirty}>
        <Button type="button" variant="outline" disabled={mutation.isPending} onClick={handleReset}>
          <Undo2 />
          撤销更改
        </Button>
        <Button type="submit" form={formId} disabled={mutation.isPending}>
          {mutation.isPending ? <Loader2 className="animate-spin" /> : <Save />}
          保存更改
        </Button>
      </FloatingActionBar>
    </form>
  );
}

function readAuditDraft(data: Record<string, unknown>): AuditDraft {
  const rules = isRecord(data.auditRecordRules) ? data.auditRecordRules : {};
  const mode: AuditMode = rules.mode === "BLACKLIST" ? "BLACKLIST" : "WHITELIST";
  const operations = toStringArray(rules.operations);
  const resourceTypes = toStringArray(rules.resourceTypes);
  const allByDefault = operations.length === 0 && resourceTypes.length === 0;

  return {
    mode,
    operations: allByDefault && mode === "WHITELIST" ? [...AUDIT_OPERATION_VALUES] : operations,
    resourceTypes,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
}

function toggleValues(
  current: readonly string[],
  values: readonly string[],
  checked: boolean,
): string[] {
  const next = new Set(current);

  for (const value of values) {
    if (checked) {
      next.add(value);
    } else {
      next.delete(value);
    }
  }

  return [...next];
}

function sameValues(left: readonly string[], right: readonly string[]): boolean {
  const leftSet = new Set(left);
  const rightSet = new Set(right);

  return leftSet.size === rightSet.size && [...leftSet].every((value) => rightSet.has(value));
}

function withCustomGroup(
  groups: readonly AuditEventGroup[],
  values: readonly string[],
): readonly AuditEventGroup[] {
  if (values.length === 0) {
    return groups;
  }

  return [
    ...groups,
    {
      key: "custom",
      label: "其他已配置项",
      description: "当前配置中不在预设列表内的条目。",
      items: values.map((value) => ({ value, label: value })),
    },
  ];
}
