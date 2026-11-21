import { useEffect, useMemo, useRef } from "react";
import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import type { RoleBinding } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
import { fetchAllUsers } from "@/modules/workspace/console/users/lib/api";
import { useUserGroups } from "@/modules/workspace/lib/user-groups";

import { createRoleBinding, resolveRoleBindingApiError, updateRoleBinding } from "../lib/api";
import {
  type BindingSubjectOption,
  bindableRoleOptions,
  parseSubjectOptionKey,
  roleBindingRoleName,
  roleBindingSubjectKeys,
  subjectOptionKey,
} from "../lib/binding-model";
import { roleDisplayName } from "../lib/role-model";
import { useRoles } from "../lib/use-roles";
import { SubjectSelector } from "./subject-selector";

type RoleBindingDialogProps = {
  binding?: RoleBinding;
  onClose: () => void;
};

const roleBindingSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "请输入绑定名称。")
    .max(63, "绑定名称不能超过 63 个字符。")
    .regex(/^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/, "绑定名称只能包含小写字母、数字和 -。"),
  roleName: z.string().min(1, "请选择角色。"),
  subjects: z.array(z.string()).min(1, "请至少选择一个绑定主体。"),
});

type RoleBindingValues = z.infer<typeof roleBindingSchema>;

export function RoleBindingDialog({ binding, onClose }: RoleBindingDialogProps) {
  const queryClient = useQueryClient();
  const rolesQuery = useRoles();
  const groupsQuery = useUserGroups();
  const usersQuery = useQuery({
    queryKey: ["users", "binding-options"],
    queryFn: () => fetchAllUsers(),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
  const roleOptions = useMemo(() => bindableRoleOptions(rolesQuery.data ?? []), [rolesQuery.data]);
  const subjectOptions = useMemo<BindingSubjectOption[]>(() => {
    const groups = (groupsQuery.data ?? [])
      .map((group) => ({
        key: subjectOptionKey("UserGroup", group.metadata.name),
        kind: "UserGroup" as const,
        name: group.metadata.name,
        label: group.displayName || group.metadata.name,
        hint: `@${group.metadata.name}`,
      }))
      .sort((left, right) => left.label.localeCompare(right.label, "zh-CN"));
    const users = (usersQuery.data ?? [])
      .filter((user): user is typeof user & { name: string } => Boolean(user.name))
      .map((user) => ({
        key: subjectOptionKey("User", user.name),
        kind: "User" as const,
        name: user.name,
        label: user.displayName || user.name,
        hint: `@${user.name}`,
      }))
      .sort((left, right) => left.label.localeCompare(right.label, "zh-CN"));
    const options: BindingSubjectOption[] = [...groups, ...users];
    if (binding) {
      const knownKeys = new Set(options.map((option) => option.key));
      for (const key of roleBindingSubjectKeys(binding)) {
        const subject = parseSubjectOptionKey(key);
        if (subject && !knownKeys.has(key)) {
          options.push({
            key,
            kind: subject.kind,
            name: subject.name,
            label: subject.name,
            hint: `@${subject.name}`,
          });
        }
      }
    }
    return options;
  }, [binding, groupsQuery.data, usersQuery.data]);

  const form = useForm<RoleBindingValues>({
    resolver: zodResolver(roleBindingSchema) as Resolver<RoleBindingValues>,
    defaultValues: {
      name: binding?.metadata.name ?? "",
      roleName: binding ? roleBindingRoleName(binding) : "",
      subjects: binding ? roleBindingSubjectKeys(binding) : [],
    },
  });

  const roleNames = rolesQuery.data?.map((role) => role.metadata.name) ?? [];
  const roleName = useWatch({ control: form.control, name: "roleName" }) ?? "";
  const roleExists = Boolean(roleName) && roleNames.includes(roleName);
  const roleSelectable = roleOptions.some((role) => role.metadata.name === roleName);
  const suggestedName = roleName ? `${roleName}-binding` : "";
  const previousSuggestion = useRef("");

  useEffect(() => {
    if (binding) {
      return;
    }
    const currentName = form.getValues("name");
    if (suggestedName && (!currentName || currentName === previousSuggestion.current)) {
      form.setValue("name", suggestedName, { shouldDirty: true });
    }
    previousSuggestion.current = suggestedName;
  }, [binding, form, suggestedName]);

  const mutation = useMutation({
    mutationFn: async (values: RoleBindingValues) => {
      const subjects = values.subjects
        .map(parseSubjectOptionKey)
        .filter(
          (subject): subject is { kind: "User" | "UserGroup"; name: string } => subject !== null,
        );
      if (binding) {
        await updateRoleBinding(binding, values.roleName, subjects);
      } else {
        await createRoleBinding(values.name, values.roleName, subjects);
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["role-bindings"] }),
        queryClient.invalidateQueries({ queryKey: ["user-permissions"] }),
      ]);
      toast.success(binding ? "角色绑定已更新。" : "角色绑定已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveRoleBindingApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const subjectsError = form.formState.errors.subjects?.message;

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<Link2 />}
      title={binding ? "编辑角色绑定" : "添加角色绑定"}
      description="将角色直接授予用户或用户组；直接绑定与用户组继承的角色同时生效。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-xl"
      maxHeightRatio={0.65}
      submitLabel={binding ? "保存更改" : "创建绑定"}
      resetLabel="清空"
    >
      <FormField
        label="绑定名称"
        htmlFor="role-binding-name"
        error={form.formState.errors.name?.message}
        hint={
          binding
            ? "绑定名称创建后不可修改。"
            : "只能包含小写字母、数字和 -；留空时按角色名称自动生成。"
        }
      >
        <Input
          id="role-binding-name"
          autoComplete="off"
          spellCheck={false}
          placeholder={suggestedName || "例如 super-admins-binding"}
          readOnly={Boolean(binding)}
          disabled={Boolean(binding)}
          {...form.register("name")}
        />
      </FormField>

      <FormField
        label="角色"
        htmlFor="role-binding-role"
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
              disabled={rolesQuery.isPending || roleOptions.length === 0}
            >
              <SelectTrigger id="role-binding-role" className="w-full">
                <SelectValue placeholder="选择角色" />
              </SelectTrigger>
              <SelectContent>
                {roleOptions.map((role) => (
                  <SelectItem key={role.metadata.name} value={role.metadata.name}>
                    {roleDisplayName(role)}
                  </SelectItem>
                ))}
                {roleName && roleExists && !roleSelectable ? (
                  <SelectItem value={roleName}>{roleName}</SelectItem>
                ) : null}
              </SelectContent>
            </Select>
          )}
        />
      </FormField>
      {roleName && !roleExists && roleNames.length > 0 ? (
        <p className="text-xs text-muted-foreground">该角色已不存在，请重新选择。</p>
      ) : null}
      {roleName && roleExists && !roleSelectable ? (
        <p className="text-xs text-muted-foreground">该角色为隐藏角色，仅保留当前绑定。</p>
      ) : null}

      <div className="space-y-2">
        <p className="text-sm font-medium">绑定主体</p>
        <Controller
          control={form.control}
          name="subjects"
          render={({ field }) => (
            <SubjectSelector
              options={subjectOptions}
              selected={field.value}
              isLoading={groupsQuery.isPending || usersQuery.isPending}
              isError={groupsQuery.isError && usersQuery.isError}
              onChange={field.onChange}
            />
          )}
        />
        {groupsQuery.isError !== usersQuery.isError ? (
          <p className="text-xs text-muted-foreground">
            部分用户或用户组无法加载，仅能选择已加载的主体。
          </p>
        ) : null}
        {subjectsError ? <p className="text-xs text-destructive">{subjectsError}</p> : null}
      </div>
    </FormDialog>
  );
}
