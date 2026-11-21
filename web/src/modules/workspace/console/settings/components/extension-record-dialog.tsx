"use client";

import type { ReactNode } from "react";
import { type FieldErrors, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";

import type { ExtensionRecord } from "../lib/extension-records";
import {
  type ParameterFieldSet,
  type ParameterFormValues,
  createParameterFormValues,
  createParameterRequestBody,
  createParameterSchema,
  groupParameterFieldsBySection,
} from "../lib/parameter-fields";
import { ParameterFieldControl } from "./parameter-field";

type ExtensionRecordDialogProps = {
  title: string;
  description?: ReactNode;
  fields: ParameterFieldSet;
  existing?: ExtensionRecord;
  createRecord: (body: Record<string, unknown>) => ExtensionRecord;
  saveRecord: (record: ExtensionRecord) => Promise<unknown>;
  invalidateKeys: readonly (readonly unknown[])[];
  successMessage: string;
  resolveError: (error: unknown) => string;
  validateValues?: (values: ParameterFormValues) => string | null;
  onClose: () => void;
};

export function ExtensionRecordDialog({
  title,
  description,
  fields,
  existing,
  createRecord,
  saveRecord,
  invalidateKeys,
  successMessage,
  resolveError,
  validateValues,
  onClose,
}: ExtensionRecordDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<ParameterFormValues>({
    resolver: zodResolver(createParameterSchema(fields)) as Resolver<ParameterFormValues>,
    defaultValues: createParameterFormValues(
      fields,
      (existing ?? {}) as unknown as Record<string, unknown>,
    ),
  });

  const mutation = useMutation({
    mutationFn: (record: ExtensionRecord) => saveRecord(record),
    onSuccess: async () => {
      await Promise.all(
        invalidateKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })),
      );
      toast.success(successMessage);
      onClose();
    },
    onError: (error) => {
      toast.error(resolveError(error));
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => {
      const invalidMessage = validateValues?.(values);
      if (invalidMessage) {
        toast.error(invalidMessage);
        return;
      }
      mutation.mutate(createRecord(createParameterRequestBody(fields, values)));
    },
    (errors: FieldErrors<ParameterFormValues>) => {
      const firstMessage = Object.values(errors).find(
        (error) => typeof error?.message === "string",
      )?.message;

      toast.error(typeof firstMessage === "string" ? firstMessage : "请检查填写内容。");
    },
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      title={title}
      description={description}
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-2xl"
      formClassName="space-y-6"
    >
      {groupParameterFieldsBySection(fields.fields).map((section, index) => (
        <FormDialogSection key={`${section.title ?? "default"}-${index}`} title={section.title}>
          <div className="space-y-5">
            {section.fields.map((field) => (
              <ParameterFieldControl
                key={field.name}
                form={form}
                groupKey="extension-resource"
                field={field}
                userGroupOptions={[]}
                userGroupsPending={false}
              />
            ))}
          </div>
        </FormDialogSection>
      ))}
    </FormDialog>
  );
}
