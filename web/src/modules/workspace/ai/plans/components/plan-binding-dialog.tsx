import { useEffect } from "react";
import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UserPlus } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { beijingDateTimeInputToIso, beijingDateTimeInputValue } from "@/lib/date";
import {
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "@/modules/workspace/ai/providers/lib/model";
import { ConsoleState } from "@/modules/workspace/console/components/console-state";

import type { AiPlanBindingItem, AiPlanItem } from "../lib/api";
import {
  buildPlanBindingItem,
  createPlanBinding,
  fetchPlanBinding,
  resolvePlanApiError,
  resolvePlanSubjects,
  updatePlanBinding,
} from "../lib/api";
import { planSubjectKindLabel } from "../lib/model";
import { SubjectPicker } from "./subject-picker";

const bindingSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "请输入绑定名称。")
      .max(63, "绑定名称不能超过 63 个字符。")
      .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
    plan: z.string().trim().min(1, "请选择套餐。"),
    subjectKind: z.enum(["User", "UserGroup"]),
    subjectName: z.string().trim(),
    priority: z.string().trim().regex(/^\d+$/, "优先级必须为非负整数。"),
    effectiveFrom: z.string(),
    effectiveTo: z.string(),
  })
  .superRefine((values, context) => {
    if (values.effectiveFrom && values.effectiveTo) {
      const from = beijingDateTimeInputToIso(values.effectiveFrom);
      const to = beijingDateTimeInputToIso(values.effectiveTo);
      if (from && to && new Date(from) >= new Date(to)) {
        context.addIssue({
          code: "custom",
          path: ["effectiveTo"],
          message: "生效结束时间必须晚于开始时间。",
        });
      }
    }
  });

type BindingFormValues = z.infer<typeof bindingSchema>;

type PlanBindingDialogProps = {
  bindingName?: string;
  plans: AiPlanItem[];
  onClose: () => void;
};

export function PlanBindingDialog({ bindingName, plans, onClose }: PlanBindingDialogProps) {
  const queryClient = useQueryClient();
  const bindingQuery = useQuery({
    queryKey: ["ai-plan-binding", bindingName ?? ""],
    queryFn: () => fetchPlanBinding(bindingName as string),
    enabled: Boolean(bindingName),
    retry: false,
  });
  const binding = bindingQuery.data;
  const form = useForm<BindingFormValues>({
    resolver: zodResolver(bindingSchema) as Resolver<BindingFormValues>,
    defaultValues: initialValues(undefined, plans),
  });
  const subjectKind = useWatch({ control: form.control, name: "subjectKind" });
  const subjectName = useWatch({ control: form.control, name: "subjectName" });
  const plan = useWatch({ control: form.control, name: "plan" });

  useEffect(() => {
    if (binding) {
      form.reset(initialValues(binding, plans));
    }
  }, [binding, form, plans]);

  const mutation = useMutation({
    mutationFn: async (values: BindingFormValues) => {
      let subject = binding?.spec.subject;
      if (!binding) {
        if (!values.subjectName) {
          throw new Error("请选择要绑定的用户或用户组。");
        }
        const resolved = await resolvePlanSubjects(values.subjectKind, [values.subjectName]);
        subject = resolved[0];
      }
      if (!subject) {
        throw new Error("无法解析绑定主体。");
      }
      const input = {
        name: binding?.metadata.name ?? values.name,
        plan: values.plan,
        subject,
        priority: Number(values.priority),
        effectiveFrom: beijingDateTimeInputToIso(values.effectiveFrom) ?? null,
        effectiveTo: beijingDateTimeInputToIso(values.effectiveTo) ?? null,
      };
      return binding
        ? updatePlanBinding(buildPlanBindingItem(input, binding))
        : createPlanBinding(buildPlanBindingItem(input));
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-bindings"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-binding"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-plan-usage"] });
      toast.success(binding ? "套餐绑定已保存" : "套餐绑定已创建");
      onClose();
    },
    onError: (error) => toast.error(resolvePlanApiError(error)),
  });

  if (bindingName && bindingQuery.isLoading) {
    return (
      <DialogFormShell
        open
        onOpenChange={(open) => !open && onClose()}
        title="编辑套餐绑定"
        description="正在读取绑定详情…"
        size="lg"
      >
        <div className="space-y-2">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-8 w-full" />
          ))}
        </div>
      </DialogFormShell>
    );
  }

  if (bindingName && bindingQuery.isError) {
    return (
      <DialogFormShell
        open
        onOpenChange={(open) => !open && onClose()}
        title="编辑套餐绑定"
        description="无法读取绑定详情。"
        size="lg"
        footer={
          <Button type="button" variant="outline" onClick={onClose}>
            关闭
          </Button>
        }
      >
        <ConsoleState
          variant="error"
          title="无法加载套餐绑定"
          description={resolvePlanApiError(bindingQuery.error)}
          onRetry={() => void bindingQuery.refetch()}
        />
      </DialogFormShell>
    );
  }

  return (
    <FormDialog
      open
      onClose={onClose}
      title={binding ? "编辑套餐绑定" : "新建套餐绑定"}
      description="单条绑定支持用户或用户组主体，生效时间按北京时间解析。"
      icon={<UserPlus />}
      form={form}
      onSubmit={form.handleSubmit(
        (values) => mutation.mutate(values),
        () => undefined,
      )}
      isPending={mutation.isPending}
      submitLabel={binding ? "保存绑定" : "创建绑定"}
      maxHeightRatio={0.6}
    >
      <FormDialogSection title="绑定主体">
        <div className="space-y-3">
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">绑定名称</span>
            <Input disabled={Boolean(binding)} {...form.register("name")} />
            {form.formState.errors.name ? (
              <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
            ) : null}
          </label>
          {binding ? (
            <div className="flex flex-wrap items-center gap-2 border p-3 text-sm">
              <Badge variant="secondary">{planSubjectKindLabel(binding.spec.subject.kind)}</Badge>
              <span className="font-mono text-xs">{binding.spec.subject.name}</span>
              <span className="text-xs text-muted-foreground">
                主体创建后不可修改，如需更换请新建绑定。
              </span>
            </div>
          ) : (
            <div className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">绑定主体</span>
              <SubjectPicker
                kind={subjectKind}
                onKindChange={(value) => {
                  form.setValue("subjectKind", value, { shouldDirty: true });
                  form.setValue("subjectName", "", { shouldDirty: true });
                }}
                value={subjectName ? [subjectName] : []}
                onChange={(value) =>
                  form.setValue("subjectName", value[value.length - 1] ?? "", {
                    shouldDirty: true,
                  })
                }
                multiple={false}
              />
              {!subjectName ? (
                <p className="text-xs text-muted-foreground">请选择一个用户或用户组。</p>
              ) : null}
            </div>
          )}
        </div>
      </FormDialogSection>
      <FormDialogSection title="套餐与生效">
        <div className="space-y-3">
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">套餐</span>
            <Select
              value={plan}
              onValueChange={(value) => form.setValue("plan", value, { shouldDirty: true })}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="选择套餐" />
              </SelectTrigger>
              <SelectContent>
                {plans.map((item) => (
                  <SelectItem key={item.metadata.name} value={item.metadata.name}>
                    {item.spec.displayName || item.metadata.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {form.formState.errors.plan ? (
              <p className="text-xs text-destructive">{form.formState.errors.plan.message}</p>
            ) : null}
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">优先级</span>
            <Input inputMode="numeric" {...form.register("priority")} />
            {form.formState.errors.priority ? (
              <p className="text-xs text-destructive">{form.formState.errors.priority.message}</p>
            ) : null}
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">生效开始（北京时间，可空）</span>
              <Input type="datetime-local" {...form.register("effectiveFrom")} />
            </label>
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">生效结束（北京时间，可空）</span>
              <Input type="datetime-local" {...form.register("effectiveTo")} />
              {form.formState.errors.effectiveTo ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.effectiveTo.message}
                </p>
              ) : null}
            </label>
          </div>
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}

function initialValues(
  binding: AiPlanBindingItem | undefined,
  plans: AiPlanItem[],
): BindingFormValues {
  return {
    name: binding?.metadata.name ?? "",
    plan: binding?.spec.plan ?? plans[0]?.metadata.name ?? "",
    subjectKind: binding?.spec.subject.kind ?? "UserGroup",
    subjectName: binding?.spec.subject.kind === "UserGroup" ? binding.spec.subject.name : "",
    priority: String(binding?.spec.priority ?? 0),
    effectiveFrom: beijingDateTimeInputValue(binding?.spec.effectiveFrom),
    effectiveTo: beijingDateTimeInputValue(binding?.spec.effectiveTo),
  };
}
