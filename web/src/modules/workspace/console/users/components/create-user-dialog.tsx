import { Controller, type Resolver, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { UserPlus } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { PasswordInput } from "@/components/password-input";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useMenuPath } from "@/modules/workspace/lib/permissions";
import { useUserGroups } from "@/modules/workspace/lib/user-groups";

import { createLocalUser, resolveUserApiError } from "../lib/api";
import { passwordField, usernameField } from "../lib/validation";

type CreateUserDialogProps = {
  onClose: () => void;
};

const createUserSchema = z
  .object({
    username: usernameField,
    password: passwordField,
    confirmPassword: z.string(),
    userGroup: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "两次输入的密码不一致。",
  });

type CreateUserValues = z.infer<typeof createUserSchema>;

export function CreateUserDialog({ onClose }: CreateUserDialogProps) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const usersPath = useMenuPath("users");
  const form = useForm<CreateUserValues>({
    resolver: zodResolver(createUserSchema) as Resolver<CreateUserValues>,
    defaultValues: {
      username: "",
      password: "",
      confirmPassword: "",
      userGroup: "",
    },
  });

  const userGroupsQuery = useUserGroups();

  const mutation = useMutation({
    mutationFn: (values: CreateUserValues) =>
      createLocalUser({
        username: values.username,
        password: values.password,
        userGroup: values.userGroup || undefined,
      }),
    onSuccess: async (createdName) => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success("用户已创建。");
      onClose();
      if (createdName && usersPath) {
        void navigate({ to: `${usersPath}/${createdName}` });
      }
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  const onSubmit = form.handleSubmit(
    (values) => mutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<UserPlus />}
      title="创建用户"
      description="创建本地账号并设置初始密码；姓名、邮箱、手机号和头像会在绑定飞书后同步。"
      form={form}
      onSubmit={onSubmit}
      isPending={mutation.isPending}
      contentClassName="sm:max-w-lg"
      maxHeightRatio={0.55}
      submitLabel="创建用户"
      resetLabel="清空"
    >
      <FormField
        label="用户名"
        htmlFor="create-username"
        error={form.formState.errors.username?.message}
        hint="小写字母、数字、- 和 ."
      >
        <Input
          id="create-username"
          autoComplete="off"
          spellCheck={false}
          {...form.register("username")}
        />
      </FormField>

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="初始密码"
          htmlFor="create-password"
          error={form.formState.errors.password?.message}
          hint="8-257 位，仅限字母、数字和 !@#$%^&*.?"
        >
          <PasswordInput
            id="create-password"
            autoComplete="new-password"
            {...form.register("password")}
          />
        </FormField>
        <FormField
          label="确认密码"
          htmlFor="create-confirm-password"
          error={form.formState.errors.confirmPassword?.message}
        >
          <PasswordInput
            id="create-confirm-password"
            autoComplete="new-password"
            {...form.register("confirmPassword")}
          />
        </FormField>
      </div>

      <div className="space-y-4">
        <FormField
          label="用户组"
          htmlFor="create-user-group"
          hint="头像、手机号等信息在绑定飞书后由同步任务自动获取。"
        >
          <Controller
            control={form.control}
            name="userGroup"
            render={({ field }) => (
              <Select
                value={field.value || "default"}
                onValueChange={(value) => field.onChange(value === "default" ? "" : value)}
                disabled={userGroupsQuery.isPending}
              >
                <SelectTrigger id="create-user-group" className="w-full">
                  <SelectValue placeholder="选择用户组" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="default">默认用户组</SelectItem>
                  {(userGroupsQuery.data ?? []).map((group) => (
                    <SelectItem key={group.metadata.name} value={group.metadata.name}>
                      {group.displayName || group.metadata.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
      </div>
    </FormDialog>
  );
}
