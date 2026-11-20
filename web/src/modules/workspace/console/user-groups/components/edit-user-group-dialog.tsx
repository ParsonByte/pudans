import { useMemo } from "react";
import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { type UserGroup, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Pencil } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { isBuiltinRole } from "@/modules/workspace/console/roles/lib/role-model";
import { useRoles } from "@/modules/workspace/console/roles/lib/use-roles";
import { USER_GROUPS_QUERY_KEY } from "@/modules/workspace/lib/user-groups";

import { resolveUserGroupApiError } from "../lib/api";
import {
  isBuiltinUserGroup,
  userGroupRoleNames,
  userGroupRoleOptions,
  userGroupRoleRefs,
} from "../lib/user-group-model";
import { RoleRefSelector } from "./role-ref-selector";

type EditUserGroupDialogProps = {
  group: UserGroup;
  onClose: () => void;
};

const editUserGroupSchema = z.object({
  displayName: z.string().trim().min(1, "请输入显示名称。").max(80, "显示名称不能超过 80 个字符。"),
  description: z.string().trim().max(200, "描述不能超过 200 个字符。"),
  roles: z.array(z.string()),
});

type EditUserGroupValues = z.infer<typeof editUserGroupSchema>;

export function EditUserGroupDialog({ group, onClose }: EditUserGroupDialogProps) {
  const queryClient = useQueryClient();
  const rolesQuery = useRoles();
  const builtin = isBuiltinUserGroup(group);
  const roleNames = useMemo(() => userGroupRoleNames(group), [group]);
  const builtinRoleNames = useMemo(() => {
    const names = new Set<string>();
    for (const role of rolesQuery.data ?? []) {
      if (isBuiltinRole(role) && roleNames.includes(role.metadata.name)) {
        names.add(role.metadata.name);
      }
    }
    return names;
  }, [roleNames, rolesQuery.data]);
  const lockedRoleNames = useMemo(
    () => (builtin ? [...builtinRoleNames] : []),
    [builtin, builtinRoleNames],
  );
  const roleOptions = useMemo(
    () => userGroupRoleOptions(rolesQuery.data ?? [], lockedRoleNames),
    [lockedRoleNames, rolesQuery.data],
  );
  const form = useForm<EditUserGroupValues>({
    resolver: zodResolver(editUserGroupSchema) as Resolver<EditUserGroupValues>,
    defaultValues: {
      displayName: group.displayName || group.metadata.name,
      description: group.description ?? "",
      roles: roleNames,
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: EditUserGroupValues) => {
      const submittedRoles = builtin
        ? values.roles
        : values.roles.filter((roleName) => !builtinRoleNames.has(roleName));
      await apiClient.userGroup.updateUserGroup({
        name: group.metadata.name,
        userGroup: {
          ...group,
          displayName: builtin ? group.displayName || group.metadata.name : values.displayName,
          description: builtin ? group.description : values.description || undefined,
          roleRefs: userGroupRoleRefs(submittedRoles),
        },
      });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: USER_GROUPS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ["user-group"] }),
        queryClient.invalidateQueries({ queryKey: ["users"] }),
      ]);
      toast.success(builtin ? "用户组角色已更新。" : "用户组已更新。");
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
      icon={<Pencil />}
      title={builtin ? "调整用户组角色" : "编辑用户组"}
      description={
        builtin
          ? `调整「${group.displayName || group.metadata.name}」绑定的角色；内置角色不可移除，显示名称和描述由系统维护。`
          : `调整「${group.displayName || group.metadata.name}」的名称、描述和绑定角色；用户组标识不可修改。`
      }
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={builtin ? 0.4 : 0.55}
      submitLabel={builtin ? "保存角色" : "保存更改"}
    >
      {builtin ? (
        <p className="border bg-muted/40 p-3 text-xs leading-5 text-muted-foreground">
          内置用户组不能移除内置角色，但可以添加自定义角色。
        </p>
      ) : (
        <>
          <FormField
            label="显示名称"
            htmlFor="edit-user-group-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="edit-user-group-display-name"
              autoComplete="off"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="描述"
            htmlFor="edit-user-group-description"
            error={form.formState.errors.description?.message}
          >
            <Textarea
              id="edit-user-group-description"
              rows={3}
              placeholder="说明该用户组的用途和适用范围。"
              {...form.register("description")}
            />
          </FormField>
        </>
      )}
      <div className="space-y-2">
        <p className="text-sm font-medium">绑定角色</p>
        <p className="text-xs text-muted-foreground">
          {builtin
            ? "内置角色已锁定，不能移除；可添加自定义角色。"
            : "自定义用户组只能绑定自定义角色，成员用户会继承这些角色。"}
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
