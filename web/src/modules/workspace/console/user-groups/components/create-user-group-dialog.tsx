import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UsersRound } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useRoles } from "@/modules/workspace/console/roles/lib/use-roles";
import { USER_GROUPS_QUERY_KEY } from "@/modules/workspace/lib/user-groups";

import { resolveUserGroupApiError } from "../lib/api";
import {
  USER_GROUP_API_VERSION,
  USER_GROUP_KIND,
  userGroupRoleOptions,
  userGroupRoleRefs,
} from "../lib/user-group-model";
import { RoleRefSelector } from "./role-ref-selector";

type CreateUserGroupDialogProps = {
  onClose: () => void;
};

const createUserGroupSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入用户组标识。")
    .max(63, "用户组标识不能超过 63 个字符。")
    .regex(/^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/, "用户组标识只能包含小写字母、数字和 -。"),
  displayName: z.string().trim().min(1, "请输入显示名称。").max(80, "显示名称不能超过 80 个字符。"),
  description: z.string().trim().max(200, "描述不能超过 200 个字符。"),
  roles: z.array(z.string()),
});

type CreateUserGroupValues = z.infer<typeof createUserGroupSchema>;

export function CreateUserGroupDialog({ onClose }: CreateUserGroupDialogProps) {
  const queryClient = useQueryClient();
  const rolesQuery = useRoles();
  const roleOptions = userGroupRoleOptions(rolesQuery.data ?? [], []);
  const form = useForm<CreateUserGroupValues>({
    resolver: zodResolver(createUserGroupSchema) as Resolver<CreateUserGroupValues>,
    defaultValues: {
      name: "",
      displayName: "",
      description: "",
      roles: [],
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: CreateUserGroupValues) => {
      await apiClient.userGroup.createUserGroup({
        userGroup: {
          apiVersion: USER_GROUP_API_VERSION,
          kind: USER_GROUP_KIND,
          metadata: { name: values.name },
          displayName: values.displayName,
          description: values.description || undefined,
          roleRefs: userGroupRoleRefs(values.roles),
        },
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: USER_GROUPS_QUERY_KEY });
      toast.success("用户组已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveUserGroupApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<UsersRound />}
      title="创建用户组"
      description="用户组通过绑定的角色获得权限，成员用户会继承这些角色。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.55}
      submitLabel="创建用户组"
      resetLabel="清空"
    >
      <FormField
        label="用户组标识"
        htmlFor="create-user-group-name"
        error={form.formState.errors.name?.message}
        hint="创建后不可修改；只能包含小写字母、数字和 -。"
      >
        <Input
          id="create-user-group-name"
          autoComplete="off"
          spellCheck={false}
          {...form.register("name")}
        />
      </FormField>
      <FormField
        label="显示名称"
        htmlFor="create-user-group-display-name"
        error={form.formState.errors.displayName?.message}
      >
        <Input
          id="create-user-group-display-name"
          autoComplete="off"
          {...form.register("displayName")}
        />
      </FormField>
      <FormField
        label="描述"
        htmlFor="create-user-group-description"
        error={form.formState.errors.description?.message}
      >
        <Textarea
          id="create-user-group-description"
          rows={3}
          placeholder="说明该用户组的用途和适用范围。"
          {...form.register("description")}
        />
      </FormField>
      <div className="space-y-2">
        <p className="text-sm font-medium">绑定角色</p>
        <p className="text-xs text-muted-foreground">
          自定义用户组只能绑定自定义角色，可稍后调整。
        </p>
        <Controller
          control={form.control}
          name="roles"
          render={({ field }) => (
            <RoleRefSelector
              options={roleOptions}
              selected={field.value}
              isLoading={rolesQuery.isPending}
              isError={rolesQuery.isError}
              onChange={field.onChange}
            />
          )}
        />
      </div>
    </FormDialog>
  );
}
