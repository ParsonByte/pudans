import { type ReactNode, useMemo } from "react";
import { type FieldErrors, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Loader2, Save, Undo2 } from "lucide-react";
import { toast } from "sonner";

import { FloatingActionBar } from "@/components/floating-action-bar";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import { resolveParameterSettingError, updateParameterGroup } from "../lib/parameter-api";
import {
  type ParameterField,
  type ParameterFormValues,
  type ParameterGroup,
  createParameterRequestBody,
  createParameterSchema,
} from "../lib/parameter-fields";
import { LogoPair } from "./logo-fields";
import { ParameterFieldControl } from "./parameter-field";

type ParameterGroupFormProps = {
  group: ParameterGroup;
  initialValues: ParameterFormValues;
  sectionActions?: Partial<Record<string, ReactNode>>;
};

export function ParameterGroupForm({
  group,
  initialValues,
  sectionActions,
}: ParameterGroupFormProps) {
  const schema = useMemo(() => createParameterSchema(group), [group]);
  const form = useForm<ParameterFormValues>({
    resolver: zodResolver(schema) as Resolver<ParameterFormValues>,
    defaultValues: initialValues,
  });

  const userGroupsQuery = useQuery({
    queryKey: ["user-groups", "parameter-options"],
    queryFn: async () =>
      (await apiClient.userGroup.listUserGroup({ page: 1, size: 200 })).data.items,
    enabled: group.fields.some((field) => field.kind === "user-group"),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
  const userGroupOptions = (userGroupsQuery.data ?? []).map((userGroup) => ({
    value: userGroup.metadata.name,
    label: userGroup.displayName || userGroup.metadata.name,
  }));

  const mutation = useMutation({
    mutationFn: (values: ParameterFormValues) =>
      updateParameterGroup(group.key, createParameterRequestBody(group, values)),
    onSuccess: ({ restartRequired }, values) => {
      form.reset(values);
      toast.success("参数已保存。");

      if (restartRequired) {
        toast.info("部分安全参数需要重启后生效。");
      }
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    (errors: FieldErrors<ParameterFormValues>) => {
      const firstMessage = Object.values(errors).find(
        (error) => typeof error?.message === "string",
      )?.message;

      toast.error(typeof firstMessage === "string" ? firstMessage : "请检查填写内容。");
    },
  );

  const formId = `parameter-form-${group.key}`;

  return (
    <form
      id={formId}
      onSubmit={onSubmit}
      className="max-w-3xl space-y-8"
      aria-busy={mutation.isPending}
      noValidate
    >
      <div className="space-y-10">
        {groupFieldsBySection(group.fields).map((section, index) => {
          const logoFields = section.fields.filter(
            (field) => field.name === "logoLight" || field.name === "logoDark",
          );
          const otherFields = section.fields.filter(
            (field) => field.name !== "logoLight" && field.name !== "logoDark",
          );

          return (
            <section key={`${section.title ?? "default"}-${index}`} className="space-y-4">
              {section.title ? (
                <div className="flex min-h-8 items-center justify-between gap-3">
                  <h3 className="text-base font-semibold">{section.title}</h3>
                  {sectionActions?.[section.title]}
                </div>
              ) : null}
              <div className={cn("space-y-6", section.title && "md:pl-10")}>
                {logoFields.length > 0 ? <LogoPair form={form} fields={logoFields} /> : null}
                {otherFields.map((field) => (
                  <ParameterFieldControl
                    key={field.name}
                    form={form}
                    groupKey={group.key}
                    field={field}
                    userGroupOptions={userGroupOptions}
                    userGroupsPending={userGroupsQuery.isPending}
                  />
                ))}
              </div>
            </section>
          );
        })}
      </div>

      <FloatingActionBar open={form.formState.isDirty}>
        <Button
          type="button"
          variant="outline"
          disabled={mutation.isPending}
          onClick={() => form.reset()}
        >
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

type ParameterSection = {
  title?: string;
  fields: ParameterField[];
};

function groupFieldsBySection(fields: readonly ParameterField[]): ParameterSection[] {
  return fields.reduce<ParameterSection[]>((sections, field) => {
    const current = sections[sections.length - 1];

    if (current && current.title === field.section) {
      current.fields.push(field);
    } else {
      sections.push({ title: field.section, fields: [field] });
    }

    return sections;
  }, []);
}
