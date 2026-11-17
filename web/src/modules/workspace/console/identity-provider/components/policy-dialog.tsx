import { type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { ParameterFieldControl } from "@/modules/workspace/console/settings/components/parameter-field";
import {
  resolveParameterSettingError,
  updateParameterGroup,
} from "@/modules/workspace/console/settings/lib/parameter-api";
import {
  type ParameterFieldSet,
  type ParameterFormValues,
  createParameterFormValues,
  createParameterRequestBody,
  createParameterSchema,
  groupParameterFieldsBySection,
} from "@/modules/workspace/console/settings/lib/parameter-fields";

import { AUTHENTICATION_GROUP } from "../lib/authentication-fields";
import type { IdentityKind } from "./identity-icon";
import { IdentityIcon } from "./identity-icon";

type PolicyDialogProps = {
  kind: IdentityKind;
  title: string;
  description: string;
  fields: ParameterFieldSet;
  data: Record<string, unknown>;
  onClose: () => void;
};

export function PolicyDialog({
  kind,
  title,
  description,
  fields,
  data,
  onClose,
}: PolicyDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<ParameterFormValues>({
    resolver: zodResolver(createParameterSchema(fields)) as Resolver<ParameterFormValues>,
    defaultValues: createParameterFormValues(fields, data),
  });

  const mutation = useMutation({
    mutationFn: (values: ParameterFormValues) =>
      updateParameterGroup(AUTHENTICATION_GROUP, createParameterRequestBody(fields, values)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["parameter-setting", AUTHENTICATION_GROUP],
      });
      toast.success("身份认证配置已保存。");
      onClose();
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  const dialogWidthClass = kind === "feishu" ? "sm:max-w-xl" : "sm:max-w-lg";
  const scrollHeightRatio = kind === "feishu" ? 0.48 : 0.4;
  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<IdentityIcon kind={kind} size={24} />}
      title={title}
      description={description}
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      maxHeightRatio={scrollHeightRatio}
      contentClassName={dialogWidthClass}
      formClassName="space-y-5"
    >
      {groupParameterFieldsBySection(fields.fields).map((section, index) => (
        <FormDialogSection key={`${section.title ?? "default"}-${index}`} title={section.title}>
          <div className="space-y-5">
            {section.fields.map((field) => (
              <ParameterFieldControl
                key={field.name}
                form={form}
                groupKey="identity-provider"
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
