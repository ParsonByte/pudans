import { useMemo } from "react";
import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import type { UserGroupBinding } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useRoles } from "@/modules/workspace/console/roles/lib/use-roles";
import { USER_GROUPS_QUERY_KEY } from "@/modules/workspace/lib/user-groups";

import {
  createGroupRoleBinding,
  resolveUserGroupBindingApiError,
  updateGroupRoleBinding,
} from "../lib/api";
import { userGroupRoleBindingName, userGroupRoleOptions } from "../lib/user-group-model";

type UserGroupRoleBindingDialogProps = {
  groupName: string;
  groupDisplayName: string;
  binding?: UserGroupBinding;
  boundRoleNames: readonly string[];
  onClose: () => void;
};

const roleBindingSchema = z.object({
  roleName: z.string().min(1, "请选择角色。"),
});

type RoleBindingValues = z.infer<typeof roleBindingSchema>;

export function UserGroupRoleBindingDialog({
  groupName,
  groupDisplayName,
  binding,
  boundRoleNames,
  onClose,
}: UserGroupRoleBindingDialogProps) {
  const queryClient = useQueryClient();
  const rolesQuery = useRoles();
  const currentRoleName = binding?.roleRef?.name ?? "";
  const roleOptions = useMemo(
    () => userGroupRoleOptions(rolesQuery.data ?? [], currentRoleName ? [currentRoleName] : []),
    [currentRoleName, rolesQuery.data],
  );
  const availableOptions = useMemo(
    () =>
      roleOptions.filter(
        (option) => option.name === currentRoleName || !boundRoleNames.includes(option.name),
      ),
    [boundRoleNames, currentRoleName, roleOptions],
  );
  const form = useForm<RoleBindingValues>({
    resolver: zodResolver(roleBindingSchema) as Resolver<RoleBindingValues>,
    defaultValues: {
      roleName: currentRoleName,
    },
  });
  const roleName = useWatch({ control: form.control, name: "roleName" }) ?? "";
  const bindingName =
    binding?.metadata.name ?? (roleName ? userGroupRoleBindingName(groupName, roleName) : "");

  const mutation = useMutation({
    mutationFn: async (values: RoleBindingValues) => {
      if (binding) {
        await updateGroupRoleBinding(binding, values.roleName);
      } else {
        await createGroupRoleBinding(
          userGroupRoleBindingName(groupName, values.roleName),
          groupName,
          values.roleName,
        );
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-group"] }),
        queryClient.invalidateQueries({ queryKey: USER_GROUPS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ["user-permissions"] }),
      ]);
      toast.success(binding ? "角色绑定已更新。" : "角色绑定已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveUserGroupBindingApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Link2 />}
      title={binding ? "编辑角色绑定" : "添加角色绑定"}
      description={`为「${groupDisplayName}」绑定一个角色；成员用户会继承该角色。`}
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.4}
      submitLabel={binding ? "保存更改" : "创建绑定"}
      resetLabel="清空"
    >
      <FormField
        label="角色"
        htmlFor="user-group-role-binding-role"
        error={form.formState.errors.roleName?.message}
        hint={rolesQuery.isError ? "无法加载角色列表，请稍后重试。" : undefined}
      >
        <Controller
          control={form.control}
          name="roleName"
          render={({ field }) => (
            <Select
              value={field.value}
              onValueChange={field.onChange}
              disabled={rolesQuery.isPending || availableOptions.length === 0}
            >
              <SelectTrigger id="user-group-role-binding-role" className="w-full">
                <SelectValue placeholder="选择角色" />
              </SelectTrigger>
              <SelectContent>
                {availableOptions.map((option) => (
                  <SelectItem key={option.name} value={option.name}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </FormField>
      {!rolesQuery.isPending && availableOptions.length === 0 ? (
        <p className="text-xs text-muted-foreground">
          没有可绑定的角色：自定义用户组只能绑定尚未授予的自定义角色。
        </p>
      ) : null}

      <FormField
        label="绑定名称"
        htmlFor="user-group-role-binding-name"
        hint={binding ? "绑定名称创建后不可修改。" : "按用户组和角色自动生成。"}
      >
        <Input
          id="user-group-role-binding-name"
          value={bindingName}
          placeholder="选择角色后自动生成"
          className="font-mono text-xs"
          readOnly
        />
      </FormField>
    </FormDialog>
  );
}
