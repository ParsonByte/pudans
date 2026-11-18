import { useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ShieldPlus } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

import { resolveRoleApiError } from "../lib/api";
import {
  ROLE_API_VERSION,
  ROLE_DEPENDENCIES_ANNO,
  ROLE_DESCRIPTION_ANNO,
  ROLE_DISPLAY_NAME_ANNO,
  ROLE_KIND,
  ROLE_UI_PERMISSIONS_ANNO,
} from "../lib/role-model";

type CreateRoleDialogProps = {
  onClose: () => void;
  onCreated: (name: string) => void;
};

const createRoleSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入角色名称。")
    .max(63, "角色名称不能超过 63 个字符。")
    .regex(/^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/, "角色名称只能包含小写字母、数字和 -。"),
  displayName: z.string().trim().max(80, "显示名称不能超过 80 个字符。"),
  description: z.string().trim().max(200, "描述不能超过 200 个字符。"),
});

type CreateRoleValues = z.infer<typeof createRoleSchema>;

export function CreateRoleDialog({ onClose, onCreated }: CreateRoleDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<CreateRoleValues>({
    resolver: zodResolver(createRoleSchema),
    defaultValues: {
      name: "",
      displayName: "",
      description: "",
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: CreateRoleValues) => {
      const annotations: Record<string, string> = {
        [ROLE_DEPENDENCIES_ANNO]: "[]",
        [ROLE_UI_PERMISSIONS_ANNO]: "[]",
      };
      if (values.displayName) {
        annotations[ROLE_DISPLAY_NAME_ANNO] = values.displayName;
      }
      if (values.description) {
        annotations[ROLE_DESCRIPTION_ANNO] = values.description;
      }
      const response = await apiClient.role.createRole({
        role: {
          apiVersion: ROLE_API_VERSION,
          kind: ROLE_KIND,
          metadata: { name: values.name, annotations },
          rules: [],
        },
      });
      return response.data.metadata.name;
    },
    onSuccess: async (name) => {
      await queryClient.invalidateQueries({ queryKey: ["roles"] });
      toast.success("角色已创建。");
      onCreated(name);
      onClose();
    },
    onError: (error) => toast.error(resolveRoleApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<ShieldPlus />}
      title="创建角色"
      description="创建后可以在角色详情中配置菜单、操作、数据和工作区权限。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.5}
      submitLabel="创建角色"
      resetLabel="清空"
    >
      <FormField
        label="角色名称"
        htmlFor="create-role-name"
        error={form.formState.errors.name?.message}
        hint="创建后不可修改；只能包含小写字母、数字和 -。"
      >
        <Input
          id="create-role-name"
          autoComplete="off"
          spellCheck={false}
          {...form.register("name")}
        />
      </FormField>
      <FormField
        label="显示名称"
        htmlFor="create-role-display-name"
        error={form.formState.errors.displayName?.message}
      >
        <Input id="create-role-display-name" autoComplete="off" {...form.register("displayName")} />
      </FormField>
      <FormField
        label="描述"
        htmlFor="create-role-description"
        error={form.formState.errors.description?.message}
      >
        <Textarea
          id="create-role-description"
          rows={3}
          placeholder="说明该角色的用途和适用范围。"
          {...form.register("description")}
        />
      </FormField>
    </FormDialog>
  );
}
